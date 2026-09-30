/*
 * KbDrugPickerDialog.kt — JEMMA Pass · Plan B · v2.6.0 · L_PHASE12
 *
 * 🆕 PHASE12 — Enrichissement avec doses WHO ATC/DDD :
 *   - Chaque résultat de search est enrichi via batchGetDoseStandards()
 *     (JOIN sur table `dosages`, 2229 médicaments couverts)
 *   - Le code IPS est CACHÉ — gain de place pour l'info utile :
 *     ```
 *     Avant : "N02BE01  ·  paracétamol"
 *     Après : "paracétamol
 *              💊 3 g · oral · adulte"
 *     ```
 *   - PickedDrug enrichi avec atcCode + doseDdd + doseUnit + doseRoute + doseNote
 *     pour permettre l'auto-fill du formulaire dans MedicationFormBottomSheet
 *
 * Material AlertDialog spécialisé pour piquer un drug dans la KB DIAMOND v1.2
 * (~300K drugs avec codes ATC/RxNorm/SNOMED).
 *
 * Différence avec IpsCodePickerDialog :
 *   IpsCodePicker : catalogue **statique** in-memory (39 V3-RoleCode, 328 SNOMED)
 *                   chargé une fois, filtre client-side via search bar.
 *   KbDrugPicker  : catalogue **trop gros** pour le RAM → live-search FTS5
 *                   sur la KB SQLite avec debounce 350ms.
 *
 * Flow :
 *   1. User tape "warfa" → 350ms debounce → KnowledgeBaseService.searchCodes()
 *      avec categoryFilter="Medication"
 *   2. Filter by script + batch fetch doses
 *   3. Affiche top 20 résultats avec dose en sous-ligne grise
 *   4. Tap row → commit le code (ATC ou RxNorm) + transmet la dose
 *
 * Tag log : JEMMA-KB-DRUG-PICKER · 🔎 search / 💊 doses / ✅ pick
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
import be.heyman.android.jemmapassdemo.kb.DoseStandard
import be.heyman.android.jemmapassdemo.kb.KbSearchResult
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseManager
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.kb.batchGetDoseStandards
import be.heyman.android.jemmapassdemo.kb.filterByScriptForLang
import be.heyman.android.jemmapassdemo.kb.formatDoseForDisplay
import be.heyman.android.jemmapassdemo.kb.stripDiacritics
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Une drug pickée — surface minimale exposée au caller.
 *
 * @property code le code (ATC/RxNorm/SNOMED CUI selon ce que la KB indexe)
 * @property display nom localisé du médicament
 * @property system URI du code system (e.g. SNOMED, ATC, RxNorm)
 *
 * 🆕 PHASE12 — Champs optionnels pour pré-fill auto du formulaire :
 * @property atcCode l'ATC résolu (peut être null pour les codes non-ATC non-mappables)
 * @property doseDdd valeur DDD si dispo (WHO ATC/DDD reference)
 * @property doseUnit unité de la DDD (g/mg/u/mcg/...)
 * @property doseRoute route raw KB (oral/parenteral/inhal.aerosol/...)
 * @property doseNote contexte clinique (ex: "as sodium salt", "anti Xa")
 */
data class PickedDrug(
    val code: String,
    val display: String,
    val system: String,
    val atcCode: String? = null,
    val doseDdd: Double? = null,
    val doseUnit: String? = null,
    val doseRoute: String? = null,
    val doseNote: String? = null,
)

@AndroidEntryPoint
class KbDrugPickerDialog : DialogFragment() {

