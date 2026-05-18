/*
 * KbConditionPicker.kt — JEMMA Pass · v2.6.0 · L_PHASE11
 *
 * 🆕 PHASE11 — Smart-suggestion par médicament :
 *   - Nouveau overload newInstance(title, lang, atcCode) qui active le mode
 *     smart-suggest.
 *   - Si atcCode connu, le picker affiche en haut une section
 *     "💊 SUGGÉRÉ pour [drug]" (≤12 indications) suivie d'un divider
 *     "Tous les diagnostics" puis la liste alphabétique classique.
 *   - Utilise kb.suggestProblemsForDrug(atcCode, lang) qui remonte l'arbre
 *     ATC via atc_hierarchy + extrait keywords thérapeutiques + match SNOMED.
 *
 * 🆕 PHASE10 — Refactor : la source de données passe de
 *   `terminology_codes WHERE category='Condition'` (UMLS soup 70K codes
 *   avec Zar/Koro/Amok/Yaws/Noma/Kuru/Siti/Gout) à
 *   `ips_valuesets_translations WHERE vs_id='problems-snomed-ct-ips-free-set'`
 *   (3596 codes FR cliniquement pertinents : Hypertension, Diabète,
 *   Asthme, Douleur, Anxiété, Infections, etc.)
 *
 *   Search natif SQLite (LIKE '%query%' COLLATE NOCASE) — pas besoin de
 *   FTS5 / strip-diacritics car le set est petit (3596 vs 70K). Latence
 *   typique : 5-15 ms par search vs 50-150 ms avant.
 *
 * Picker live-search KB DIAMOND v1.2 pour récupérer un code de Condition/Symptom
 * (SNOMED CT typiquement). Utilisé pour :
 *   - AllergyIntolerance.reaction.manifestation : MAINTENANT déplacé sur
 *     IpsCodePickerDialog avec liste curée de 29 réactions (phase 9).
 *   - MedicationStatement.reasonCode (indication médicale)  ← cas d'usage actuel
 *   - Condition.code (futur pilier diagnostics)
 *
 * Result : PickedDrug (réutilisé tel quel — c'est un simple {code, display, system}).
 *
 * Tag log : JEMMA-KB-CONDITION-PICKER · 🔎 search / ✅ pick / 💡 suggest
 */
