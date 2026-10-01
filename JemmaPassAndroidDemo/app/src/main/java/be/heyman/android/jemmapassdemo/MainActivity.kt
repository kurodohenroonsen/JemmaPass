/*
 * MainActivity.kt — single-Activity host of the nav_graph.xml.
 *
 * Sets up the bottom nav with the Navigation component and hides it on
 * destinations that should be full-bleed : permissions, splash (legacy),
 * radar (rescuer mode), sos broadcast, qr scanner, etc.
 *
 * Permissions screen is the start destination ; the user is auto-redirected
 * to dest_profiles by `PermissionsFragment.onResume()` if all 5 mandatory
 * permissions are already granted.
 *
 * 🆕 v2.6.2a.3 — starts the JemmaTaskShutdownService at boot so that the
 * Android framework wires our onTaskRemoved hook. Without this, swiping the
 * app from recents leaves the Nearby Connections broadcasts running and
 * other devices in the mesh continue to relay our stale chunks for minutes.
 */
package be.heyman.android.jemmapassdemo

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import be.heyman.android.jemmapassdemo.sos.JemmaTaskShutdownService
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "JEMMA-NAV"

        // Destinations that should hide the bottom nav (full-bleed screens).
        // R.id is resolved at runtime via resources to keep the list editable
        // without recompiling all fragments.
        private val FULL_BLEED_DESTINATION_NAMES = setOf(
            "dest_permissions",
            "dest_splash",
            "dest_radar",
            // 🆕 v2.5.11 — rescue-mode screens : the bottom nav bar is a
            // distraction when the user is actively scanning / triaging,
            // and it pushes the radar overlay up making rescuer pins clip.
            "dest_radar_rescuer",
            "dest_rescue_qr_scan",
            "dest_sos_broadcast",
            "dest_qr_viewer",
            "dest_med_scan",
            "dest_patient_detail",
            // 🆕 v2.6.0l — profile detail + 4 active edit pillars :
            // the bottom nav steals attention from the edit task and covers
            // the sticky save button. The user navigates back via toolbar.
            "dest_profile_detail",
            "dest_perso",
            "dest_contacts",
            "dest_allergies",
            "dest_medications",
            "dest_immunizations",
            "dest_procedures",
            "dest_devices",
            "dest_results",
            "dest_past_problems",
            "dest_problems",
            "dest_pregnancy",
            // 🆕 Lot 14.5c1 — écrans Assistant Jemma. Le pipeline screen
            // affiche une preview pleine largeur + 3 steps progressives ;
            // la bottom nav volerait l'attention et la place utile.
            "dest_assistant_pipeline",
            "dest_assistant_multi_preview",
            "dest_pillar_assistant",
            // 🆕 Lot 14.5c19 — Chat allergies en voix : la bottom nav
            // cachait le bouton stop-tts et le bas du chat. Full-bleed.
            "dest_allergies_chat",
            // 🆕 Lot 14.5c37 — Patient vision pipeline (idempotent : si le
            // c37 a déjà ajouté l'entrée plus haut dans le set, Kotlin n'en
            // ajoute pas de doublon car c'est un Set).
            "dest_patient_pipeline",
            // 🆕 Lot 14.5c40 — Patient voice chat (STT → Gemma → handoff).
            "dest_patient_chat",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreate")

        // 🆕 v2.6.2a.3 — wire the task-removal hook so that the SOS / radar
        // broadcasts stop cleanly when the user swipes the app from recents.
        // No-op past the first call ; safe to fire on every onCreate.
        JemmaTaskShutdownService.ensureStarted(this)

        setContentView(R.layout.activity_main)

        // Bind nav graph to bottom nav.
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)

        // Hide bottom nav on full-bleed screens.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val destName = try {
                resources.getResourceEntryName(destination.id)
            } catch (e: Exception) {
                ""
            }
            val hide = destName in FULL_BLEED_DESTINATION_NAMES
            bottomNav.visibility = if (hide) View.GONE else View.VISIBLE
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 🧭 dest=$destName · bottomNav=${if (hide) "GONE" else "VISIBLE"}"
            )
        }
    }
}
