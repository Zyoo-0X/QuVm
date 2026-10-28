package app.aemu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aemu.R
import app.aemu.core.CpuInfo
import app.aemu.core.GuestImage
import app.aemu.core.VmSettings

private data class Res(val label: String, val w: Int, val h: Int, val dpi: Int)

private val RESOLUTIONS = listOf(
    Res("480×800", 480, 800, 240),
    Res("540×960", 540, 960, 240),
    Res("720×1280", 720, 1280, 320),
)

private val RAM_STEPS = listOf(0, 256, 512, 768, 1024, 1536, 2048, 3072, 4096)

/** Per-machine settings: a pinned header and save bar, groups of rows in between. */
@Composable
fun SettingsSheet(img: GuestImage, onDismiss: () -> Unit, onSave: (VmSettings) -> Unit) {
    var s by remember { mutableStateOf(img.settings) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding()) {
            // header
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                VersionBadge(img, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
                    Text(img.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Group(stringResource(R.string.vs_screen)) {
                    if (s.legacyEngine && img.api < 14) {
                        Text(stringResource(R.string.vs_fixed_2x), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            RESOLUTIONS.forEachIndexed { i, r ->
                                ToggleButton(
                                    checked = s.width == r.w && s.height == r.h,
                                    onCheckedChange = { s = s.copy(width = r.w, height = r.h, density = r.dpi) },
                                    shapes = when (i) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        RESOLUTIONS.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                                ) { Text(r.label, maxLines = 1, softWrap = false) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            NumField(stringResource(R.string.vs_width), s.width, Modifier.weight(1f)) { s = s.copy(width = it.coerceIn(240, 2160) and 0x7ffffffe) }
                            NumField(stringResource(R.string.vs_height), s.height, Modifier.weight(1f)) { s = s.copy(height = it.coerceIn(320, 3840) and 0x7ffffffe) }
                            NumField(stringResource(R.string.vs_dpi), s.density, Modifier.weight(1f)) { s = s.copy(density = it.coerceIn(96, 640)) }
                        }
                        Text(stringResource(R.string.vs_screen_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                }

                Group(stringResource(R.string.vs_perf)) {
                    Toggle(stringResource(R.string.vs_gpu), stringResource(R.string.vs_gpu_sub), s.gpu) { s = s.copy(gpu = it) }
                    if (img.api >= 14) Toggle(stringResource(R.string.vs_hwui), stringResource(R.string.vs_hwui_sub), s.hwui) { s = s.copy(hwui = it) }
                    Toggle(stringResource(R.string.vs_jit), stringResource(R.string.vs_jit_sub), s.jit) { s = s.copy(jit = it) }
                    if (img.api >= 21) Toggle(stringResource(R.string.vs_fulldex), stringResource(R.string.vs_fulldex_sub), s.fullDexopt) { s = s.copy(fullDexopt = it) }
                    if (img.api < 14) Toggle(stringResource(R.string.vs_legacy), stringResource(R.string.vs_legacy_sub), s.legacyEngine) { s = s.copy(legacyEngine = it) }
                    val has32 = CpuInfo.hasAArch32
                    Toggle(
                        stringResource(R.string.vs_native32),
                        if (has32) stringResource(R.string.vs_native32_sub) else stringResource(R.string.vs_native32_unsupported),
                        s.native32 && has32
                    ) { if (has32) s = s.copy(native32 = it) }
                    val ramIdx = RAM_STEPS.indexOf(s.ramMb).coerceAtLeast(0)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.vs_ram), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Tag(if (s.ramMb == 0) stringResource(R.string.vs_ram_auto) else "${s.ramMb} MB", tint = MaterialTheme.colorScheme.primary)
                        }
                        Slider(value = ramIdx.toFloat(), onValueChange = { s = s.copy(ramMb = RAM_STEPS[it.toInt()]) },
                            valueRange = 0f..RAM_STEPS.lastIndex.toFloat(), steps = RAM_STEPS.size - 2)
                    }
                    Toggle(stringResource(R.string.vs_lowram), stringResource(R.string.vs_lowram_sub), s.lowRam) { s = s.copy(lowRam = it) }
                    Toggle(stringResource(R.string.vs_ramfriendly), stringResource(R.string.vs_ramfriendly_sub), s.ramFriendly) { s = s.copy(ramFriendly = it) }
                    Toggle(stringResource(R.string.vs_proxy), stringResource(R.string.vs_proxy_sub), s.netProxy) { s = s.copy(netProxy = it) }
                }

                Group(stringResource(R.string.vs_radio_title)) {
                    Toggle(stringResource(R.string.vs_radio), stringResource(R.string.vs_radio_sub), s.radio) { s = s.copy(radio = it) }
                    if (s.radio) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        OutlinedTextField(value = s.imei, onValueChange = { v -> s = s.copy(imei = v.filter(Char::isDigit).take(15)) },
                            label = { Text("IMEI") }, placeholder = { Text(VmSettings.DEFAULT_IMEI) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        TextButton(onClick = { s = s.copy(imei = VmSettings.randomImei()) }) { Text(stringResource(R.string.vs_random)) }
                    }
                }

                Group(stringResource(R.string.vs_advanced)) {
                    OutlinedTextField(value = s.qemuArgs, onValueChange = { s = s.copy(qemuArgs = it) },
                        label = { Text(stringResource(R.string.vs_qemu_args)) }, supportingText = { Text(stringResource(R.string.vs_qemu_args_sub)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                }

                Group(stringResource(R.string.vs_recovery)) { RecoverySection(img) }

                Group(stringResource(R.string.vs_controls)) {
                    Toggle(stringResource(R.string.vs_nav), stringResource(R.string.vs_nav_sub), s.showNavBar) { s = s.copy(showNavBar = it) }
                    if (s.showNavBar) NavBarSetup(s) { s = it }
                    Toggle(stringResource(R.string.vs_awake), stringResource(R.string.vs_awake_sub), s.keepScreenOn) { s = s.copy(keepScreenOn = it) }
                    Toggle(stringResource(R.string.as_led), stringResource(R.string.as_led_sub), s.showLed) { s = s.copy(showLed = it) }
                    Toggle(stringResource(R.string.vs_single), stringResource(R.string.vs_single_sub), s.mtMode == 4) { s = s.copy(mtMode = if (it) 4 else 0) }
                }
            }
            // pinned save bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                GradientButton(stringResource(R.string.save), onClick = { onSave(s) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Which buttons the bar has (tap to add or remove; added ones go to the end) and how big it is. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun NavBarSetup(s: VmSettings, set: (VmSettings) -> Unit) {
    val keys = s.navKeys.split(',').filter { it in VmSettings.NAV_ALL }
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (k in keys + VmSettings.NAV_ALL.filter { it !in keys }) {
            val on = k in keys
            androidx.compose.material3.FilterChip(selected = on, label = { Text(navKeyLabel(k)) },
                onClick = { set(s.copy(navKeys = (if (on) keys - k else keys + k).joinToString(","))) })
        }
    }
    Toggle(stringResource(R.string.vs_nav_compact), stringResource(R.string.vs_nav_compact_sub), s.navSize == 0) { set(s.copy(navSize = if (it) 0 else 1)) }
}

@Composable
internal fun navKeyLabel(k: String) = stringResource(when (k) {
    "back" -> R.string.back; "home" -> R.string.home; "recents" -> R.string.recents; "menu" -> R.string.menu
    "search" -> R.string.nav_search; "volup" -> R.string.vol_up; "voldown" -> R.string.vol_down
    "power" -> R.string.power; else -> R.string.nav_dpad
})

@Composable
private fun NumField(label: String, value: Int, modifier: Modifier, set: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(value = text, onValueChange = { v -> text = v.filter(Char::isDigit).take(4); text.toIntOrNull()?.let(set) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

/** Recovery of this firmware: stock one from the import, or a custom recovery.img (TWRP, OrangeFox, CWM…). */
@Composable
private fun RecoverySection(img: GuestImage) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val paths = remember(img.id) { app.aemu.core.VmPaths(ctx, img.id) }
    var status by remember { mutableStateOf(recoveryStatus(paths)) }
    var busy by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.vs_recovery_failed)
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        Thread {
            val ok = runCatching {
                val data = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                app.aemu.core.RecoveryImage.install(paths, data) {}
            }.getOrDefault(false)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                busy = false
                status = recoveryStatus(paths)
                if (!ok) android.widget.Toast.makeText(ctx, failed, android.widget.Toast.LENGTH_LONG).show()
            }
        }.start()
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(status ?: stringResource(R.string.vs_recovery_none), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.vs_recovery_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(enabled = !busy, onClick = { pick.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.vs_recovery_install)) }
    }
}

private fun recoveryStatus(paths: app.aemu.core.VmPaths): String? {
    if (!app.aemu.core.RecoveryImage.installed(paths)) return null
    val dir = app.aemu.core.RecoveryImage.dir(paths)
    val prop = runCatching { java.io.File(dir, "default.prop").readText() }.getOrDefault("")
    val twrp = Regex("""ro\.twrp\.version=(\S+)""").find(prop)?.groupValues?.get(1)
    return when {
        twrp != null -> "TWRP $twrp"
        java.io.File(dir, "twres").isDirectory -> "TWRP"
        java.io.File(dir, "sbin/orangefox.sh").exists() || prop.contains("orangefox", true) -> "OrangeFox"
        prop.contains("cwm", true) || java.io.File(dir, "res/images/icon_clockwork.png").exists() -> "ClockworkMod"
        else -> "Stock recovery"
    }
}
