package app.aemu.core

import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Process list of the guest for the task manager. Guest programs are ordinary processes of the phone, so the list
 * comes from /proc (works from the first second of the boot, no shell inside the guest needed): memory from statm,
 * CPU from the tick counters between two samples.
 */
class GuestProcs(private val root: File, private val imageId: String) {
    data class Proc(
        val pid: Int, val name: String, val rssMb: Long, val cpu: Float, val stopped: Boolean,
        /** killing it takes the system (or the binder) down with it */
        val essential: Boolean,
    )

    private val tick = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(100L).coerceAtLeast(1L)
    private val page = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrDefault(4096L).coerceAtLeast(1L)
    private var lastAt = 0L
    private var lastTicks = HashMap<Int, Long>()

    @Synchronized fun sample(): List<Proc> {
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastAt == 0L) 0L else now - lastAt
        val ticks = HashMap<Int, Long>()
        val out = ArrayList<Proc>()
        val me = Process.myPid()
        val mark = "AEMU64_ROOT=${root.absolutePath}"
        val markCanon = "AEMU64_ROOT=${runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)}"
        File("/proc").listFiles()?.forEach { d ->
            val pid = d.name.toIntOrNull() ?: return@forEach
            if (pid == me) return@forEach
            val raw = runCatching { String(File(d, "cmdline").readBytes(), Charsets.ISO_8859_1) }.getOrNull() ?: return@forEach
            val args = raw.split('\u0000').filter { it.isNotEmpty() }
            val ours = raw.contains("/images/$imageId/") || runCatching {
                File(d, "environ").inputStream().use { i -> val b = ByteArray(32 * 1024); val n = i.read(b); n > 0 && String(b, 0, n, Charsets.ISO_8859_1).split('\u0000').any { it == mark || it == markCanon } }
            }.getOrDefault(false)
            if (!ours) return@forEach
            val stat = runCatching { File(d, "stat").readText() }.getOrNull() ?: return@forEach
            val f = stat.substringAfterLast(')').trim().split(' ')      // f[0] = state, f[11] = utime, f[12] = stime
            val state = f.getOrNull(0)?.firstOrNull() ?: '?'
            if (state == 'Z' || state == 'X') return@forEach
            val t = (f.getOrNull(11)?.toLongOrNull() ?: 0L) + (f.getOrNull(12)?.toLongOrNull() ?: 0L)
            ticks[pid] = t
            val cpu = lastTicks[pid]?.takeIf { dt > 0 }?.let { ((t - it) * 1000f / tick) / dt * 100f } ?: 0f
            val rss = runCatching { File(d, "statm").readText().trim().split(' ')[1].toLong() * page / (1024 * 1024) }.getOrDefault(0L)
            val name = displayName(args, runCatching { File(d, "comm").readText().trim() }.getOrDefault(""))
            out += Proc(pid, name, rss, cpu.coerceAtLeast(0f), state == 'T', name in ESSENTIAL)
        }
        lastAt = now; lastTicks = ticks
        return out.sortedByDescending { it.rssMb }
    }

    fun kill(pid: Int) { runCatching { Os.kill(pid, OsConstants.SIGKILL) } }

    private fun displayName(args: List<String>, comm: String): String {
        // the engine's runner comes first on the command line of a program started through it
        val rest = args.dropWhile { it.contains("libaemu64") || it.contains("libqemu") }
        val pick = rest.firstOrNull { !it.startsWith("-") && !it.endsWith("/bin") && it.isNotBlank() }
            ?: args.firstOrNull { !it.startsWith("-") && it.isNotBlank() } ?: comm
        val n = pick.substringAfterLast('/').ifBlank { comm }
        return when {
            n.startsWith("libbinderd") -> "binderd"
            n.isBlank() -> "?"
            else -> n
        }
    }

    companion object {
        val ESSENTIAL = setOf("binderd", "hwbinderd", "vndbinderd", "servicemanager", "hwservicemanager", "vndservicemanager",
            "zygote", "zygote64", "zygote32", "system_server", "surfaceflinger", "init")
    }
}
