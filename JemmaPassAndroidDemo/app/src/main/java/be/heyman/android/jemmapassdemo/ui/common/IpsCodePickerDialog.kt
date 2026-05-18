/*
 * IpsCodePickerDialog.kt — JEMMA Pass · Plan B · v2.6.0 · L_PICKERS
 *
 * Material AlertDialog réutilisable pour piquer UN code dans un ValueSet
 * IPS. Utilisé par :
 *   - Contacts (1b) : relationship picker (39 V3-RoleCode)
 *   - Allergies (1c) : criticality / clinical-status / category / substance pickers
 *   - Medications (1d) : route picker (à venir)
 *
 * Design :
 *   - Search bar en haut (filter incrémental)
 *   - ListView avec compact label "PREFIX  Display" (PREFIX = emoji ou code)
 *   - Pas de "OK" button — tap on row commit directement (Material 3 spec)
 *   - "Cancel" en footer pour aborter
 *
 * 🆕 v2.6.0 L_PICKERS — Le picker accepte maintenant un map optionnel
 *   `codeToPrefix: Map<String, String>` permettant d'afficher un emoji
 *   contextuel au lieu du code brut. Si un code n'a pas de prefix dans
 *   le map, fallback sur le code (compat backward).
 *   Cas d'usage : substance picker SNOMED — l'emoji catégorie (🍴 food,
 *   💊 medication, 🌿 environment, 🧬 biologic) est plus signifiant pour
 *   le user que le code SNOMED brut.
 *
 * 🎯 PHILOSOPHIE — le picker travaille avec des `PickerItem(code, display)`
 *   abstrait. Le caller passe sa liste et reçoit le code choisi. Aucune
 *   string en dur côté picker. Aucune connaissance du domaine FHIR ici.
 *
 * Logging : tag JEMMA-IPS-PICKER
 */
package be.heyman.android.jemmapassdemo.ui.common

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import be.heyman.android.jemmapassdemo.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Un item du picker — abstraction minimale qui marche pour tous les
 * ValueSets IPS (V3-RoleCode, SNOMED, FHIR enum, etc.).
 *
 * 🆕 v2.6.0o — `searchKey` est maintenant normalisé (lowercase + strip
 * diacritiques NFD) pour que les recherches "me" matchent "Mère",
 * "Belle-mère", etc. Sans cette normalisation, "mère" était stocké
 * tel quel et `searchKey.contains("me")` retournait false car "mè" ≠ "me".
 *
 * @property code la valeur à persister (ex "FTH", "91936005", "high")
 * @property display label localisé (ex "Père", "Allergie à la pénicilline")
 * @property searchKey concat code+display normalisé pour filter (rempli par le caller)
 */
data class IpsPickerItem(
    val code: String,
    val display: String,
    val searchKey: String = normalizeForPickerSearch("${code} ${display}"),
)

/**
 * Normalise une string pour la recherche du picker :
 * lowercase + NFD + strip combining marks (diacritiques).
 * Ex: "Mère" → "mere", "Père" → "pere", "Allergie à" → "allergie a".
 */
internal fun normalizeForPickerSearch(s: String): String {
    val nfd = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
    return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()
}

/**
 * Picker dialog générique. **N'EST PAS** un Fragment Hilt — c'est un
 * simple DialogFragment qui prend ses args via newInstance().
 *
 * Usage :
 *   IpsCodePickerDialog
 *     .newInstance("Choisir une relation", items)
 *     .setOnPicked { picked -> contactDraft.r = picked.code }
 *     .show(childFragmentManager, "tag_picker_rel")
 *
 * ⚠ Le callback ne survit PAS à la rotation. C'est OK pour un picker
 * éphémère ; le caller doit re-instancier au besoin.
 */
class IpsCodePickerDialog : DialogFragment() {

