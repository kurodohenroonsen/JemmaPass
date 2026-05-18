/*
 * PermissionCard.kt — model of the 5 permission cards displayed on the
 * onboarding screen. Each card groups 1 or more underlying Android runtime
 * permissions into a single user-facing UX unit.
 *
 *   Card               Underlying perms                            # prompts
 *   ─────────────────  ──────────────────────────────────────────  ─────────
 *   CAMERA             android.permission.CAMERA                   1
 *   MICROPHONE         android.permission.RECORD_AUDIO             1
 *   BLUETOOTH          BLUETOOTH_SCAN, _ADVERTISE, _CONNECT (12+)  3-4 grouped
 *                      + NEARBY_WIFI_DEVICES (Android 13+)             by the
 *                                                                      system
 *   LOCATION           ACCESS_FINE_LOCATION                        1
 *   NOTIFICATIONS      POST_NOTIFICATIONS (Android 13+ only ;      0 or 1
 *                      auto-GRANTED below 13)
 *
 * Mandatory : ALL 5 cards must be GRANTED before the user can leave the
 * permissions screen. The "Continuer" button stays disabled otherwise.
 *
 * v2.5.6.4 — Added NEARBY_WIFI_DEVICES to the BLUETOOTH card (Plan A HTML
 * already had this since L44.16.23). Without it, Nearby Connections
 * P2P_CLUSTER rejects every startDiscovery/startAdvertising call on
 * Android 13+ with ApiException 8029 (MISSING_PERMISSION_NEARBY_WIFI_DEVICES),
 * even when the manifest declares the permission. The "Nearby devices"
 * system group bundles all 4 perms into a single user dialog.
 */
package be.heyman.android.jemmapassdemo.ui.permissions

import android.Manifest
import android.os.Build
import androidx.annotation.StringRes
import be.heyman.android.jemmapassdemo.R

/**
 * Stable identifier for each card. Used as the key in the
 * `PermissionsViewModel` state map.
 */
enum class PermissionCardId {
    CAMERA,
    MICROPHONE,
    BLUETOOTH,
    LOCATION,
    NOTIFICATIONS,
}

/**
 * Lifecycle state of a single card. Drives the pill background + label.
 *
 *  PENDING            → user has not been prompted yet for this card
 *  GRANTED            → all underlying permissions granted ✓
 *  DENIED             → at least one denied, but the system still allows
 *                       us to re-prompt (rationale is shown next time)
 *  DENIED_PERMANENT   → user denied + checked "Don't ask again" (or denied
 *                       twice on Android 11+) ; we can no longer prompt,
 *                       must redirect to system Settings
 */
enum class PermissionCardStatus {
    PENDING,
    GRANTED,
    DENIED,
    DENIED_PERMANENT,
}

/**
 * Static description of a permission card. Built once per card at init.
 */
data class PermissionCard(
    val id: PermissionCardId,
    val emoji: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    /**
     * Underlying Android runtime permissions for this card. Empty list means
     * the card is auto-GRANTED on this OS version (e.g. NOTIFICATIONS on
     * Android < 13, ACCESS_FINE_LOCATION on devices without GPS hw, etc.).
     */
    val runtimePermissions: List<String>,
    /**
     * Human-readable display of the underlying permissions, shown small in
     * monospace under the description for educational transparency.
     */
    val underlyingDisplay: String,
) {
    companion object {

        /**
         * Build the canonical 5-card list for the current OS version.
         *
         * NOTIFICATIONS only requires a runtime permission on Android 13+
         * (Tiramisu, API 33). Below that, we report auto-GRANTED.
         */
        fun all(): List<PermissionCard> = listOf(
            PermissionCard(
                id = PermissionCardId.CAMERA,
                emoji = "📷",
                titleRes = R.string.permission_camera_title,
                descriptionRes = R.string.permission_camera_desc,
                runtimePermissions = listOf(Manifest.permission.CAMERA),
                underlyingDisplay = "android.permission.CAMERA",
            ),
            PermissionCard(
                id = PermissionCardId.MICROPHONE,
                emoji = "🎙️",
                titleRes = R.string.permission_microphone_title,
                descriptionRes = R.string.permission_microphone_desc,
                runtimePermissions = listOf(Manifest.permission.RECORD_AUDIO),
                underlyingDisplay = "android.permission.RECORD_AUDIO",
            ),
            PermissionCard(
                id = PermissionCardId.BLUETOOTH,
                emoji = "📡",
                titleRes = R.string.permission_bluetooth_title,
                descriptionRes = R.string.permission_bluetooth_desc,
                runtimePermissions = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // Android 12+ : BLE permissions split out of the
                        // legacy BLUETOOTH/BLUETOOTH_ADMIN install-time pair.
                        add(Manifest.permission.BLUETOOTH_SCAN)
                        add(Manifest.permission.BLUETOOTH_ADVERTISE)
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // 🆕 v2.5.6.4 (ported from Plan A HTML L44.16.23) —
                        // Nearby Connections P2P_CLUSTER requires
                        // NEARBY_WIFI_DEVICES on Android 13+ for the WiFi
                        // Direct / WiFi Aware transports. Without it, GMS
                        // rejects every startDiscovery / startAdvertising
                        // call with ApiException 8029
                        // (MISSING_PERMISSION_NEARBY_WIFI_DEVICES), even
                        // when the manifest declares it. The permission is
                        // grouped under the system's "Nearby devices" UI
                        // toggle together with the 3 BLUETOOTH_* perms, so
                        // requesting them together produces a single dialog.
                        add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    }
                },
                underlyingDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "BLUETOOTH_SCAN · BLUETOOTH_ADVERTISE · BLUETOOTH_CONNECT · NEARBY_WIFI_DEVICES"
                } else {
                    "BLUETOOTH_SCAN · BLUETOOTH_ADVERTISE · BLUETOOTH_CONNECT"
                },
            ),
            PermissionCard(
                id = PermissionCardId.LOCATION,
                emoji = "📍",
                titleRes = R.string.permission_location_title,
                descriptionRes = R.string.permission_location_desc,
                runtimePermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION),
                underlyingDisplay = "android.permission.ACCESS_FINE_LOCATION",
            ),
            PermissionCard(
                id = PermissionCardId.NOTIFICATIONS,
                emoji = "🔔",
                titleRes = R.string.permission_notifications_title,
                descriptionRes = R.string.permission_notifications_desc,
                runtimePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Pre-Android 13 : POST_NOTIFICATIONS does not exist as a
                    // runtime permission ; we'll report auto-GRANTED.
                    emptyList()
                },
                underlyingDisplay = "android.permission.POST_NOTIFICATIONS",
            ),
        )

        /**
         * Flatten all runtime permissions of all cards into a single array,
         * suitable for `requestMultiplePermissions()`. The Android system
         * automatically de-duplicates and groups related permissions in one
         * dialog (e.g. the 3 BLE permissions in a single prompt).
         */
        fun allRuntimePermissions(): Array<String> =
            all().flatMap { it.runtimePermissions }.toTypedArray()
    }
}
