package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JContact
import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.qr.JCondition
import be.heyman.android.jemmapassdemo.ips.IpsCodeSystems
import be.heyman.android.jemmapassdemo.ips.IpsDevice
import be.heyman.android.jemmapassdemo.ips.IpsImmunization
import be.heyman.android.jemmapassdemo.ips.IpsNativePillars
import be.heyman.android.jemmapassdemo.ips.IpsConditionSeverity
import be.heyman.android.jemmapassdemo.ips.IpsPastProblem
import be.heyman.android.jemmapassdemo.ips.IpsProblem
import be.heyman.android.jemmapassdemo.ips.IpsPregnancyObs
import be.heyman.android.jemmapassdemo.ips.IpsProcedure
import be.heyman.android.jemmapassdemo.ips.IpsResult
import be.heyman.android.jemmapassdemo.ips.IpsResultCategory
import be.heyman.android.jemmapassdemo.ips.IpsResultInterpretation

object JemmaPersonasSeeder {

    const val SID_KURODO = "demo_kurodo"
    const val SID_KAMEKICHI = "demo_kamekichi"
    const val SID_HARU = "demo_haru"

    /**
     * FHIR-native demo data per persona (Immunizations). Stable ids so the
     * seeded Bundles are reproducible; null when a persona has none.
     */
    fun getDemoNativePillars(sid: String): IpsNativePillars? = when (sid) {
        SID_KURODO -> IpsNativePillars(
            immunizations = listOf(
                IpsImmunization(
                    id = "im-kurodo-tdap-2022", code = "871876003", system = IpsCodeSystems.SNOMED,
                    display = "Tetanus-diphtheria-pertussis (Tdap)", date = "2022-05-17",
                    lotNumber = "AC52B213BC", manufacturer = "Sanofi Pasteur",
                    performer = "Dr. Lambert, Couvin",
                ),
                IpsImmunization(
                    id = "im-kurodo-hepab-2016", code = "871803007", system = IpsCodeSystems.SNOMED,
                    display = "Hepatitis A + B vaccine", date = "2016-03-02",
                    doseNumber = 3, seriesDoses = 3, manufacturer = "GSK",
                    note = "Series completed before the first Shikoku pilgrimage",
                ),
                IpsImmunization(
                    id = "im-kurodo-je-2023", code = "836378001", system = IpsCodeSystems.SNOMED,
                    display = "Japanese encephalitis vaccine", date = "2023-01-20",
                    doseNumber = 2, seriesDoses = 2, manufacturer = "Valneva",
                ),
                IpsImmunization(
                    id = "im-kurodo-covid-2021", code = "1119349007", system = IpsCodeSystems.SNOMED,
                    display = "COVID-19 mRNA vaccine", date = "2021-06-11",
                    doseNumber = 2, seriesDoses = 2, lotNumber = "FD0168", manufacturer = "Pfizer-BioNTech",
                ),
            ),
            procedures = listOf(
                IpsProcedure(
                    id = "pr-kurodo-appendectomy-1995", code = "80146002", system = IpsCodeSystems.SNOMED,
                    display = "Appendectomy", date = "1995-07-12", location = "CHU UCL Namur (Godinne)",
                    outcome = "Uneventful recovery", note = "Laparoscopic",
                ),
                IpsProcedure(
                    id = "pr-kurodo-colonoscopy-2024", code = "73761001", system = IpsCodeSystems.SNOMED,
                    display = "Colonoscopy", date = "2024-02-19", performer = "Dr. Lambert, Couvin",
                    outcome = "Normal — screening", note = "Next screening 2034",
                ),
            ),
            // Kurodo carries no device: the empty-pillar path stays covered by a persona.
            // (Each persona also gets a derived blood-group Observation from `p.bt` at write time.)
            results = listOf(
                IpsResult(
                    id = "rs-kurodo-hba1c-2026", code = "4548-4", system = IpsCodeSystems.LOINC,
                    display = "Hemoglobin A1c", date = "2026-01-15", value = "5.6", unit = "%",
                    interpretation = IpsResultInterpretation.NORMAL, refLow = "4", refHigh = "6",
                    performer = "Laboratoire CHU UCL Namur",
                ),
                IpsResult(
                    id = "rs-kurodo-ldl-2026", code = "2089-1", system = IpsCodeSystems.LOINC,
                    display = "LDL cholesterol", date = "2026-01-15", value = "131", unit = "mg/dL",
                    interpretation = IpsResultInterpretation.HIGH, refHigh = "100",
                    performer = "Laboratoire CHU UCL Namur", note = "Lifestyle advice, recheck in 6 months",
                ),
                IpsResult(
                    id = "rs-kurodo-creat-2026", code = "2160-0", system = IpsCodeSystems.LOINC,
                    display = "Creatinine (serum/plasma)", date = "2026-01-15", value = "0.9", unit = "mg/dL",
                    interpretation = IpsResultInterpretation.NORMAL, refLow = "0.7", refHigh = "1.2",
                    performer = "Laboratoire CHU UCL Namur",
                ),
            ),
            problems = listOf(
                IpsProblem(
                    id = "cn-kurodo-hypercholesterolemia", code = "13644009", system = IpsCodeSystems.SNOMED,
                    display = "Hypercholesterolemia", onset = "2026-01",
                    severity = IpsConditionSeverity.MILD, note = "LDL 131 mg/dL — lifestyle first",
                ),
            ),
            pastProblems = listOf(
                IpsPastProblem(
                    id = "ph-kurodo-appendicitis-1995", code = "74400008", system = IpsCodeSystems.SNOMED,
                    display = "Appendicitis", onset = "1995-07-10", abatement = "1995-07-12",
                    severity = IpsConditionSeverity.MODERATE, note = "Treated by appendectomy",
                ),
                IpsPastProblem(
                    id = "ph-kurodo-pneumonia-2018", code = "233604007", system = IpsCodeSystems.SNOMED,
                    display = "Pneumonia", onset = "2018-02", abatement = "2018-03",
                    severity = IpsConditionSeverity.MILD,
                ),
            ),
        )
        SID_HARU -> IpsNativePillars(
            immunizations = listOf(
                IpsImmunization(
                    id = "im-haru-flu-2025", code = "1181000221105", system = IpsCodeSystems.SNOMED,
                    display = "Seasonal influenza vaccine", date = "2025-10-14",
                    performer = "青森市民病院", note = "定期接種（高齢者）",
                ),
                IpsImmunization(
                    id = "im-haru-pcv-2021", code = "1801000221105", system = IpsCodeSystems.SNOMED,
                    display = "Pneumococcal conjugate vaccine (PCV)", date = "2021-04-06",
                    doseNumber = 1, seriesDoses = 1,
                ),
                IpsImmunization(
                    id = "im-haru-covid-2024", code = "1119349007", system = IpsCodeSystems.SNOMED,
                    display = "COVID-19 mRNA vaccine", date = "2024-11-02",
                    doseNumber = 7, lotNumber = "HG1282", manufacturer = "Pfizer-BioNTech",
                ),
            ),
            procedures = listOf(
                IpsProcedure(
                    id = "pr-haru-cabg-2015", code = "232717009", system = IpsCodeSystems.SNOMED,
                    display = "Coronary artery bypass graft", date = "2015-09-02",
                    location = "青森市民病院", bodySite = "Heart", outcome = "Triple bypass, good recovery",
                ),
                IpsProcedure(
                    id = "pr-haru-cesarean-1975", code = "11466000", system = IpsCodeSystems.SNOMED,
                    display = "Cesarean section", date = "1975",
                ),
            ),
            devices = listOf(
                IpsDevice(
                    id = "dv-haru-pacemaker-2021", code = "14106009", system = IpsCodeSystems.SNOMED,
                    display = "Cardiac pacemaker", udi = "(01)00643169007222(21)PJN1234567",
                    manufacturer = "Medtronic", model = "Azure XT DR MRI SureScan", serial = "PJN1234567",
                    date = "2021-03-15", bodySite = "Left pectoral", note = "MRI-conditional",
                ),
                IpsDevice(
                    id = "dv-haru-hearing-aid-2019", code = "6012004", system = IpsCodeSystems.SNOMED,
                    display = "Hearing aid", manufacturer = "Phonak", date = "2019-06", bodySite = "Both ears",
                ),
            ),
            results = listOf(
                IpsResult(
                    id = "rs-haru-potassium-2026", code = "2823-3", system = IpsCodeSystems.LOINC,
                    display = "Potassium", date = "2026-02-10", value = "4.1", unit = "mmol/L",
                    interpretation = IpsResultInterpretation.NORMAL, refLow = "3.5", refHigh = "5.1",
                    performer = "Matsuyama Red Cross Hospital laboratory",
                ),
                IpsResult(
                    id = "rs-haru-hemoglobin-2026", code = "718-7", system = IpsCodeSystems.LOINC,
                    display = "Hemoglobin", date = "2026-02-10", value = "11.8", unit = "g/dL",
                    interpretation = IpsResultInterpretation.LOW, refLow = "12", refHigh = "16",
                    performer = "Matsuyama Red Cross Hospital laboratory",
                ),
                IpsResult(
                    id = "rs-haru-egfr-2026", code = "33914-3", system = IpsCodeSystems.LOINC,
                    display = "eGFR (MDRD)", date = "2026-02-10", value = "48", unit = "mL/min/{1.73_m2}",
                    interpretation = IpsResultInterpretation.LOW, refLow = "60",
                    performer = "Matsuyama Red Cross Hospital laboratory", note = "CKD stage 3a — adjust renally cleared drugs",
                ),
                IpsResult(
                    id = "rs-haru-chest-xray-2025", text = "Chest X-ray", date = "2025-12-03",
                    category = IpsResultCategory.IMAGING, valueText = "Mild cardiomegaly, no pleural effusion",
                    performer = "Radiology, Matsuyama Red Cross Hospital",
                ),
            ),
            // 🤰 obstetric summary (sprint 6): two term live births (one by cesarean, 1975).
            pregnancy = listOf(
                IpsPregnancyObs(id = "pg-haru-births-total", code = "11640-0", count = 2, date = "2026-02-10"),
                IpsPregnancyObs(id = "pg-haru-births-live", code = "11636-8", count = 2, date = "2026-02-10"),
                IpsPregnancyObs(id = "pg-haru-births-term", code = "11639-2", count = 2, date = "2026-02-10"),
            ),
            problems = listOf(
                IpsProblem(
                    id = "cn-haru-heart-failure", code = "84114007", system = IpsCodeSystems.SNOMED,
                    display = "Heart failure", onset = "2020-11",
                    severity = IpsConditionSeverity.MODERATE, note = "NYHA II, on furosemide",
                ),
                IpsProblem(
                    id = "cn-haru-ckd3", code = "433144002", system = IpsCodeSystems.SNOMED,
                    display = "Chronic kidney disease stage 3", onset = "2022",
                    note = "eGFR 48 (2026-02)",
                ),
            ),
            pastProblems = listOf(
                IpsPastProblem(
                    id = "ph-haru-mi-2015", code = "22298006", system = IpsCodeSystems.SNOMED,
                    display = "Myocardial infarction", onset = "2015-08-27", abatement = "2015-09",
                    severity = IpsConditionSeverity.SEVERE, note = "Treated by coronary bypass (2015-09)",
                ),
                IpsPastProblem(
                    id = "ph-haru-tb-1962", code = "56717001", system = IpsCodeSystems.SNOMED,
                    display = "Tuberculosis", onset = "1962", abatement = "1963",
                    note = "Pulmonary, treated — calcified scar on chest X-ray",
                ),
            ),
        )
        // Kamekichi: problem list only (bisoprolol, warfarin and isosorbide dinitrate in `md`).
        SID_KAMEKICHI -> IpsNativePillars(
            problems = listOf(
                IpsProblem(
                    id = "cn-kamekichi-hypertension", code = "59621000", system = IpsCodeSystems.SNOMED,
                    display = "Essential hypertension", onset = "2010",
                ),
                IpsProblem(
                    id = "cn-kamekichi-af", code = "49436004", system = IpsCodeSystems.SNOMED,
                    display = "Atrial fibrillation", onset = "2018-06",
                    severity = IpsConditionSeverity.MODERATE, note = "Anticoagulated (warfarin)",
                ),
                IpsProblem(
                    id = "cn-kamekichi-angina", code = "194828000", system = IpsCodeSystems.SNOMED,
                    display = "Angina pectoris", onset = "2021",
                ),
            ),
        )
        else -> null
    }