package be.heyman.android.jemmapassdemo.ui.common

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseManager
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.kb.searchIpsProblems
import be.heyman.android.jemmapassdemo.kb.suggestProblemsForDrug
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class KbConditionPicker : DialogFragment() {

    companion object {
        private const val TAG = "JEMMA-KB-CONDITION-PICKER"
        private const val ARG_TITLE = "title"
        private const val ARG_LANG = "lang"
        private const val ARG_ATC_CODE = "atc_code"  // 🆕 PHASE11
        private const val DEBOUNCE_MS = 350L
        private const val MIN_CHARS = 2

        // Sentinel codes pour le header "SUGGÉRÉ" / "TOUS"
        private const val HEADER_SUGGESTED = "__HEADER_SUGGESTED__"
        private const val HEADER_ALL = "__HEADER_ALL__"

        fun newInstance(title: String, lang: String): KbConditionPicker {
            return newInstance(title, lang, atcCode = null)
        }

        /**
         * 🆕 PHASE11 — Overload avec atcCode pour activer le smart-suggest.
         * Si atcCode non-null, la liste affichera en haut une section
         * "💊 SUGGÉRÉ pour [drug]" avec les indications les plus probables
         * dérivées de l'arbre ATC.
         */
        fun newInstance(title: String, lang: String, atcCode: String?): KbConditionPicker {
            return KbConditionPicker().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_LANG, lang)
                    putString(ARG_ATC_CODE, atcCode)
                }
            }
        }
    }

    @Inject
    lateinit var kb: KnowledgeBaseService

    @Inject
    lateinit var kbManager: KnowledgeBaseManager

    private var onPicked: ((PickedDrug) -> Unit)? = null
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    private var currentSearchJob: Job? = null
    private val currentResults = mutableListOf<PickedDrug>()
    private lateinit var adapter: ArrayAdapter<String>

    // 🆕 PHASE11 — ATC code optionnel pour smart-suggest
    private var atcCode: String? = null

    fun setOnPicked(callback: (PickedDrug) -> Unit): KbConditionPicker {
        this.onPicked = callback
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val title = args.getString(ARG_TITLE) ?: ""
        val lang = args.getString(ARG_LANG) ?: "en"
        atcCode = args.getString(ARG_ATC_CODE)?.takeIf { it.isNotBlank() }

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 picker open · title='$title' · " +
            "lang=$lang · atc=$atcCode")

        val ctx = requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_kb_drug_picker, null, false)
        val searchEdit = view.findViewById<EditText>(R.id.drug_picker_search)
        val listView = view.findViewById<ListView>(R.id.drug_picker_list)
        val statusText = view.findViewById<TextView>(R.id.drug_picker_status)

        adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        statusText.setText(R.string.condition_picker_status_hint)
        searchEdit.setHint(R.string.condition_picker_search_hint)

        // 🆕 PHASE11 — Charge l'état initial : section SUGGÉRÉ (si atcCode) +
        // section TOUS (alphabetic).
        lifecycleScope.launch { loadInitialList(lang, statusText) }

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                pendingSearch?.let { debounceHandler.removeCallbacks(it) }
                if (q.length < MIN_CHARS) {
                    // Reload initial state (suggested + alphabetic)
                    currentSearchJob?.cancel()
                    lifecycleScope.launch { loadInitialList(lang, statusText) }
                    return
                }
                statusText.setText(R.string.drug_picker_status_searching)
                pendingSearch = Runnable { performSearch(q, lang, statusText) }
                debounceHandler.postDelayed(pendingSearch!!, DEBOUNCE_MS)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            if (position in currentResults.indices) {
                val picked = currentResults[position]
                // 🆕 PHASE11 — Ignore les rangées header (sentinels)
                if (picked.code == HEADER_SUGGESTED || picked.code == HEADER_ALL) {
                    Log.d(TAG, "[t=${System.currentTimeMillis()}] 🚫 ignored tap on header row")
                    return@setOnItemClickListener
                }
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ pick · code=${picked.code} · " +
                    "system=${picked.system} · display='${picked.display}'")
                onPicked?.invoke(picked) ?: Log.w(TAG,
                    "[t=${System.currentTimeMillis()}] ⚠ no callback set — pick is lost")
                dismiss()
            }
        }

        return MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(view)
            .setNegativeButton(R.string.drug_picker_cancel) { d, _ ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ↩ cancel")
                d.dismiss()
            }
            .create()
    }

    /** Returns true if the fragment view is still alive (avoid NPE on dismissed dialogs). */
    /**
     * 🆕 PHASE11 — Charge l'état initial du picker :
     *   - Si atcCode connu : section "💊 SUGGÉRÉ" (≤12 codes) + divider +
     *     section "TOUS" (alphabetic top 50)
     *   - Sinon : juste la liste alphabétique (compatible PHASE10)
     *
     * Les rangées headers utilisent des sentinels code (HEADER_SUGGESTED,
     * HEADER_ALL) pour pouvoir les ignorer dans le tap handler.
     */
    private suspend fun loadInitialList(lang: String, statusText: TextView) {
        // 1. Chargement alphabétique (background pool de fallback)
        val alphabetic = kb.searchIpsProblems(kbManager, lang, "", 50)

        // 2. Chargement smart-suggest si on a un atcCode
        val suggested = if (atcCode != null) {
            kb.suggestProblemsForDrug(kbManager, lang, atcCode, limit = 12)
        } else {
            emptyList()
        }

        if (!_isAlive()) return

        currentResults.clear()

        if (suggested.isNotEmpty()) {
            // Section SUGGÉRÉ avec header
            currentResults.add(
                PickedDrug(
                    code = HEADER_SUGGESTED,
                    display = getString(R.string.condition_picker_section_suggested),
                    system = "",
                ),
            )
            // Dédup : les SNOMED suggérés ne doivent pas réapparaître dans la section TOUS
            val suggestedCodes = suggested.map { it.code }.toSet()
            suggested.forEach { item ->
                currentResults.add(
                    PickedDrug(code = item.code, display = item.display, system = item.system),
                )
            }
            // Section TOUS avec header + items hors-suggested
            currentResults.add(
                PickedDrug(
                    code = HEADER_ALL,
                    display = getString(R.string.condition_picker_section_all),
                    system = "",
                ),
            )
            alphabetic.filter { it.code !in suggestedCodes }.forEach { item ->
                currentResults.add(
                    PickedDrug(code = item.code, display = item.display, system = item.system),
                )
            }
        } else {
            // Mode PHASE10 : juste alphabetic
            alphabetic.forEach { item ->
                currentResults.add(
                    PickedDrug(code = item.code, display = item.display, system = item.system),
                )
            }
        }

        val labels = currentResults.map { picked ->
            when (picked.code) {
                HEADER_SUGGESTED -> "▸ ${picked.display}"
                HEADER_ALL -> "▸ ${picked.display}"
                else -> "${picked.code.take(15)}  ·  ${picked.display}"
            }
        }
        adapter.clear()
        adapter.addAll(labels)
        adapter.notifyDataSetChanged()

        val nonHeaderCount = currentResults.count { it.code != HEADER_SUGGESTED && it.code != HEADER_ALL }
        statusText.text = getString(R.string.condition_picker_status_top, nonHeaderCount)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔝 initial loaded · " +
            "suggested=${suggested.size} · alphabetic=${alphabetic.size} · total=$nonHeaderCount")
    }

    private fun _isAlive(): Boolean = isAdded && view != null || dialog != null

    // 🆕 PHASE10 — pickDisplayClient helper removed: searchIpsProblems retourne
    // déjà des AllergyReactionItem(code, display, system) avec le display
    // pré-localisé via la query SQL ('lang = ?' filter).

    private fun performSearch(query: String, lang: String, statusText: TextView) {
        currentSearchJob?.cancel()
        currentSearchJob = lifecycleScope.launch {
            // 🆕 PHASE10 — Search dans le ValueSet IPS problems via le LIKE
            // SQL natif (pas besoin de FTS5 / strip-diacritics car SQLite
            // collation NOCASE et le set est petit : 3596 codes en FR).
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔎 IPS problems search · q='$query' · lang=$lang")
            val items = kb.searchIpsProblems(
                kbManager = kbManager,
                lang = lang,
                query = query,
                limit = 50,
            )
            if (_isAlive()) {
                currentResults.clear()
                items.forEach { item ->
                    currentResults.add(
                        PickedDrug(
                            code = item.code,
                            display = item.display,
                            system = item.system,
                        ),
                    )
                }
                val labels = currentResults.map { picked ->
                    "${picked.code.take(15)}  ·  ${picked.display}"
                }
                adapter.clear()
                adapter.addAll(labels)
                adapter.notifyDataSetChanged()
                val text = if (currentResults.isEmpty()) {
                    getString(R.string.drug_picker_status_empty)
                } else {
                    getString(R.string.drug_picker_status_count, currentResults.size)
                }
                statusText.text = text
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ IPS search done · n=${items.size}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pendingSearch?.let { debounceHandler.removeCallbacks(it) }
        currentSearchJob?.cancel()
    }
}