    companion object {
        private const val TAG = "JEMMA-KB-DRUG-PICKER"
        private const val ARG_TITLE = "title"
        private const val ARG_LANG = "lang"
        /** KB `terminology_codes.category` to search: "Medication" (default), "Procedure", "Device"… */
        private const val ARG_CATEGORY = "category"
        /** Optional curated suggestions shown while the query is empty (parallel arrays). */
        private const val ARG_SUGGEST_CODES = "suggest_codes"
        private const val ARG_SUGGEST_DISPLAYS = "suggest_displays"
        private const val ARG_SUGGEST_SYSTEM = "suggest_system"
        const val CATEGORY_MEDICATION = "Medication"
        const val CATEGORY_PROCEDURE = "Procedure"
        const val CATEGORY_DEVICE = "Device"
        private const val DEBOUNCE_MS = 350L
        private const val MIN_CHARS = 2

        fun newInstance(title: String, lang: String): KbDrugPickerDialog =
            newInstance(title, lang, CATEGORY_MEDICATION, emptyList(), "http://snomed.info/sct")

        /**
         * Generic KB concept picker (sprint 2): same live FTS5 search, restricted to
         * [category], with [suggestions] (code → display) listed before the user types.
         */
        fun newInstance(
            title: String,
            lang: String,
            category: String,
            suggestions: List<Pair<String, String>> = emptyList(),
            suggestionsSystem: String = "http://snomed.info/sct",
        ): KbDrugPickerDialog {
            return KbDrugPickerDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_LANG, lang)
                    putString(ARG_CATEGORY, category)
                    putStringArray(ARG_SUGGEST_CODES, suggestions.map { it.first }.toTypedArray())
                    putStringArray(ARG_SUGGEST_DISPLAYS, suggestions.map { it.second }.toTypedArray())
                    putString(ARG_SUGGEST_SYSTEM, suggestionsSystem)
                }
            }
        }
    }

    private var category: String = CATEGORY_MEDICATION
    private val suggestions = mutableListOf<PickedDrug>()

    @Inject
    lateinit var kb: KnowledgeBaseService

    // 🆕 PHASE12 — Needed for batchGetDoseStandards to look up DDDs
    @Inject
    lateinit var kbManager: KnowledgeBaseManager

    private var onPicked: ((PickedDrug) -> Unit)? = null
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    private var currentSearchJob: Job? = null
    private val currentResults = mutableListOf<PickedDrug>()
    private lateinit var adapter: ArrayAdapter<String>

    fun setOnPicked(callback: (PickedDrug) -> Unit): KbDrugPickerDialog {
        this.onPicked = callback
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val title = args.getString(ARG_TITLE) ?: ""
        val lang = args.getString(ARG_LANG) ?: "en"
        category = args.getString(ARG_CATEGORY) ?: CATEGORY_MEDICATION
        val suggestSystem = args.getString(ARG_SUGGEST_SYSTEM) ?: "http://snomed.info/sct"
        val suggestCodes = args.getStringArray(ARG_SUGGEST_CODES) ?: emptyArray()
        val suggestDisplays = args.getStringArray(ARG_SUGGEST_DISPLAYS) ?: emptyArray()
        suggestions.clear()
        suggestCodes.indices.forEach { i ->
            suggestions.add(PickedDrug(code = suggestCodes[i], display = suggestDisplays.getOrElse(i) { suggestCodes[i] }, system = suggestSystem))
        }

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 picker open · title='$title' · lang=$lang · category=$category · suggestions=${suggestions.size}")

        val ctx = requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_kb_drug_picker, null, false)
        val searchEdit = view.findViewById<EditText>(R.id.drug_picker_search)
        val listView = view.findViewById<ListView>(R.id.drug_picker_list)
        val statusText = view.findViewById<TextView>(R.id.drug_picker_status)

        adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        statusText.setText(R.string.drug_picker_status_hint)
        showSuggestions(statusText)

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                pendingSearch?.let { debounceHandler.removeCallbacks(it) }
                if (q.length < MIN_CHARS) {
                    currentSearchJob?.cancel()
                    adapter.clear()
                    adapter.notifyDataSetChanged()
                    currentResults.clear()
                    statusText.setText(R.string.drug_picker_status_hint)
                    showSuggestions(statusText)
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

    /** Curated common entries, listed while the query is empty (generic pickers only). */
    private fun showSuggestions(statusText: TextView) {
        if (suggestions.isEmpty()) return
        currentResults.clear()
        currentResults.addAll(suggestions)
        adapter.clear()
        adapter.addAll(suggestions.map { it.display })
        adapter.notifyDataSetChanged()
        statusText.text = getString(R.string.drug_picker_status_count, suggestions.size)
    }

    private fun performSearch(query: String, lang: String, statusText: TextView) {
        currentSearchJob?.cancel()
        currentSearchJob = lifecycleScope.launch {
            // 🆕 v2.6.0o — Strip diacritics + overfetch + script filter (cf. KbConditionPicker)
            val stripped = stripDiacritics(query)
            val effectiveQuery = if (stripped.isNotBlank() && stripped != query) stripped else query

            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔎 search · q='$query' · " +
                "stripped='$effectiveQuery' · lang=$lang")
            val result = kb.searchCodes(
                query = effectiveQuery,
                lang = lang,
                categoryFilter = category,
                maxResults = 40,  // overfetch then script-filter
            )
            when (result) {
                is KbSearchResult.Success -> {
                    // Filter by script compatible with user lang
                    val concepts = result.hits.map { it.concept }
                    val filtered = filterByScriptForLang(concepts, lang).take(20)
                    val droppedCount = result.hits.size - filtered.size

                    // 🆕 PHASE12 — Batch fetch DDD doses for all ATC codes returned (medications only)
                    val atcCodes = if (category == CATEGORY_MEDICATION) filtered.mapNotNull { it.atcCode?.takeIf { it.isNotBlank() } } else emptyList()
                    val doseMap = if (atcCodes.isNotEmpty()) {
                        kb.batchGetDoseStandards(kbManager, atcCodes)
                    } else {
                        emptyMap()
                    }
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 💊 dose enrichment · " +
                        "atcs=${atcCodes.size} · matched=${doseMap.size}")

                    currentResults.clear()
                    filtered.forEach { concept ->
                        // Re-find original hit to get the localized display
                        val origHit = result.hits.firstOrNull { it.concept.code == concept.code }
                        val dose = concept.atcCode?.let { doseMap[it] }
                        currentResults.add(
                            PickedDrug(
                                code = concept.code,
                                display = origHit?.display ?: concept.primaryDisplay,
                                system = concept.system,
                                atcCode = concept.atcCode,
                                doseDdd = dose?.doseDdd,
                                doseUnit = dose?.doseUnit,
                                doseRoute = dose?.route,
                                doseNote = dose?.note,
                            ),
                        )
                    }
                    val labels = currentResults.map { picked ->
                        // 🆕 PHASE12 — Cache le code IPS pour gagner de la place.
                        // Format display-first :
                        //   "Metformine"
                        //   "💊 2 g · oral · adulte"  (si dose dispo)
                        // ou simplement "Display name" si pas de dose.
                        if (picked.doseDdd != null && picked.doseUnit != null) {
                            val dose = DoseStandard(
                                atcCode = picked.atcCode ?: "",
                                doseDdd = picked.doseDdd,
                                doseUnit = picked.doseUnit,
                                route = picked.doseRoute ?: "oral",
                                population = "adult",
                                note = picked.doseNote,
                            )
                            val doseStr = formatDoseForDisplay(dose, lang)
                            val noteSuffix = picked.doseNote?.let { " · $it" } ?: ""
                            "${picked.display}\n💊 $doseStr · ${picked.doseRoute ?: "oral"}$noteSuffix"
                        } else {
                            picked.display
                        }
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
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ search done · " +
                        "raw=${result.hits.size} → script-kept=${filtered.size} (dropped=$droppedCount) · " +
                        "doses=${doseMap.size} · ${result.latencyMs}ms · fts5=${result.usedFts5}")
                }
                is KbSearchResult.Error -> {
                    Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ search error · ${result.reason}")
                    statusText.text = getString(R.string.drug_picker_status_error)
                    adapter.clear()
                    adapter.notifyDataSetChanged()
                    currentResults.clear()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pendingSearch?.let { debounceHandler.removeCallbacks(it) }
        currentSearchJob?.cancel()
    }
}