    fun getDemoProfiles(): List<JemmaProfileJ> {
        val kurodo = JemmaProfileJ(
            j = "1.2",
            sid = "demo_kurodo",
            p = JPatient(
                gn = "Kurodo",
                fn = "Henro",
                gs = "M",
                bd = "1979-04-04",
                nat = "BE",
                bt = "A+",
                adr = "Rue de la Paix 12, 5660 Couvin, Belgique",
                idn = "BE-680412-123-45",
                lang = "fr-FR",
                ct = listOf(
                    JContact(
                        n = "Kamekichi",
                        r = "friend",
                        adr = "75 Avenue Louise, Bruxelles"
                    )
                )
            ),
            al = listOf(
                JAllergy(
                    c = "91936005",
                    s = "H",
                    st = "A",
                    d = "Allergy to penicillin",
                    m = "Anaphylactic shock 2019-03",
                    displayLabel = "ペニシリンアレルギー · Allergie à la pénicilline",
                    codeSystem = "http://snomed.info/sct",
                    category = "medication"
                ),
                JAllergy(
                    c = "417532002",  // Allergy to fish (KB: UMLS C0856904 → SNOMED 417532002; 232347008 was animal dander)
                    s = "H",
                    st = "A",
                    d = "Allergy to fish",
                    m = "Urticaria + tongue swelling",
                    displayLabel = "魚アレルギー · Allergie au poisson",
                    codeSystem = "http://snomed.info/sct",
                    category = "food"
                ),
                JAllergy(
                    c = "419263009",
                    s = "L",
                    st = "A",
                    d = "Allergy to tree pollen",
                    displayLabel = "花粉症 · Pollinose",
                    codeSystem = "http://snomed.info/sct",
                    category = "environment"
                )
            ),
            md = emptyList(),
            cn = emptyList()
        )

        val kamekichi = JemmaProfileJ(
            j = "1.2",
            sid = "demo_kamekichi",
            p = JPatient(
                gn = "Kamekichi",
                fn = "",
                gs = "M",
                bd = "2000-05-20",
                nat = "JP",
                bt = "B+",
                adr = "75 Avenue Louise, Bruxelles, Belgique",
                idn = "BE-570815-987-65",
                lang = "ja-JP",
                ct = listOf(
                    JContact(
                        n = "Kurodo Henro",
                        r = "friend",
                        adr = "Rue de la Paix 12, 5660 Couvin"
                    )
                )
            ),
            al = listOf(
                JAllergy(
                    c = "300916003",
                    s = "L",
                    st = "A",
                    d = "Latex allergy",
                    displayLabel = "ラテックスアレルギー · Allergie au latex",
                    codeSystem = "http://snomed.info/sct",
                    category = "environment"
                ),
                JAllergy(
                    c = "91936005",
                    s = "H",
                    st = "A",
                    d = "Allergy to penicillin",
                    displayLabel = "ペニシリンアレルギー · Allergie à la pénicilline",
                    codeSystem = "http://snomed.info/sct",
                    category = "medication"
                ),
                JAllergy(
                    c = "91935009",
                    s = "H",
                    st = "A",
                    d = "Allergy to peanuts",
                    displayLabel = "ピーナッツアレルギー · Allergie aux arachides",
                    codeSystem = "http://snomed.info/sct",
                    category = "food"
                )
            ),
            md = listOf(
                JMedication(
                    c = "C07AB07",
                    t = "Bisoprolol 2.5mg",
                    r = "O",
                    v = "1",
                    u = "tab",
                    rs = "Daily",
                    rc = "I10",
                    displayLabel = "Beta-blocker for hypertension · 高血圧用ベータ遮断薬",
                    codeSystem = "http://www.whocc.no/atc",
                    status = "active"
                ),
                JMedication(
                    c = "B01AA03",
                    t = "Warfarin 5mg",
                    r = "O",
                    v = "5",
                    u = "mg",
                    rs = "Daily",
                    displayLabel = "Anticoagulant · ワーファリン",
                    codeSystem = "http://www.whocc.no/atc",
                    status = "active"
                ),
                JMedication(
                    c = "M01AE01",
                    t = "Ibuprofen 400mg",
                    r = "O",
                    v = "400",
                    u = "mg",
                    rs = "TID PRN pain",
                    displayLabel = "NSAID pain relief · イブプロフェン",
                    codeSystem = "http://www.whocc.no/atc",
                    status = "active"
                ),
                JMedication(
                    c = "G04BE03",
                    t = "Sildenafil 50mg",
                    r = "O",
                    v = "50",
                    u = "mg",
                    rs = "PRN",
                    displayLabel = "Erectile dysfunction treatment · シルデナフィル",
                    codeSystem = "http://www.whocc.no/atc",
                    status = "active"
                ),
                JMedication(
                    c = "C01DA08",
                    t = "Isosorbide Dinitrate 20mg",
                    r = "O",
                    v = "20",
                    u = "mg",
                    rs = "BID",
                    displayLabel = "Angina pectoris prevention · 硝酸イソソルビド",
                    codeSystem = "http://www.whocc.no/atc",
                    status = "active"
                )
            ),
            cn = emptyList()
        )

        val haru = JemmaProfileJ(
            j = "1.2",
            sid = "demo_haru",
            p = JPatient(
                gn = "Haru",
                fn = "Tanaka",
                gs = "F",
                bd = "1946-02-08",
                nat = "JP",
                bt = "O+",
                adr = "Aomori, Japan",
                idn = "JP-12345678",
                lang = "ja-JP"
            ),
            al = listOf(
                JAllergy(
                    c = "782594005",  // Allergy to soy protein (IPS free set; 419474003 was mold)
                    s = "L",
                    st = "A",
                    d = "Allergy to soy protein",
                    m = "Mild GI symptoms",
                    displayLabel = "大豆タンパク質アレルギー · Allergie aux protéines de soja",
                    codeSystem = "http://snomed.info/sct",
                    category = "food"
                )
            ),
            md = listOf(
                JMedication(
                    c = "R06AX26",
                    t = "Allegra FX (fexofenadine 60mg)",
                    r = "O",
                    v = "1",
                    u = "tab",
                    rs = "BID",
                    rc = "J30",
                    displayLabel = "Antihistamine · 抗ヒスタミン薬",
                    codeSystem = "http://www.whocc.no/atc",
                    status = "active"
                ),
                JMedication(
                    c = "R05DA09",
                    t = "Medicon Pro (dextromethorphan)",
                    r = "O",
                    v = "1",
                    u = "tab",
                    rs = "PRN cough",
                    displayLabel = "Antitussive · 鎮咳薬",
                    codeSystem = "http://www.whocc.no/atc",
                    status = "active"
                ),
                JMedication(
                    c = "C03CA01",
                    t = "Furosemide 20mg",
                    r = "O",
                    v = "1",
                    u = "tab",
                    rs = "Daily",
                    rc = "I50",
                    displayLabel = "Diuretic for heart failure · 心不全用利尿薬",
                    codeSystem = "http://www.whocc.no/atc",
                    status = "active"
                )
            ),
            cn = emptyList()
        )

        return listOf(kurodo, kamekichi, haru)
    }
}
