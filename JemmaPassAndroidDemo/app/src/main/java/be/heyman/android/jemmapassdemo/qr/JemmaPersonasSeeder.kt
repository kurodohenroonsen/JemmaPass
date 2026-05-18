package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JContact
import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.qr.JCondition

object JemmaPersonasSeeder {

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
