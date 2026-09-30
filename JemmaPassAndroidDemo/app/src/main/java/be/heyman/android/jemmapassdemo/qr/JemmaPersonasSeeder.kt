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
import be.heyman.android.jemmapassdemo.ips.IpsProcedure

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
                    c = "232347008",
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
                    c = "419474003",
                    s = "L",
                    st = "A",
                    d = "Allergy to soy",
                    m = "Mild GI symptoms",
                    displayLabel = "大豆アレルギー · Allergie au soja",
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
