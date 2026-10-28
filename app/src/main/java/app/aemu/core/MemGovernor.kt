package app.aemu.core

import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * RAM-friendly mode: when the phone's free memory falls (slowly, or suddenly while the guest boots) the guest is
 * slowed down instead of being left to run the phone out of memory, where Android would kill the whole app.
 *
 *  level 0  enough memory: nothing is touched
 *  level 1  low:      the next guest service waits until memory recovers; guest programs run at a lower CPU priority
 *  level 2  critical: on top of that every guest program except the binder daemons and servicemanager is paused
 *                     (SIGSTOP) for a part of each second, so it keeps running at about half speed, and one cached
 *                     background program of the guest is killed (like the kernel's low memory killer would)
 *
 * The guest is never paused for longer than a fraction of a second at a time (its own watchdogs wait for a minute),
 * and every program is resumed and its priority restored when memory recovers, the VM stops or the boot fails.
 */
class MemGovernor(
    private val root: File,
    private val imageId: String,
    private val log: (String) -> Unit,
) {
    @Volatile var level = 0
        private set
    @Volatile var availMb = Long.MAX_VALUE
        private set
    @Volatile private var running = false
    /** called (at most every 5 s) while memory stays critical: the guest's low memory killer frees a background program */
    @Volatile var onCritical: (() -> Unit)? = null

    private val totalMb: Long = readMeminfoMb("MemTotal:") ?: 0L
    /** below this much free memory (MB) the guest is slowed; below [critAt] it is paused in turns */
    val lowAt: Long = maxOf(1100L, totalMb * 14 / 100)
    val critAt: Long = maxOf(650L, totalMb * 7 / 100)

    private val niced: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    @Volatile private var targets: List<Int> = emptyList()
    @Volatile private var targetsAt = 0L
    @Volatile private var paused = false
    private val lock = Any()

    fun start() {
        if (running) return
        running = true
        log("memory: RAM-friendly mode on (phone RAM $totalMb MB; slows the guest below $lowAt MB free, pauses it in turns below $critAt MB)")
        Thread({ monitor() }, "mem-monitor").apply { isDaemon = true; start() }
        Thread({ throttle() }, "mem-throttle").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        release()
    }

    /** Resume everything that was paused and give the guest its CPU priority back. */
    fun release() {
        synchronized(lock) {
            if (paused) { signalTargets(OsConstants.SIGCONT); paused = false }
            restoreNice()
            level = 0
        }
    }

    /**
     * Called before the next guest service starts: while memory is short, wait (the guest then boots slower and
     * safely). Never longer than [maxMs], so a phone that stays short does not hang the boot for ever.
     */
    fun awaitRoom(what: String, maxMs: Long = 60_000) {
        if (!running || level == 0) return
        val t0 = SystemClock.uptimeMillis()
        log("memory: ${availMb} MB free, waiting before starting $what")
        while (running && level > 0 && SystemClock.uptimeMillis() - t0 < (if (level >= 2) maxMs else maxMs / 2)) {
            runCatching { Thread.sleep(400) }
        }
        val waited = (SystemClock.uptimeMillis() - t0) / 1000
        log(if (level == 0) "memory: recovered ($availMb MB free) after ${waited}s, going on with $what"
            else "memory: still short ($availMb MB free) after ${waited}s, going on with $what slowly")
    }

    // ---------------------------------------------------------------- monitor

    private fun monitor() {
        val history = ArrayDeque<Long>()
        var clearTicks = 0
        var holdUntil = 0L
        var lastCritical = 0L
        while (running) {
            val avail = readMeminfoMb("MemAvailable:") ?: Long.MAX_VALUE
            availMb = avail
            val psi = psiSome10()
            val now = SystemClock.uptimeMillis()
            history.addLast(avail); while (history.size > 6) history.removeFirst()
            // a sudden fall (the guest's start of a big service, dex2oat, the browser of the phone): slow down at once
            if (history.size >= 3 && history.first() != Long.MAX_VALUE && history.first() - avail >= 700) holdUntil = now + 10_000
            val want = when {
                avail < critAt || psi > 40.0 -> 2
                avail < lowAt || psi > 15.0 || now < holdUntil -> 1
                else -> 0
            }
            if (want >= level) { clearTicks = 0; if (want != level) { level = want; announce(avail, psi) } }
            else if (++clearTicks >= 4) {              // 2 s of calm before easing off: no flapping
                clearTicks = 0; level = want; announce(avail, psi)
            }
            if (level >= 2 && now - lastCritical > 5_000) { lastCritical = now; runCatching { onCritical?.invoke() } }
            runCatching { Thread.sleep(500) }
        }
    }

    private fun announce(avail: Long, psi: Double) {
        val p = if (psi > 0) ", pressure ${"%.0f".format(psi)}%" else ""
        when (level) {
            0 -> log("memory: back to normal ($avail MB free), the guest runs at full speed")
            1 -> log("memory: low ($avail MB free$p), slowing the guest down")
            else -> log("memory: critical ($avail MB free$p), the guest runs at half speed until it recovers")
        }
    }

    // ---------------------------------------------------------------- throttle

    private fun throttle() {
        try {
            while (running) {
                when (level) {
                    0 -> { if (paused || niced.isNotEmpty()) release(); runCatching { Thread.sleep(300) } }
                    1 -> {
                        if (paused) synchronized(lock) { signalTargets(OsConstants.SIGCONT); paused = false }
                        lowerPriority()
                        runCatching { Thread.sleep(500) }
                    }
                    else -> {
                        lowerPriority()
                        synchronized(lock) { if (level >= 2) { signalTargets(OsConstants.SIGSTOP); paused = true } }
                        runCatching { Thread.sleep(250) }
                        synchronized(lock) { signalTargets(OsConstants.SIGCONT); paused = false }
                        runCatching { Thread.sleep(350) }
                    }
                }
            }
        } finally {
            release()
        }
    }

    private fun signalTargets(sig: Int) {
        for (pid in guestPids()) runCatching { Os.kill(pid, sig) }
    }

    private fun lowerPriority() {
        for (pid in guestPids()) {
            if (!niced.add(pid)) continue
            runCatching { File("/proc/$pid/task").list()?.forEach { t -> t.toIntOrNull()?.let { runCatching { Process.setThreadPriority(it, 10) } } } }
        }
    }

    private fun restoreNice() {
        val list = niced.toList(); niced.clear()
        for (pid in list) runCatching { File("/proc/$pid/task").list()?.forEach { t -> t.toIntOrNull()?.let { runCatching { Process.setThreadPriority(it, 0) } } } }
    }

    /** Programs of this guest that may be paused: not the binder daemons, not servicemanager (everyone talks to them). */
    private fun guestPids(): List<Int> {
        val now = SystemClock.uptimeMillis()
        if (now - targetsAt < 1500) return targets
        val me = Process.myPid()
        val mark = "AEMU64_ROOT=${root.absolutePath}"
        val markCanon = "AEMU64_ROOT=${runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)}"
        val out = ArrayList<Int>()
        File("/proc").listFiles()?.forEach { d ->
            val pid = d.name.toIntOrNull() ?: return@forEach
            if (pid == me) return@forEach
            val cmd = runCatching { String(File(d, "cmdline").readBytes(), Charsets.ISO_8859_1).replace('\u0000', ' ') }.getOrNull() ?: return@forEach
            if (cmd.contains("libbinderd") || cmd.contains("servicemanager")) return@forEach
            val ours = cmd.contains("/images/$imageId/") || runCatching {
                File(d, "environ").inputStream().use { i -> val b = ByteArray(32 * 1024); val n = i.read(b); n > 0 && String(b, 0, n, Charsets.ISO_8859_1).split('\u0000').any { it == mark || it == markCanon } }
            }.getOrDefault(false)
            if (ours) out += pid
        }
        targets = out; targetsAt = now
        return out
    }

    // ---------------------------------------------------------------- /proc

    private fun readMeminfoMb(key: String): Long? = runCatching {
        File("/proc/meminfo").useLines { l -> l.first { it.startsWith(key) } }.split(Regex("\\s+"))[1].toLong() / 1024
    }.getOrNull()

    /** "some avg10" of /proc/pressure/memory (0 where an app may not read it) */
    private fun psiSome10(): Double = runCatching {
        File("/proc/pressure/memory").useLines { l -> l.first { it.startsWith("some") } }.substringAfter("avg10=").substringBefore(' ').toDouble()
    }.getOrDefault(0.0)
}
