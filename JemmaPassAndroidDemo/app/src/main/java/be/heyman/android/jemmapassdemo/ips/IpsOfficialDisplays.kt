/*
 * IpsOfficialDisplays.kt — official code-system display names (HL7 validator, cycle 8).
 *
 * `Coding.display` must be a valid display of the code system (tx.fhir.org flags
 * "Wrong Display Name" otherwise). JEMMA keeps its short, friendly labels for the
 * UI and the text QR: they travel in `CodeableConcept.text`, while `coding.display`
 * carries the official term below. Decoding maps it back (friendly text wins).
 *
 * Values copied verbatim from the validator's "Default display is …" messages
 * (device-reports cycle 8) — nothing invented.
 */
package be.heyman.android.jemmapassdemo.ips

object IpsOfficialDisplays {

    private val BY_SYSTEM: Map<String, Map<String, String>> = mapOf(
        IpsCodeSystems.LOINC to mapOf(
            "882-1" to "ABO and Rh group [Type] in Blood",
            "718-7" to "Hemoglobin [Mass/volume] in Blood",
            "2089-1" to "Cholesterol in LDL [Mass/volume] in Serum or Plasma",
            "2160-0" to "Creatinine [Mass/volume] in Serum or Plasma",
            "2823-3" to "Potassium [Moles/volume] in Serum or Plasma",
            "4548-4" to "Hemoglobin A1c/Hemoglobin.total in Blood",
            "33914-3" to "Glomerular filtration rate [Volume Rate/Area] in Serum or Plasma by Creatinine-based formula (MDRD)/1.73 sq M",
        ),
        IpsCodeSystems.SNOMED to mapOf(
            "836378001" to "Japanese encephalitis virus antigen-containing vaccine product",
            "871803007" to "Hepatitis A and Hepatitis B virus antigens only vaccine product",
            "871876003" to "Acellular Bordetella pertussis and Clostridium tetani and Corynebacterium diphtheriae antigens only vaccine product",
            "1181000221105" to "Influenza virus antigen only vaccine product",
            "1801000221105" to "Streptococcus pneumoniae capsular polysaccharide antigen conjugated only vaccine product",
        ),
    )

    fun of(system: String?, code: String?): String? =
        if (code.isNullOrBlank()) null else BY_SYSTEM[system ?: IpsCodeSystems.SNOMED]?.get(code)

    /**
     * Decode side: when `coding.display` is the official term and a friendly text is
     * present, the friendly text becomes the domain display again (lossless round trip).
     */
    fun friendly(system: String?, code: String?, codingDisplay: String?, text: String?): Pair<String?, String?> {
        val official = of(system, code)
        return if (official != null && codingDisplay == official && !text.isNullOrBlank()) text to null
        else codingDisplay to text?.takeIf { it != codingDisplay }
    }
}
