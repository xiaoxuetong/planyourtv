package app.orchard.tv.settings

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Kids and Elders mode freezing the firmware's settings package: its gear key opens it over the
 * framework's GlobalKey path before Home Guard is bound, and on this firmware (MediaTek LED
 * Projector, Ntech/Wanbo) it shows as a floating panel the mode's foreground checks never see.
 * Freezing the whole package (DISABLED) makes it unresolvable to every caller from boot on —
 * the state lives in package-restrictions.xml, which PackageManager reads before any app runs.
 *
 * Needs CHANGE_COMPONENT_ENABLED_STATE, which a priv-app gets from a privapp-permissions
 * whitelist entry; on a normal install the call throws and the mode runs without the freeze.
 * [on] holds while the mode is enabled, through a parent's temporary [suspend]: the PIN that
 * suspends the mode does not hand back the settings the mode exists to keep away.
 */
object SettingsGuard {
    private const val TAG = "AerialGuard"

    /** This firmware's settings package: the gear key's target (Ntech/Wanbo, on /vendor). */
    private const val NTech_SETTINGS = "com.bkdisplay.settings"

    /** Whether the freeze is in place now, for the diagnostics. */
    @Volatile var frozen: Boolean = false
        private set

    /** Freezes [packages] while the mode is enabled; [context] is any app context. */
    fun freeze(context: Context, packages: Set<String> = setOf(NTech_SETTINGS)) {
        setState(context, packages, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    /** Undoes a freeze; the normal state when the mode is off. */
    fun unfreeze(context: Context, packages: Set<String> = setOf(NTech_SETTINGS)) {
        setState(context, packages, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
    }

    private fun setState(context: Context, packages: Set<String>, state: Int) {
        val pm = context.packageManager
        for (pkg in packages) {
            runCatching {
                val name = runCatching { pm.getApplicationInfo(pkg, 0).className }.getOrNull()
                pm.setApplicationEnabledSetting(pkg, state, 0)
                frozen = state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                Log.i(TAG, "settings guard: $pkg ${if (frozen) "frozen" else "unfrozen"}")
                name
            }.onFailure {
                Log.i(TAG, "settings guard: $pkg not frozen (${it.message})")
                frozen = false
            }
        }
    }
}
