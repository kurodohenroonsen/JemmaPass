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
                bd = "1968-04-12",
                nat = "BE",
                bt = "A+",
                adr = "Rue de la Paix 12, 5660 Couvin, Belgique",
                tel = "+32-470-XX-XX-XX",
                eml = "kurodo@henro.be",
                idn = "BE-680412-123-45",
                lang = "fr-FR",
                ct = listOf(
                    JContact(
                        n = "Misako Kudoro",
                        r = "spouse",
                        p = "+32-478-45-45-45",
                        e = "misako@example.com",
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
            md = listOf(
                JMedication(
                    c = "C09AA02",
                    t = "Enalapril 10mg",
                    r = "O",
                    v = "1",
                    u = "tab",
                    rs = "Daily",
                    rc = "I10",
                    displayLabel = "Hypertension treatment · 高血圧症治療",
                    codeSystem = "http://www.whocc.no/atc",
                    status = "active"
                ),
                JMedication(
                    c = "B01AC06",
                    t = "Aspirin 100mg",
                    r = "O",
                    v = "1",
                    u = "tab",
                    rs = "Daily",
                    rc = "I25",
                    displayLabel = "Cardioprotection · 心保護",
                    codeSystem = "http://www.whocc.no/atc",
                    status = "active"
                )
            ),
            cn = listOf(
                JCondition(
                    c = "I10",
                    d = "Essential hypertension",
                    st = "A",
                    displayLabel = "高血圧症 · Hypertension"
                )
            )
        )

        val misako = JemmaProfileJ(
            j = "1.2",
            sid = "demo_misako",
            p = JPatient(
                gn = "Misako",
                fn = "Henroonsen",
                gs = "F",
                bd = "1957-08-15",
                nat = "JP",
                bt = "AB+",
                adr = "75 Avenue Louise, Bruxelles, Belgique",
                tel = "+32-478-45-45-45",
                eml = "misako@example.com",
                idn = "BE-570815-987-65",
                lang = "ja-JP",
                ct = listOf(
                    JContact(
                        n = "Kurodo Henro",
                        r = "spouse",
                        p = "+32-470-XX-XX-XX",
                        e = "kurodo@henro.be",
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
                )
            ),
            cn = listOf(
                JCondition(
                    c = "I10",
                    d = "Essential hypertension",
                    st = "A",
                    displayLabel = "高血圧症 · Hypertension"
                )
            )
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
                tel = "+81-90-XXXX-XXXX",
                eml = "haru@tanaka.jp",
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
            cn = listOf(
                JCondition(
                    c = "I50.9",
                    d = "Congestive heart failure",
                    st = "A",
                    displayLabel = "うっ血性心不全 · Insuffisance cardiaque"
                )
            )
        )

        return listOf(kurodo, misako, haru)
    }
}