    companion object {
        private const val TAG = "JEMMA-IPS-PICKER"
        private const val ARG_TITLE = "title"
        private const val ARG_ITEMS_CODES = "items_codes"
        private const val ARG_ITEMS_DISPLAYS = "items_displays"

        // 🆕 L_PICKERS : 2 arrays parallèles pour le mapping code→prefix.
        // Si vide, fallback sur "code · display" (comportement legacy).
        private const val ARG_PREFIX_KEYS = "prefix_keys"
        private const val ARG_PREFIX_VALUES = "prefix_values"

        // 🆕 L_HERO_BAR — mapping code→fhirCategory pour le filter chips.
        // Si présent ET au moins une catégorie distincte, on affiche
        // la rangée de chips (ALL · 🍴 · 💊 · 🌿 · 🧬 · 🩹).
        private const val ARG_CATEGORY_KEYS = "category_keys"
        private const val ARG_CATEGORY_VALUES = "category_values"

        // FHIR allergy-intolerance-category codes pour le chip filter.
        // (Dupliqué ici pour éviter une dépendance sur le module kb depuis common.)
        private const val CAT_FOOD = "food"
        private const val CAT_MEDICATION = "medication"
        private const val CAT_ENVIRONMENT = "environment"
        private const val CAT_BIOLOGIC = "biologic"
        // Sentinel : "" / null = "unknown" (codes non-classifiés par le KB).
        private const val CAT_UNKNOWN = "unknown"

        /**
         * Constructeur idiomatique — préserve les args à travers
         * la création du Bundle.
         */
        fun newInstance(title: String, items: List<IpsPickerItem>): IpsCodePickerDialog {
            return newInstance(title, items, emptyMap(), emptyMap())
        }

        /**
         * 🆕 L_PICKERS — Overload avec mapping code→prefix.
         *
         * Si `codeToPrefix["91936005"] = "💊"`, l'item s'affiche
         * "💊  Allergy to penicillin" au lieu de "91936005  ·  Allergy to penicillin".
         *
         * @param codeToPrefix mapping facultatif (default = empty = legacy)
         */
        fun newInstance(
            title: String,
            items: List<IpsPickerItem>,
            codeToPrefix: Map<String, String>,
        ): IpsCodePickerDialog {
            return newInstance(title, items, codeToPrefix, emptyMap())
        }

        /**
         * 🆕 L_HERO_BAR — Full overload : prefix emojis + category filter chips.
         *
         * Si `codeToCategory` est non-vide, le picker affiche une rangée
         * de chips ALL · 🍴 · 💊 · 🌿 · 🧬 · 🩹 permettant de filtrer la
         * liste par catégorie FHIR (food/medication/environment/biologic/unknown).
         *
         * @param codeToCategory mapping code→FHIR category (default = empty = no chips)
         */
        fun newInstance(
            title: String,
            items: List<IpsPickerItem>,
            codeToPrefix: Map<String, String>,
            codeToCategory: Map<String, String>,
        ): IpsCodePickerDialog {
            return IpsCodePickerDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    // 2 arrays parallèles — plus léger qu'un Parcelable
                    putStringArray(ARG_ITEMS_CODES, items.map { it.code }.toTypedArray())
                    putStringArray(ARG_ITEMS_DISPLAYS, items.map { it.display }.toTypedArray())
                    putStringArray(ARG_PREFIX_KEYS, codeToPrefix.keys.toTypedArray())
                    putStringArray(ARG_PREFIX_VALUES, codeToPrefix.values.toTypedArray())
                    putStringArray(ARG_CATEGORY_KEYS, codeToCategory.keys.toTypedArray())
                    putStringArray(ARG_CATEGORY_VALUES, codeToCategory.values.toTypedArray())
                }
            }
        }
    }

    private var onPicked: ((IpsPickerItem) -> Unit)? = null
    private var allItems: List<IpsPickerItem> = emptyList()
    private var filteredItems: MutableList<IpsPickerItem> = mutableListOf()
    private lateinit var adapter: ArrayAdapter<String>

    // 🆕 L_PICKERS — mapping optionnel code→prefix (emoji ou autre).
    // Si vide, fallback sur "code  ·  display" (legacy).
    private var codeToPrefix: Map<String, String> = emptyMap()

    // 🆕 L_HERO_BAR — mapping code→fhirCategory pour le filter chips.
    // Si vide, les chips ne s'affichent pas.
    private var codeToCategory: Map<String, String> = emptyMap()

    // 🆕 L_HERO_BAR — Catégorie actuellement sélectionnée pour le filter.
    // null = "ALL" (pas de filtre). Sinon = un FHIR category code ou
    // CAT_UNKNOWN pour les codes sans catégorie résolue.
    private var selectedCategoryFilter: String? = null

    // 🆕 L_HERO_BAR — Buffer du dernier search query pour pouvoir
    // ré-appliquer search + category combinés à chaque changement.
    private var lastSearchNeedle: String = ""

    /**
     * 🆕 L_PICKERS — Formate une ligne pour l'affichage list.
     * Si un prefix est mappé pour ce code, utilise "prefix  display"
     * (ex: "🍴  Arachides"). Sinon, fallback legacy "code  ·  display"
     * (ex: "91936005  ·  Allergie à la pénicilline").
     */
    private fun formatRow(item: IpsPickerItem): String {
        val prefix = codeToPrefix[item.code]
        return if (prefix != null) {
            "$prefix  ${item.display}"
        } else {
            "${item.code}  ·  ${item.display}"
        }
    }

    /**
     * 🆕 L_HERO_BAR — Re-filtre la liste selon le search query courant
     * ET la catégorie sélectionnée. Appelé à chaque changement de
     * search OU de chip.
     */
    private fun applyFilters(countText: TextView, ctx: android.content.Context) {
        val needle = lastSearchNeedle
        val cat = selectedCategoryFilter
        filteredItems = allItems.filter { item ->
            val matchSearch = needle.isEmpty() || item.searchKey.contains(needle)
            val matchCat = when (cat) {
                null -> true  // ALL
                CAT_UNKNOWN -> codeToCategory[item.code].isNullOrBlank()
                else -> codeToCategory[item.code] == cat
            }
            matchSearch && matchCat
        }.toMutableList()
        adapter.clear()
        adapter.addAll(filteredItems.map { formatRow(it) })
        adapter.notifyDataSetChanged()
        countText.text = ctx.getString(R.string.ips_picker_count, filteredItems.size)
    }

    /**
     * Callback chaînable. **Doit** être appelé AVANT show() sinon le tap
     * sera silencieux (loggué).
     */
    fun setOnPicked(callback: (IpsPickerItem) -> Unit): IpsCodePickerDialog {
        this.onPicked = callback
        return this
    }

    /**
     * 🆕 L_HERO_BAR — Setup les chips de filtrage par catégorie.
     *
     * Si codeToCategory est vide → hide la rangée et return.
     * Sinon : wire les 6 chips (ALL · 🍴 · 💊 · 🌿 · 🧬 · 🩹), set ALL
     * comme initial selection, et bind le listener qui applique les
     * filters combinés (search + category).
     */
    private fun setupCategoryChips(
        view: View,
        countText: TextView,
        ctx: android.content.Context,
    ) {
        val scrollView = view.findViewById<HorizontalScrollView>(R.id.picker_categories_scroll)
        if (codeToCategory.isEmpty()) {
            scrollView.visibility = View.GONE
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🚫 no categories map · chips hidden")
            return
        }
        scrollView.visibility = View.VISIBLE
        val group = view.findViewById<MaterialButtonToggleGroup>(R.id.picker_categories_group)
        val btnAll = view.findViewById<MaterialButton>(R.id.picker_cat_all)
        val btnFood = view.findViewById<MaterialButton>(R.id.picker_cat_food)
        val btnMed = view.findViewById<MaterialButton>(R.id.picker_cat_medication)
        val btnEnv = view.findViewById<MaterialButton>(R.id.picker_cat_environment)
        val btnBio = view.findViewById<MaterialButton>(R.id.picker_cat_biologic)
        val btnUnknown = view.findViewById<MaterialButton>(R.id.picker_cat_unknown)

        // Cocher ALL au départ — pas de filtre.
        group.check(R.id.picker_cat_all)
        selectedCategoryFilter = null

        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedCategoryFilter = when (checkedId) {
                R.id.picker_cat_all -> null
                R.id.picker_cat_food -> CAT_FOOD
                R.id.picker_cat_medication -> CAT_MEDICATION
                R.id.picker_cat_environment -> CAT_ENVIRONMENT
                R.id.picker_cat_biologic -> CAT_BIOLOGIC
                R.id.picker_cat_unknown -> CAT_UNKNOWN
                else -> null
            }
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎛 chip filter changed · " +
                "selected=$selectedCategoryFilter")
            applyFilters(countText, ctx)
        }

        // Pre-count par catégorie pour donner du contexte dans les logs.
        val byCat = codeToCategory.values.groupingBy { it }.eachCount()
        val unknownCount = allItems.size - codeToCategory.size
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 🎛 chips ready · " +
            "food=${byCat[CAT_FOOD] ?: 0} · med=${byCat[CAT_MEDICATION] ?: 0} · " +
            "env=${byCat[CAT_ENVIRONMENT] ?: 0} · bio=${byCat[CAT_BIOLOGIC] ?: 0} · " +
            "unknown=$unknownCount")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val title = args.getString(ARG_TITLE) ?: ""
        val codes = args.getStringArray(ARG_ITEMS_CODES) ?: emptyArray()
        val displays = args.getStringArray(ARG_ITEMS_DISPLAYS) ?: emptyArray()

        // 🆕 L_PICKERS — récupère le mapping code→prefix (peut être vide)
        val prefixKeys = args.getStringArray(ARG_PREFIX_KEYS) ?: emptyArray()
        val prefixValues = args.getStringArray(ARG_PREFIX_VALUES) ?: emptyArray()
        codeToPrefix = prefixKeys.zip(prefixValues).toMap()

        // 🆕 L_HERO_BAR — récupère le mapping code→category (peut être vide)
        val categoryKeys = args.getStringArray(ARG_CATEGORY_KEYS) ?: emptyArray()
        val categoryValues = args.getStringArray(ARG_CATEGORY_VALUES) ?: emptyArray()
        codeToCategory = categoryKeys.zip(categoryValues).toMap()

        allItems = codes.zip(displays).map { (c, d) -> IpsPickerItem(c, d) }
        filteredItems = allItems.toMutableList()

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 picker open · title='$title' · " +
            "n=${allItems.size} · prefixes=${codeToPrefix.size} · " +
            "categories=${codeToCategory.size}")

        val ctx = requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_ips_code_picker, null, false)
        val searchEdit = view.findViewById<EditText>(R.id.picker_search)
        val listView = view.findViewById<ListView>(R.id.picker_list)
        val countText = view.findViewById<TextView>(R.id.picker_count)

        adapter = ArrayAdapter(
            ctx,
            android.R.layout.simple_list_item_1,
            filteredItems.map { formatRow(it) }.toMutableList(),
        )
        listView.adapter = adapter

        countText.text = ctx.getString(R.string.ips_picker_count, filteredItems.size)

        // 🆕 L_HERO_BAR — Setup chips de catégorie si on a un mapping non-vide
        setupCategoryChips(view, countText, ctx)

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // 🆕 v2.6.0o — Normaliser la query aussi (strip diacritics)
                // pour matcher les normalized searchKey symétriquement.
                val raw = (s?.toString() ?: "").trim()
                lastSearchNeedle = normalizeForPickerSearch(raw)
                applyFilters(countText, ctx)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val picked = filteredItems[position]
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ picker pick · " +
                "code=${picked.code} · display='${picked.display}'")
            onPicked?.invoke(picked) ?: Log.w(TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ no callback set — pick is lost")
            dismiss()
        }

        return MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(view)
            .setNegativeButton(R.string.ips_picker_cancel) { d, _ ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ↩ picker cancel")
                d.dismiss()
            }
            .create()
    }
}
