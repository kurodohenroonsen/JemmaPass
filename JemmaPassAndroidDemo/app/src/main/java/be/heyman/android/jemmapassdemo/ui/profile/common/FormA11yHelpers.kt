/*
 * FormA11yHelpers.kt — JEMMA Pass · Plan B · v2.6.0 · L_BLINDAGE
 *
 * Utilitaires d'accessibilité TalkBack pour les 4 forms manuels JEMMA.
 *
 * Pourquoi un helper et pas juste set `android:contentDescription` en XML ?
 *   - Les forms ont des champs dynamiques (substance pickée, code SNOMED
 *     rendu en label séparé du tappable card) → contentDescription doit
 *     refléter l'état runtime
 *   - Les tappable cards (relation picker, status picker, blood type)
 *     doivent annoncer "button" + l'action ("ouvre le sélecteur de …")
 *     plutôt que juste le label visible
 *   - Les toggles severity (H/L/U) ont besoin de "selected" announce
 *
 * Fonctionnalités :
 *
 *   1. announceForA11y(view, text) — utilise TYPE_ANNOUNCEMENT pour
 *      forcer TalkBack à lire un message hors focus (ex: après save)
 *
 *   2. markAsPicker(view, hintRes, currentValue) — transforme une `View`
 *      en tappable picker accessible : role=button + hint contextuel
 *
 *   3. markAsToggle(view, label, isSelected) — set role + state announce
 *      pour MaterialButtonToggleGroup buttons
 *
 *   4. markAsHeading(view) — marque un TextView comme heading (TalkBack
 *      le saute en mode "navigate by heading")
 *
 *   5. setLiveRegion(view, polite=true) — wrap ViewCompat pour annonce
 *      automatique sur changement (ex: pre-fill chargé async)
 *
 * Logging : tag JEMMA-A11Y
 */
package be.heyman.android.jemmapassdemo.ui.profile.common

import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

object FormA11yHelpers {
    private const val TAG = "JEMMA-A11Y"

    /**
     * Force TalkBack à annoncer un message MAINTENANT, indépendamment du
     * focus. Utile après une validation, un save, un xchk warning auto.
     */
    fun announce(view: View, message: CharSequence) {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📣 announce: '${message.take(80)}'")
        view.announceForAccessibility(message)
    }

    /**
     * Marque une `View` (typiquement un MaterialCardView ou LinearLayout)
     * comme un picker tappable. TalkBack annoncera :
     *   "[currentLabel], [pickerHint], double-tap to open"
     *
     * @param view la View tappable
     * @param pickerHint phrase courte décrivant l'action ("Ouvre le sélecteur de relation")
     * @param currentValue valeur actuelle pickée (peut être null si vide)
     * @param emptyHint texte annoncé quand currentValue est null
     */
    fun markAsPicker(
        view: View,
        pickerHint: String,
        currentValue: String?,
        emptyHint: String = "non renseigné",
    ) {
        val label = currentValue?.takeIf { it.isNotBlank() } ?: emptyHint
        view.contentDescription = "$label. $pickerHint"
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
                info.isClickable = true
                // Custom click action label (TalkBack reads "Activer pour Ouvrir le sélecteur de relation")
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        pickerHint,
                    )
                )
            }
        })
    }

    /**
     * Marque un TextView comme heading — TalkBack le saute en mode
     * "Navigate by heading" (raccourci d-pad commun).
     */
    fun markAsHeading(view: View) {
        ViewCompat.setAccessibilityHeading(view, true)
    }

    /**
     * Marque un toggle button (MaterialButton dans un Group) avec son
     * état selected. À appeler à chaque changement d'état.
     */
    fun markToggleState(
        view: View,
        label: String,
        isSelected: Boolean,
    ) {
        val stateWord = if (isSelected) "sélectionné" else "non sélectionné"
        view.contentDescription = "$label, $stateWord"
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.isCheckable = true
                info.isChecked = isSelected
            }
        })
    }

    /**
     * Wrap autour de ViewCompat.setAccessibilityLiveRegion qui simplifie
     * pour les cas "polite" (TalkBack lit dès que l'app est silencieuse)
     * ou "assertive" (TalkBack interrompt ce qu'il dit pour annoncer).
     */
    fun setLiveRegion(view: View, polite: Boolean = true) {
        ViewCompat.setAccessibilityLiveRegion(
            view,
            if (polite) ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE
            else ViewCompat.ACCESSIBILITY_LIVE_REGION_ASSERTIVE,
        )
    }

    /**
     * Marque un champ comme erreur — TalkBack lit "erreur, [message]" sur
     * focus. Combine bien avec TextInputLayout.error qui fait déjà le
     * niveau visuel.
     */
    fun setErrorAnnounce(view: View, errorMsg: String?) {
        if (errorMsg.isNullOrBlank()) {
            view.contentDescription = null
            return
        }
        view.contentDescription = "Erreur. $errorMsg"
        view.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
    }

    /**
     * Pour les sections de form complexes (Allergy, Medication) on a
     * souvent un sous-titre comme "Informations IPS-FULL avancées" suivi
     * de plusieurs champs. Marque le sous-titre comme heading + ajoute
     * un hint groupant.
     */
    fun setSectionHeader(view: View, headerText: String, fieldsCount: Int) {
        markAsHeading(view)
        view.contentDescription = "$headerText, section avec $fieldsCount champs"
    }
}
