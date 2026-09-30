package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.qr.JemmaTextPayloadBuilder.Lang

object JemmaTranslations {

    private val CLINICAL_SUMMARIES = mapOf(
        Lang.EN to mapOf(
            "header" to "🏥 === JEMMA CLINICAL SUMMARY (EN) ===",
            "patient_title" to "PATIENT",
            "patient_birth" to "Birth",
            "patient_blood" to "Blood",
            "patient_lang" to "Language",
            "patient_addr" to "Address",
            "patient_phone" to "Phone",
            "patient_email" to "Email",
            "patient_id" to "ID",
            "allergies_title" to "ALLERGIES",
            "medications_title" to "MEDICATIONS",
            "conditions_title" to "CONDITIONS",
            "immunizations_title" to "IMMUNIZATIONS",
            "procedures_title" to "PROCEDURES",
            "devices_title" to "MEDICAL DEVICES",
            "contacts_title" to "CONTACTS",
            "footer" to "✅ JEMMA on-device · `_j 1.2`",
            "empty" to "(none)"
        ),
        Lang.FR to mapOf(
            "header" to "🏥 === JEMMA CLINICAL SUMMARY (FR) ===",
            "patient_title" to "PATIENT",
            "patient_birth" to "Naissance",
            "patient_blood" to "Groupe",
            "patient_lang" to "Langue",
            "patient_addr" to "Adresse",
            "patient_phone" to "Tél",
            "patient_email" to "Email",
            "patient_id" to "ID",
            "allergies_title" to "ALLERGIES",
            "medications_title" to "MÉDICAMENTS",
            "conditions_title" to "CONDITIONS",
            "immunizations_title" to "VACCINATIONS",
            "procedures_title" to "INTERVENTIONS",
            "devices_title" to "DISPOSITIFS MÉDICAUX",
            "contacts_title" to "CONTACTS",
            "footer" to "✅ JEMMA on-device · `_j 1.2`",
            "empty" to "(aucun)"
        ),
        Lang.JA to mapOf(
            "header" to "🏥 === JEMMA 臨床サマリー (JA) ===",
            "patient_title" to "患者",
            "patient_birth" to "生年月日",
            "patient_blood" to "血液型",
            "patient_lang" to "言語",
            "patient_addr" to "住所",
            "patient_phone" to "電話",
            "patient_email" to "メール",
            "patient_id" to "ID",
            "allergies_title" to "アレルギー",
            "medications_title" to "服薬",
            "conditions_title" to "病態",
            "immunizations_title" to "予防接種",
            "procedures_title" to "処置・手術歴",
            "devices_title" to "医療機器",
            "contacts_title" to "緊急連絡先",
            "footer" to "✅ JEMMA on-device · `_j 1.2`",
            "empty" to "(なし)"
        ),
        Lang.ES to mapOf(
            "header" to "🏥 === JEMMA RESUMEN CLÍNICO (ES) ===",
            "patient_title" to "PACIENTE",
            "patient_birth" to "Nacimiento",
            "patient_blood" to "Grupo",
            "patient_lang" to "Idioma",
            "patient_addr" to "Dirección",
            "patient_phone" to "Tel",
            "patient_email" to "Email",
            "patient_id" to "ID",
            "allergies_title" to "ALERGIAS",
            "medications_title" to "MEDICAMENTOS",
            "conditions_title" to "CONDICIONES",
            "immunizations_title" to "VACUNAS",
            "procedures_title" to "PROCEDIMIENTOS",
            "devices_title" to "DISPOSITIVOS MÉDICOS",
            "contacts_title" to "CONTACTOS",
            "footer" to "✅ JEMMA en el dispositivo · `_j 1.2`",
            "empty" to "(ninguno)"
        ),
        Lang.DE to mapOf(
            "header" to "🏥 === JEMMA KLINISCHER AUSZUG (DE) ===",
            "patient_title" to "PATIENT",
            "patient_birth" to "Geburtsdatum",
            "patient_blood" to "Blutgruppe",
            "patient_lang" to "Sprache",
            "patient_addr" to "Adresse",
            "patient_phone" to "Tel",
            "patient_email" to "E-Mail",
            "patient_id" to "ID",
            "allergies_title" to "ALLERGIEN",
            "medications_title" to "MEDIKAMENTE",
            "conditions_title" to "BEFUNDE",
            "immunizations_title" to "IMPFUNGEN",
            "procedures_title" to "EINGRIFFE",
            "devices_title" to "MEDIZINPRODUKTE",
            "contacts_title" to "KONTAKTE",
            "footer" to "✅ JEMMA auf dem Gerät · `_j 1.2`",
            "empty" to "(keine)"
        ),
        Lang.IT to mapOf(
            "header" to "🏥 === JEMMA QUADRO CLINICO (IT) ===",
            "patient_title" to "PAZIENTE",
            "patient_birth" to "Data nascita",
            "patient_blood" to "Gruppo",
            "patient_lang" to "Lingua",
            "patient_addr" to "Indirizzo",
            "patient_phone" to "Tel",
            "patient_email" to "Email",
            "patient_id" to "ID",
            "allergies_title" to "ALLERGIE",
            "medications_title" to "FARMACI",
            "conditions_title" to "CONDIZIONI",
            "immunizations_title" to "VACCINAZIONI",
            "procedures_title" to "PROCEDURE",
            "devices_title" to "DISPOSITIVI MEDICI",
            "contacts_title" to "CONTATTI",
            "footer" to "✅ JEMMA sul dispositivo · `_j 1.2`",
            "empty" to "(nessuno)"
        ),
        Lang.PT to mapOf(
            "header" to "🏥 === RESUMO CLÍNICO JEMMA (PT) ===",
            "patient_title" to "PACIENTE",
            "patient_birth" to "Nascimento",
            "patient_blood" to "Grupo",
            "patient_lang" to "Idioma",
            "patient_addr" to "Endereço",
            "patient_phone" to "Tel",
            "patient_email" to "E-mail",
            "patient_id" to "ID",
            "allergies_title" to "ALERGIAS",
            "medications_title" to "MEDICAMENTOS",
            "conditions_title" to "CONDIÇÕES",
            "immunizations_title" to "VACINAS",
            "procedures_title" to "PROCEDIMENTOS",
            "devices_title" to "DISPOSITIVOS MÉDICOS",
            "contacts_title" to "CONTATOS",
            "footer" to "✅ JEMMA no dispositivo · `_j 1.2`",
            "empty" to "(nenhum)"
        ),
        Lang.NL to mapOf(
            "header" to "🏥 === JEMMA MEDISCH DOSSIER (NL) ===",
            "patient_title" to "PATIËNT",
            "patient_birth" to "Geboortedatum",
            "patient_blood" to "Bloedgroep",
            "patient_lang" to "Taal",
            "patient_addr" to "Adres",
            "patient_phone" to "Tel",
            "patient_email" to "E-mail",
            "patient_id" to "ID",
            "allergies_title" to "ALLERGIEËN",
            "medications_title" to "MEDICATIE",
            "conditions_title" to "AANDOENINGEN",
            "immunizations_title" to "VACCINATIES",
            "procedures_title" to "INGREPEN",
            "devices_title" to "MEDISCHE HULPMIDDELEN",
            "contacts_title" to "CONTACTEN",
            "footer" to "✅ JEMMA op apparaat · `_j 1.2`",
            "empty" to "(geen)"
        ),
        Lang.ZH to mapOf(
            "header" to "🏥 === JEMMA 临床摘要 (ZH) ===",
            "patient_title" to "患者",
            "patient_birth" to "出生日期",
            "patient_blood" to "血型",
            "patient_lang" to "语言",
            "patient_addr" to "地址",
            "patient_phone" to "电话",
            "patient_email" to "电子邮件",
            "patient_id" to "身份证号",
            "allergies_title" to "过敏史",
            "medications_title" to "药物",
            "conditions_title" to "病况",
            "immunizations_title" to "免疫接种",
            "procedures_title" to "手术与操作",
            "devices_title" to "医疗器械",
            "contacts_title" to "紧急联系人",
            "footer" to "✅ JEMMA 本地安全防护 · `_j 1.2`",
            "empty" to "(无)"
        ),
        Lang.KO to mapOf(
            "header" to "🏥 === JEMMA 임상 요약 (KO) ===",
            "patient_title" to "환자",
            "patient_birth" to "생년월일",
            "patient_blood" to "혈액형",
            "patient_lang" to "언어",
            "patient_addr" to "주소",
            "patient_phone" to "전화",
            "patient_email" to "이메일",
            "patient_id" to "ID",
            "allergies_title" to "알레르기",
            "medications_title" to "복용 약물",
            "conditions_title" to "질환 및 상태",
            "immunizations_title" to "예방접종",
            "procedures_title" to "시술·수술",
            "devices_title" to "의료기기",
            "contacts_title" to "비상 연락처",
            "footer" to "✅ JEMMA 기기 자체 저장 · `_j 1.2`",
            "empty" to "(없음)"
        ),
        Lang.AR to mapOf(
            "header" to "🏥 === ملخص جيما الطبي (AR) ===",
            "patient_title" to "المريض",
            "patient_birth" to "تاريخ الميلاد",
            "patient_blood" to "فصيلة الدم",
            "patient_lang" to "اللغة",
            "patient_addr" to "العنوان",
            "patient_phone" to "الهاتف",
            "patient_email" to "البريد الإلكتروني",
            "patient_id" to "الهوية",
            "allergies_title" to "الحساسية",
            "medications_title" to "الأدوية",
            "conditions_title" to "الحالات الطبية",
            "immunizations_title" to "التطعيمات",
            "procedures_title" to "الإجراءات الطبية",
            "devices_title" to "الأجهزة الطبية",
            "contacts_title" to "جهات الطوارئ",
            "footer" to "✅ جيما على الجهاز · `_j 1.2`",
            "empty" to "(لا يوجد)"
        ),
        Lang.RU to mapOf(
            "header" to "🏥 === КЛИНИЧЕСКАЯ СВОДКА JEMMA (RU) ===",
            "patient_title" to "ПАЦИЕНТ",
            "patient_birth" to "Родился(ась)",
            "patient_blood" to "Группа",
            "patient_lang" to "Язык",
            "patient_addr" to "Адрес",
            "patient_phone" to "Тел",
            "patient_email" to "Email",
            "patient_id" to "ID",
            "allergies_title" to "АЛЛЕРГИЯ",
            "medications_title" to "ЛЕКАРСТВА",
            "conditions_title" to "ЗАБОЛЕВАНИЯ",
            "immunizations_title" to "ПРИВИВКИ",
            "procedures_title" to "ПРОЦЕДУРЫ И ОПЕРАЦИИ",
            "devices_title" to "МЕДИЦИНСКИЕ УСТРОЙСТВА",
            "contacts_title" to "КОНТАКТЫ",
            "footer" to "✅ JEMMA на устройстве · `_j 1.2`",
            "empty" to "(нет)"
        ),
        Lang.HI to mapOf(
            "header" to "🏥 === जेम्मा नैदानिक सारांश (HI) ===",
            "patient_title" to "मरीज",
            "patient_birth" to "जन्म तिथि",
            "patient_blood" to "रक्त समूह",
            "patient_lang" to "भाषा",
            "patient_addr" to "पता",
            "patient_phone" to "फ़ोन",
            "patient_email" to "ईमेल",
            "patient_id" to "आईडी",
            "allergies_title" to "एलर्जी",
            "medications_title" to "दवाएं",
            "conditions_title" to "बीमारी",
            "immunizations_title" to "टीकाकरण",
            "procedures_title" to "प्रक्रियाएँ",
            "devices_title" to "चिकित्सा उपकरण",
            "contacts_title" to "संपर्क",
            "footer" to "✅ जेम्मा डिवाइस पर · `_j 1.2`",
            "empty" to "(कोई नहीं)"
        ),
        Lang.BN to mapOf(
            "header" to "🏥 === জেম্মা ক্লিনিকাল সারাংশ (BN) ===",
            "patient_title" to "রোগী",
            "patient_birth" to "জন্ম তারিখ",
            "patient_blood" to "রক্তের গ্রুপ",
            "patient_lang" to "भाषा",
            "patient_addr" to "ঠিকানা",
            "patient_phone" to "ফোন",
            "patient_email" to "ইমেল",
            "patient_id" to "আইডি",
            "allergies_title" to "অ্যালার্জি",
            "medications_title" to "ওষুধ",
            "conditions_title" to "শারীরিক অবস্থা",
            "immunizations_title" to "টিকা",
            "procedures_title" to "চিকিৎসা প্রক্রিয়া",
            "devices_title" to "চিকিৎসা যন্ত্র",
            "contacts_title" to "জরুরী যোগাযোগ",
            "footer" to "✅ জেম্মা অন-ডিভাইস · `_j 1.2`",
            "empty" to "(নেই)"
        ),
        Lang.TR to mapOf(
            "header" to "🏥 === JEMMA TIBBİ ÖZET (TR) ===",
            "patient_title" to "HASTA",
            "patient_birth" to "Doğum Tarihi",
            "patient_blood" to "Kan Grubu",
            "patient_lang" to "Dil",
            "patient_addr" to "Adres",
            "patient_phone" to "Tel",
            "patient_email" to "E-posta",
            "patient_id" to "Kimlik",
            "allergies_title" to "ALERJİLER",
            "medications_title" to "İLAÇLAR",
            "conditions_title" to "KRONİK HASTALIKLAR",
            "immunizations_title" to "AŞILAR",
            "procedures_title" to "GİRİŞİMLER",
            "devices_title" to "TIBBİ CİHAZLAR",
            "contacts_title" to "İLETİŞİM",
            "footer" to "✅ Cihaz üzerinde JEMMA · `_j 1.2`",
            "empty" to "(yok)"
        ),
        Lang.PL to mapOf(
            "header" to "🏥 === KARTA CLINICZNA JEMMA (PL) ===",
            "patient_title" to "PACJENT",
            "patient_birth" to "Data urodzenia",
            "patient_blood" to "Grupa krwi",
            "patient_lang" to "Język",
            "patient_addr" to "Adres",
            "patient_phone" to "Tel",
            "patient_email" to "E-mail",
            "patient_id" to "PESEL",
            "allergies_title" to "ALERGIE",
            "medications_title" to "LEKI",
            "conditions_title" to "CHOROBY",
            "immunizations_title" to "SZCZEPIENIA",
            "procedures_title" to "ZABIEGI",
            "devices_title" to "WYROBY MEDYCZNE",
            "contacts_title" to "KONTAKTY",
            "footer" to "✅ JEMMA na urządzeniu · `_j 1.2`",
            "empty" to "(brak)"
        ),
        Lang.UK to mapOf(
            "header" to "🏥 === КЛІНІЧНА СВОДКА JEMMA (UK) ===",
            "patient_title" to "ПАЦІЄНТ",
            "patient_birth" to "Народився(лась)",
            "patient_blood" to "Группа",
            "patient_lang" to "Мова",
            "patient_addr" to "Адреса",
            "patient_phone" to "Тел",
            "patient_email" to "Email",
            "patient_id" to "ID",
            "allergies_title" to "АЛЕРГІЇ",
            "medications_title" to "ЛІКИ",
            "conditions_title" to "ДІАГНОЗИ",
            "immunizations_title" to "ЩЕПЛЕННЯ",
            "procedures_title" to "ПРОЦЕДУРИ ТА ОПЕРАЦІЇ",
            "devices_title" to "МЕДИЧНІ ПРИСТРОЇ",
            "contacts_title" to "КОНТАКТИ",
            "footer" to "✅ JEMMA на пристрої · `_j 1.2`",
            "empty" to "(немає)"
        ),
        Lang.VI to mapOf(
            "header" to "🏥 === TÓM TẮT Y KHOA JEMMA (VI) ===",
            "patient_title" to "BỆNH NHÂN",
            "patient_birth" to "Ngày sinh",
            "patient_blood" to "Nhóm máu",
            "patient_lang" to "Ngôn ngữ",
            "patient_addr" to "Địa chỉ",
            "patient_phone" to "ĐT",
            "patient_email" to "Email",
            "patient_id" to "CCCD",
            "allergies_title" to "DỊ ỨNG",
            "medications_title" to "THUỐC ĐANG DÙNG",
            "conditions_title" to "BỆNH LÝ",
            "immunizations_title" to "TIÊM CHỦNG",
            "procedures_title" to "THỦ THUẬT / PHẪU THUẬT",
            "devices_title" to "THIẾT BỊ Y TẾ",
            "contacts_title" to "LIÊN HỆ KHẨN CẤP",
            "footer" to "✅ JEMMA trên thiết bị · `_j 1.2`",
            "empty" to "(không)"
        ),
        Lang.TH to mapOf(
            "header" to "🏥 === JEMMA บันทึกทางการแพทย์ (TH) ===",
            "patient_title" to "ผู้ป่วย",
            "patient_birth" to "วันเกิด",
            "patient_blood" to "หมู่เลือด",
            "patient_lang" to "ภาษา",
            "patient_addr" to "ที่อยู่",
            "patient_phone" to "โทร",
            "patient_email" to "อีเมล",
            "patient_id" to "เลขประจำตัว",
            "allergies_title" to "อาการแพ้",
            "medications_title" to "ยาที่ใช้",
            "conditions_title" to "โรคประจำตัว",
            "immunizations_title" to "การฉีดวัคซีน",
            "procedures_title" to "หัตถการ",
            "devices_title" to "อุปกรณ์การแพทย์",
            "contacts_title" to "ผู้ติดต่อฉุกเฉิน",
            "footer" to "✅ บันทึก JEMMA บนอุปกรณ์ · `_j 1.2`",
            "empty" to "(ไม่มี)"
        ),
        Lang.ID to mapOf(
            "header" to "🏥 === RINGKASAN KLINIS JEMMA (ID) ===",
            "patient_title" to "PASIEN",
            "patient_birth" to "Tanggal Lahir",
            "patient_blood" to "Gol. Darah",
            "patient_lang" to "Bahasa",
            "patient_addr" to "Alamat",
            "patient_phone" to "Telp",
            "patient_email" to "Email",
            "patient_id" to "NIK",
            "allergies_title" to "ALERGI",
            "medications_title" to "PENGOBATAN",
            "conditions_title" to "KONDISI MEDIS",
            "immunizations_title" to "IMUNISASI",
            "procedures_title" to "PROSEDUR",
            "devices_title" to "ALAT KESEHATAN",
            "contacts_title" to "KONTAK DARURAT",
            "footer" to "✅ JEMMA di dalam perangkat · `_j 1.2`",
            "empty" to "(tidak ada)"
        ),
        Lang.SV to mapOf(
            "header" to "🏥 === JEMMA MEDICINSK SAMMANFATTNING (SV) ===",
            "patient_title" to "PATIENT",
            "patient_birth" to "Födelsedatum",
            "patient_blood" to "Blodgrupp",
            "patient_lang" to "Språk",
            "patient_addr" to "Adress",
            "patient_phone" to "Tel",
            "patient_email" to "E-post",
            "patient_id" to "Personnummer",
            "allergies_title" to "ALLERGIER",
            "medications_title" to "LÄKEMEDEL",
            "conditions_title" to "DIAGNOSER",
            "immunizations_title" to "VACCINATIONER",
            "procedures_title" to "INGREPP",
            "devices_title" to "MEDICINTEKNISKA PRODUKTER",
            "contacts_title" to "KONTAKTER",
            "footer" to "✅ JEMMA på enheten · `_j 1.2`",
            "empty" to "(inga)"
        ),
        Lang.NO to mapOf(
            "header" to "🏥 === JEMMA MEDISINSK SAMMENDRAG (NO) ===",
            "patient_title" to "PASIENT",
            "patient_birth" to "Fødselsdato",
            "patient_blood" to "Blodgruppe",
            "patient_lang" to "Språk",
            "patient_addr" to "Adresse",
            "patient_phone" to "Tlf",
            "patient_email" to "E-post",
            "patient_id" to "Fødselsnummer",
            "allergies_title" to "ALLERGIER",
            "medications_title" to "MEDISINER",
            "conditions_title" to "DIAGNOSER",
            "immunizations_title" to "VAKSINER",
            "procedures_title" to "INNGREP",
            "devices_title" to "MEDISINSK UTSTYR",
            "contacts_title" to "KONTAKTER",
            "footer" to "✅ JEMMA på enheten · `_j 1.2`",
            "empty" to "(ingen)"
        ),
        Lang.DA to mapOf(
            "header" to "🏥 === JEMMA MEDICINSK RESUME (DA) ===",
            "patient_title" to "PATIENT",
            "patient_birth" to "Fødselsdato",
            "patient_blood" to "Blodtype",
            "patient_lang" to "Sprog",
            "patient_addr" to "Adresse",
            "patient_phone" to "Tlf",
            "patient_email" to "E-mail",
            "patient_id" to "CPR-nummer",
            "allergies_title" to "ALLERGIER",
            "medications_title" to "MEDICIN",
            "conditions_title" to "SYGDOMME",
            "immunizations_title" to "VACCINATIONER",
            "procedures_title" to "INDGREB",
            "devices_title" to "MEDICINSK UDSTYR",
            "contacts_title" to "KONTAKTER",
            "footer" to "✅ JEMMA på enheden · `_j 1.2`",
            "empty" to "(ingen)"
        ),
        Lang.FI to mapOf(
            "header" to "🏥 === JEMMA LÄÄKETIETEELLINEN YHTEENVETO (FI) ===",
            "patient_title" to "POTILAS",
            "patient_birth" to "Syntymäaika",
            "patient_blood" to "Veriryhmä",
            "patient_lang" to "Kieli",
            "patient_addr" to "Osoite",
            "patient_phone" to "Puh",
            "patient_email" to "Sähköposti",
            "patient_id" to "Henkilötunnus",
            "allergies_title" to "ALLERGIAT",
            "medications_title" to "LÄÄKITYS",
            "conditions_title" to "DIAGNOOSIT",
            "immunizations_title" to "ROKOTUKSET",
            "procedures_title" to "TOIMENPITEET",
            "devices_title" to "LÄÄKINNÄLLISET LAITTEET",
            "contacts_title" to "YHTEYSTIEDOT",
            "footer" to "✅ JEMMA laitteessa · `_j 1.2`",
            "empty" to "(ei mitään)"
        ),
        Lang.RO to mapOf(
            "header" to "🏥 === FIȘĂ CLINICĂ JEMMA (RO) ===",
            "patient_title" to "PACIENT",
            "patient_birth" to "Data nașterii",
            "patient_blood" to "Grupa sanguină",
            "patient_lang" to "Limba",
            "patient_addr" to "Adresă",
            "patient_phone" to "Tel",
            "patient_email" to "E-mail",
            "patient_id" to "CNP",
            "allergies_title" to "ALERGII",
            "medications_title" to "MEDICAMENTE",
            "conditions_title" to "AFECȚIUNI",
            "immunizations_title" to "VACCINĂRI",
            "procedures_title" to "PROCEDURI",
            "devices_title" to "DISPOZITIVE MEDICALE",
            "contacts_title" to "CONTACTE DE URGENȚĂ",
            "footer" to "✅ JEMMA pe dispozitiv · `_j 1.2`",
            "empty" to "(niciuna)"
        )
    )

    private val PDF_LABELS = mapOf(
        Lang.EN to mapOf(
            "sub_title" to "EMERGENCY MEDICAL CARD",
            "fold_instruction" to "Jemma Pass Offline Booklet — Fold in 4 and wear as a badge",
            "passport_title" to "OFFLINE EMERGENCY PASSPORT",
            "mesh_tagline" to "📡 Near-Range Mesh & Offline Decryption",
            "caption_scan" to "📷 SCAN WITH ANY SMARTPHONE (EN)",
            "caption_pruned" to "📷 CLINICAL SCAN · HIGH DENSITY (PRUNED)",
            "page_title" to "JEMMA PASS — DECRYPT KEYS",
            "card1_title" to "QR 1 : UNIVERSAL EMOJI SUMMARY",
            "card2_title" to "QR 2 : COMPACT FHIR RECORD",
            "page2_footer" to "Offline backup decrypted on-device using local Knowledge Base & Google Gemma AI",
            "born" to "Born",
            "gender" to "Gender",
            "national_id" to "National ID",
            "blood_type" to "Blood Type",
            "gender_m" to "M",
            "gender_f" to "F",
            "gender_o" to "O",
            "gender_u" to "U"
        ),
        Lang.FR to mapOf(
            "sub_title" to "CARTE MÉDICALE D'URGENCE",
            "fold_instruction" to "Livret Jemma Pass Hors-Ligne — Plier en 4 et porter comme badge",
            "passport_title" to "PASSEPORT D'URGENCE HORS-LIGNE",
            "mesh_tagline" to "📡 Réseau Mesh Proche & Décryptage Hors-Ligne",
            "caption_scan" to "📷 SCANNEZ AVEC UN SMARTPHONE (FR)",
            "caption_pruned" to "📷 SCAN CLINIQUE · HAUTE DENSITÉ (PRUNED)",
            "page_title" to "JEMMA PASS — CLÉS DE DÉCRYPTAGE",
            "card1_title" to "QR 1 : RÉSUMÉ UNIVERSEL EN EMOJIS",
            "card2_title" to "QR 2 : DOSSIER COMPACT FHIR",
            "page2_footer" to "Sauvegarde hors-ligne décryptée sur l'appareil à l'aide de la base de connaissances locale & Google Gemma AI",
            "born" to "Né(e)",
            "gender" to "Sexe",
            "national_id" to "N° Identité",
            "blood_type" to "Groupe Sanguin",
            "gender_m" to "H",
            "gender_f" to "F",
            "gender_o" to "O",
            "gender_u" to "U"
        ),
        Lang.JA to mapOf(
            "sub_title" to "緊急医療カード",
            "fold_instruction" to "Jemma Pass オフライン冊子 — 4つに折りたたんでバッジとして着用",
            "passport_title" to "オフライン緊急医療パスポート",
            "mesh_tagline" to "📡 近距離メッシュ & オフライン復号",
            "caption_scan" to "📷 スキャンしてローカルテキストを表示 (JA)",
            "caption_pruned" to "📷 医療用スキャン・高密度圧縮 (PRUNED)",
            "page_title" to "JEMMA PASS — 復号キー",
            "card1_title" to "QR 1 : ユニバーサル絵文字サマリー",
            "card2_title" to "QR 2 : コンパクト FHIR レコード",
            "page2_footer" to "ローカルデータベースと Google Gemma AI を使用してデバイス上で復号されたオフラインバックアップ",
            "born" to "生年月日",
            "gender" to "性別",
            "national_id" to "身分証番号",
            "blood_type" to "血液型",
            "gender_m" to "男",
            "gender_f" to "女",
            "gender_o" to "他",
            "gender_u" to "不明"
        ),
        Lang.ES to mapOf(
            "sub_title" to "TARJETA MÉDICA DE EMERGENCIA",
            "fold_instruction" to "Folleto fuera de línea Jemma Pass — Doblar en 4 y llevar como credencial",
            "passport_title" to "PASAPORTE DE EMERGENCIA FUERA DE LÍNEA",
            "mesh_tagline" to "📡 Red Mesh cercana y descifrado fuera de línea",
            "caption_scan" to "📷 ESCANEE CON CUALQUIER SMARTPHONE (ES)",
            "caption_pruned" to "📷 ESCANEO CLÍNICO · ALTA DENSIDAD (PRUNED)",
            "page_title" to "JEMMA PASS — CLAVES DE DESCIFRADO",
            "card1_title" to "QR 1: RESUMEN DE EMOJIS UNIVERSALES",
            "card2_title" to "QR 2: REGISTRO COMPACTO FHIR",
            "page2_footer" to "Copia de seguridad fuera de línea descifrada en el dispositivo usando la base de conocimientos local y Google Gemma AI",
            "born" to "Nacido(a)",
            "gender" to "Sexo",
            "national_id" to "Identificación",
            "blood_type" to "Grupo Sanguíneo",
            "gender_m" to "M",
            "gender_f" to "F",
            "gender_o" to "O",
            "gender_u" to "U"
        ),
        Lang.DE to mapOf(
            "sub_title" to "NOTFALL-MEDIZINKARTE",
            "fold_instruction" to "Jemma Pass Offline-Broschüre — In 4 falten und als Ausweis tragen",
            "passport_title" to "OFFLINE-NOTFALLPASS",
            "mesh_tagline" to "📡 Nahbereichs-Mesh & Offline-Entschlüsselung",
            "caption_scan" to "📷 MIT JEDEM SMARTPHONE SCANNEN (DE)",
            "caption_pruned" to "📷 KLINISCHER SCAN · HOHE DICHTE (PRUNED)",
            "page_title" to "JEMMA PASS — ENTSCHLÜSSELUNGSSCHLÜSSEL",
            "card1_title" to "QR 1: UNIVERSELLE EMOJI-ZUSAMMENFASSUNG",
            "card2_title" to "QR 2: KOMPAKTE FHIR-AKTE",
            "page2_footer" to "Offline-Backup auf dem Gerät mit lokaler Wissensdatenbank & Google Gemma AI entschlüsselt",
            "born" to "Geboren",
            "gender" to "Geschlecht",
            "national_id" to "Ausweisnummer",
            "blood_type" to "Blutgruppe",
            "gender_m" to "M",
            "gender_f" to "W",
            "gender_o" to "A",
            "gender_u" to "U"
        ),
        Lang.IT to mapOf(
            "sub_title" to "TESSERA MEDICA DI EMERGENZA",
            "fold_instruction" to "Libretto offline Jemma Pass — Piegare in 4 e indossare come badge",
            "passport_title" to "PASSAPORTO DI EMERGENZA OFFLINE",
            "mesh_tagline" to "📡 Rete Mesh vicina e decrittografia offline",
            "caption_scan" to "📷 SCANSIONA CON QUALSIASI SMARTPHONE (IT)",
            "caption_pruned" to "📷 SCANSIONE CLINICA · ALTA DENSITÀ (PRUNED)",
            "page_title" to "JEMMA PASS — CHIAVI DI DECRITTOGRAFIA",
            "card1_title" to "QR 1: RIEPILOGO EMOJI UNIVERSALE",
            "card2_title" to "QR 2: CARTELLA COMPATTA FHIR",
            "page2_footer" to "Backup offline decrittografato sul dispositivo tramite database locale e Google Gemma AI",
            "born" to "Nato/a",
            "gender" to "Sesso",
            "national_id" to "Codice Fiscale",
            "blood_type" to "Gruppo Sanguigno",
            "gender_m" to "M",
            "gender_f" to "F",
            "gender_o" to "O",
            "gender_u" to "U"
        ),
        Lang.PT to mapOf(
            "sub_title" to "CARTÃO MÉDICO DE EMERGÊNCIA",
            "fold_instruction" to "Folheto Offline Jemma Pass — Dobrar em 4 e usar como crachá",
            "passport_title" to "PASSAPORTE DE EMERGÊNCIA OFFLINE",
            "mesh_tagline" to "📡 Rede Mesh próxima e descriptografia offline",
            "caption_scan" to "📷 DIGITALIZE COM QUALQUER SMARTPHONE (PT)",
            "caption_pruned" to "📷 SCAN CLÍNICO · ALTA DENSIDADE (PRUNED)",
            "page_title" to "JEMMA PASS — CHAVES DE DESCRIPTOGRAFIA",
            "card1_title" to "QR 1: RESUMO DE EMOJI UNIVERSAL",
            "card2_title" to "QR 2: REGISTRO COMPACTO FHIR",
            "page2_footer" to "Backup offline descriptografado no dispositivo usando a base de conhecimento local e Google Gemma AI",
            "born" to "Nascimento",
            "gender" to "Sexo",
            "national_id" to "Identidade",
            "blood_type" to "Grupo Sanguíneo",
            "gender_m" to "M",
            "gender_f" to "F",
            "gender_o" to "O",
            "gender_u" to "U"
        ),
        Lang.NL to mapOf(
            "sub_title" to "MEDISCHE NOODKAART",
            "fold_instruction" to "Jemma Pass Offline Boekje — Vouw in 4 en draag als badge",
            "passport_title" to "OFFLINE NOODPASPOORT",
            "mesh_tagline" to "📡 Nabijheids-Mesh & Offline Ontsleuteling",
            "caption_scan" to "📷 SCAN MET ELKE SMARTPHONE (NL)",
            "caption_pruned" to "📷 KLINISCHE SCAN · HOGE DICHTHEID (PRUNED)",
            "page_title" to "JEMMA PASS — ONTSLEUTELINGSSLEUTELS",
            "card1_title" to "QR 1: UNIVERSELE EMOJI SAMENVATTING",
            "card2_title" to "QR 2: COMPACT FHIR DOSSIER",
            "page2_footer" to "Offline back-up on-device ontsleuteld met lokale kennisbank & Google Gemma AI",
            "born" to "Geboren",
            "gender" to "Geslacht",
            "national_id" to "ID nummer",
            "blood_type" to "Bloedgroep",
            "gender_m" to "M",
            "gender_f" to "V",
            "gender_o" to "A",
            "gender_u" to "U"
        ),
        Lang.ZH to mapOf(
            "sub_title" to "紧急医疗卡",
            "fold_instruction" to "Jemma Pass 离线手册 — 折叠4次并佩戴为胸牌",
            "passport_title" to "离线紧急健康护照",
            "mesh_tagline" to "📡 近距离网状网络与离线解密",
            "caption_scan" to "📷 使用任何智能手机扫描 (ZH)",
            "caption_pruned" to "📷 临床扫描 · 高密度压缩 (PRUNED)",
            "page_title" to "JEMMA PASS — 解密密钥",
            "card1_title" to "QR 1：通用表情符号摘要",
            "card2_title" to "QR 2：紧凑型 FHIR 记录",
            "page2_footer" to "使用本地知识库和 Google Gemma AI 在设备上解密的离线备份",
            "born" to "出生日期",
            "gender" to "性别",
            "national_id" to "身份证号",
            "blood_type" to "血型",
            "gender_m" to "男",
            "gender_f" to "女",
            "gender_o" to "他",
            "gender_u" to "未"
        ),
        Lang.KO to mapOf(
            "sub_title" to "응급 의료 카드",
            "fold_instruction" to "Jemma Pass 오프라인 책자 — 4등분으로 접어 배지로 착용",
            "passport_title" to "오프라인 응급 여권",
            "mesh_tagline" to "📡 근거리 메쉬 및 오프라인 암호 해독",
            "caption_scan" to "📷 스마트폰 카메라로 스캔하세요 (KO)",
            "caption_pruned" to "📷 임상 스캔 · 고밀도 압축 (PRUNED)",
            "page_title" to "JEMMA PASS — 암호 해독 키",
            "card1_title" to "QR 1 : 범용 이모지 요약",
            "card2_title" to "QR 2 : 압축 FHIR 기록",
            "page2_footer" to "로컬 지식베이스 및 Google Gemma AI를 사용하여 기기에서 자체 복호화된 오프라인 백업",
            "born" to "생년월일",
            "gender" to "성별",
            "national_id" to "ID 번호",
            "blood_type" to "혈액형",
            "gender_m" to "남",
            "gender_f" to "여",
            "gender_o" to "기타",
            "gender_u" to "미상"
        ),
        Lang.AR to mapOf(
            "sub_title" to "بطاقة الطوارئ الطبية",
            "fold_instruction" to "كتيب جيما أوفلاين — يطوى إلى 4 أجزاء ويرتدى كبطاقة تعريفية",
            "passport_title" to "جواز طوارئ طبي أوفلاين",
            "mesh_tagline" to "📡 شبكة ميش محلية وتشفير أوفلاين",
            "caption_scan" to "📷 امسح بأي هاتف ذكي (AR)",
            "caption_pruned" to "📷 مسح طبي · كثافة عالية (PRUNED)",
            "page_title" to "مفاتيح فك التشفير — JEMMA PASS",
            "card1_title" to "ملخص إيموجي طبي موحد : QR 1",
            "card2_title" to "سجل طبي مضغوط FHIR : QR 2",
            "page2_footer" to "نسخة احتياضية مشفرة محلياً على الجهاز بواسطة قاعدة المعرفة المحلية وذكاء جيما الاصطناعي",
            "born" to "تاريخ الميلاد",
            "gender" to "الجنس",
            "national_id" to "الهوية الوطنية",
            "blood_type" to "فصيلة الدم",
            "gender_m" to "ذكر",
            "gender_f" to "أنثى",
            "gender_o" to "آخر",
            "gender_u" to "غير محدد"
        ),
        Lang.RU to mapOf(
            "sub_title" to "АВАРИЙНАЯ МЕДИЦИНСКАЯ КАРТА",
            "fold_instruction" to "Офлайн-буклет Jemma Pass — Сложить в 4 раза и носить как бейдж",
            "passport_title" to "ОФЛАЙН-ПАСПОРТ ЗДОРОВЬЯ",
            "mesh_tagline" to "📡 Локальная Mesh-сеть и офлайн-дешифрование",
            "caption_scan" to "📷 ОТСКАНИРУЙТЕ СМАРТФОНОМ (RU)",
            "caption_pruned" to "📷 КЛИНИЧЕСКИЙ СКАН · ВЫСОКАЯ ПЛОТНОСТЬ (PRUNED)",
            "page_title" to "JEMMA PASS — КЛЮЧИ ДЕШИФРОВАНИЯ",
            "card1_title" to "QR 1: УНИВЕРСАЛЬНАЯ СВОДКА В ЭМОДЗИ",
            "card2_title" to "QR 2: КОМПАКТНАЯ FHIR-КАРТА",
            "page2_footer" to "Офлайн-копия, расшифрованная на устройстве с помощью локальной базы знаний и Google Gemma AI",
            "born" to "Родился(ась)",
            "gender" to "Пол",
            "national_id" to "Удостоверение",
            "blood_type" to "Группа крови",
            "gender_m" to "М",
            "gender_f" to "Ж",
            "gender_o" to "Д",
            "gender_u" to "Н"
        ),
        Lang.HI to mapOf(
            "sub_title" to "आपातकालीन मेडिकल कार्ड",
            "fold_instruction" to "जेम्मा पास ऑफ़लाइन बुकलेट — 4 भागों में मोड़ें और बैज की तरह पहनें",
            "passport_title" to "ऑफ़लाइन आपातकालीन पासपोर्ट",
            "mesh_tagline" to "📡 स्थानीय मेश नेटवर्क और ऑफ़लाइन डिक्रिप्शन",
            "caption_scan" to "📷 किसी भी स्मार्टफोन से स्कैन करें (HI)",
            "caption_pruned" to "📷 क्लिनिकल स्कैन · उच्च घनत्व (PRUNED)",
            "page_title" to "जेम्मा पास — डिक्रिप्शन कुंजी",
            "card1_title" to "क्यूआर 1: सार्वभौमिक इमोजी सारांश",
            "card2_title" to "क्यूआर 2: कॉम्पैक्ट FHIR रिकॉर्ड",
            "page2_footer" to "लोकल डेटाबेस और गूगल जेम्मा एआई द्वारा डिवाइस पर डिक्रिप्ट किया गया बैकअप",
            "born" to "जन्म",
            "gender" to "लिंग",
            "national_id" to "आईडी कार्ड",
            "blood_type" to "रक्त समूह",
            "gender_m" to "पु",
            "gender_f" to "म",
            "gender_o" to "अ",
            "gender_u" to "अज्ञा"
        ),
        Lang.BN to mapOf(
            "sub_title" to "জরুরী মেডিকেল कार्ड",
            "fold_instruction" to "জেম্মা পাস অফলাইন বুকলেট — ৪ ভাঁজ করে ব্যাজ হিসেবে ব্যবহার করুন",
            "passport_title" to "অফলাইন জরুরী পাসপোর্ট",
            "mesh_tagline" to "📡 স্থানীয় মেশ নেটওয়ার্ক এবং অফলাইন ডিক্রিপশন",
            "caption_scan" to "📷 যেকোনো স্মার্টফোন দিয়ে স্ক্যান করুন (BN)",
            "caption_pruned" to "📷 ক্লিনিকাল স্ক্যান · উচ্চ ঘনত্ব (PRUNED)",
            "page_title" to "জেম্মা পাস — ডিক্রিপশন কী",
            "card1_title" to "QR ১: সার্বজনীন ইমোজি সারাংশ",
            "card2_title" to "QR ২: কম্প্যাক্ট FHIR রেকর্ড",
            "page2_footer" to "ডিভাইসে ডিক্রিপ্ট করা অফলাইন बैकअप (লোকাল ডেটাবেস এবং গুগল জেম্মা এআই)",
            "born" to "जन्म",
            "gender" to "লিঙ্গ",
            "national_id" to "জাতীয় আইডি",
            "blood_type" to "রক্তের গ্রুপ",
            "gender_m" to "পুরুষ",
            "gender_f" to "নারী",
            "gender_o" to "অন্যান্য",
            "gender_u" to "অজানা"
        ),
        Lang.TR to mapOf(
            "sub_title" to "ACİL TIBBİ KART",
            "fold_instruction" to "Jemma Pass Çevrimdışı Kitapçık — 4'e katlayın ve rozet olarak takın",
            "passport_title" to "ÇEVRİMDIŞI ACİL DURUM PASAPORTU",
            "mesh_tagline" to "📡 Yakın Mesafe Mesh & Çevrimdışı Şifre Çözme",
            "caption_scan" to "📷 HERHANGİ BİR AKILLI TELEFONLA TARA (TR)",
            "caption_pruned" to "📷 TIBBİ TARAMA · YÜKSEK YOĞUNLUK (PRUNED)",
            "page_title" to "JEMMA PASS — ŞİFRE ÇÖZME ANAHTARLARI",
            "card1_title" to "QR 1: EVRENSEL EMOJİ ÖZETİ",
            "card2_title" to "QR 2: KOMPAKT FHIR KAYDI",
            "page2_footer" to "Cihaz üzerinde yerel veritabanı & Google Gemma AI ile şifresi çözülmüş çevrimdışı yedek",
            "born" to "Doğum",
            "gender" to "Cinsiyet",
            "national_id" to "T.C. Kimlik",
            "blood_type" to "Kan Grubu",
            "gender_m" to "E",
            "gender_f" to "K",
            "gender_o" to "D",
            "gender_u" to "B"
        ),
        Lang.PL to mapOf(
            "sub_title" to "KARTA MEDYCZNA",
            "fold_instruction" to "Ulotka Jemma Pass Offline — Złóż na 4 i noś jako identyfikator",
            "passport_title" to "OFFLINE PASZPORT MEDYCZNY",
            "mesh_tagline" to "📡 Sieć Mesh bliskiego zasięgu i deszyfrowanie offline",
            "caption_scan" to "📷 ZESKANUJ DOWOLNYM SMARTFONEM (PL)",
            "caption_pruned" to "📷 SKAN MEDYCZNY · WYSOKA GĘSTOŚĆ (PRUNED)",
            "page_title" to "JEMMA PASS — KLUCZE DESZYFRUJĄCE",
            "card1_title" to "QR 1: UNIWERSALNE PODSUMOWANIE EMOJI",
            "card2_title" to "QR 2: SKOMPRESOWANY REKORD FHIR",
            "page2_footer" to "Kopia offline odszyfrowana lokalnie przez bazę wiedzy i Google Gemma AI",
            "born" to "Urodzony",
            "gender" to "Płeć",
            "national_id" to "PESEL",
            "blood_type" to "Grupa krwi",
            "gender_m" to "M",
            "gender_f" to "K",
            "gender_o" to "I",
            "gender_u" to "N"
        ),
        Lang.UK to mapOf(
            "sub_title" to "МЕДИЧНА КАРТКА",
            "fold_instruction" to "Офлайн-буклет Jemma Pass — Скласти в 4 рази та носити як бейдж",
            "passport_title" to "ОФЛАЙН-ПАСПОРТ ЗДОРОВ'Я",
            "mesh_tagline" to "📡 Локальна Mesh-мережа та офлайн-дешифрування",
            "caption_scan" to "📷 ВІДСКАНУЙТЕ СМАРТФОНОМ (UK)",
            "caption_pruned" to "📷 КЛІНІЧНИЙ СКАН · ВИСОКА ЩІЛЬНІСТЬ (PRUNED)",
            "page_title" to "JEMMA PASS — КЛЮЧІ ДЕШИФРУВАННЯ",
            "card1_title" to "QR 1: УНІВЕРСАЛЬНА СВОДКА В ЕМОДЗІ",
            "card2_title" to "QR 2: КОМПАКТНА КАРТА FHIR",
            "page2_footer" to "Офлайн-копія, розшифрована на пристрої локальною базою знань та Google Gemma AI",
            "born" to "Народився(лась)",
            "gender" to "Стать",
            "national_id" to "Посвідчення",
            "blood_type" to "Група крові",
            "gender_m" to "Ч",
            "gender_f" to "Ж",
            "gender_o" to "І",
            "gender_u" to "Н"
        ),
        Lang.VI to mapOf(
            "sub_title" to "THẺ Y TẾ KHẨN CẤP",
            "fold_instruction" to "Sổ tay Ngoại tuyến Jemma Pass — Gấp làm 4 và đeo như thẻ",
            "passport_title" to "HỘ CHIẾU Y TẾ NGOẠI TUYẾN",
            "mesh_tagline" to "📡 Mạng Mesh nội bộ & Giải mã ngoại tuyến",
            "caption_scan" to "📷 QUÉT BẰNG ĐIỆN THOẠI BẤT KỲ (VI)",
            "caption_pruned" to "📷 BẢN QUÉT Y KHOA · NÉN CAO (PRUNED)",
            "page_title" to "JEMMA PASS — KHÓA GIẢI MÃ",
            "card1_title" to "QR 1: TÓM TẮT BẰNG BIỂU TƯỢNG (EMOJI)",
            "card2_title" to "QR 2: HỒ SƠ FHIR TIÊU CHUẨN",
            "page2_footer" to "Bản sao ngoại tuyến được giải mã trên thiết bị bằng CSDL cục bộ và Google Gemma AI",
            "born" to "Ngày sinh",
            "gender" to "Giới tính",
            "national_id" to "Số định danh",
            "blood_type" to "Nhóm máu",
            "gender_m" to "Nam",
            "gender_f" to "Nữ",
            "gender_o" to "Khác",
            "gender_u" to "Chưa rõ"
        ),
        Lang.TH to mapOf(
            "sub_title" to "บัตรการแพทย์ฉุกเฉิน",
            "fold_instruction" to "คู่มือ Jemma Pass ออฟไลน์ — พับ 4 ส่วนและใส่เป็นป้ายชื่อ",
            "passport_title" to "พาสปอร์ตการแพทย์ออฟไลน์",
            "mesh_tagline" to "📡 เครือข่าย Mesh ระยะใกล้ & การถอดรหัสออฟไลน์",
            "caption_scan" to "📷 สแกนด้วยสมาร์ทโฟนใดก็ได้ (TH)",
            "caption_pruned" to "📷 สแกนการแพทย์ · ความหนาแน่นสูง (PRUNED)",
            "page_title" to "JEMMA PASS — รหัสถอดรหัส",
            "card1_title" to "QR 1: สรุปด้วยอีโมจิสากล",
            "card2_title" to "QR 2: บันทึก FHIR ขนาดเล็ก",
            "page2_footer" to "การสำรองข้อมูลออฟไลน์ที่ถอดรหัสบนอุปกรณ์ด้วย CSDL ท้องถิ่นและ Google Gemma AI",
            "born" to "เกิด",
            "gender" to "เพศ",
            "national_id" to "บัตรประชาชน",
            "blood_type" to "หมู่เลือด",
            "gender_m" to "ชาย",
            "gender_f" to "หญิง",
            "gender_o" to "อื่นๆ",
            "gender_u" to "ไม่ระบุ"
        ),
        Lang.ID to mapOf(
            "sub_title" to "KARTU MEDIS DARURAT",
            "fold_instruction" to "Buku Panduan Offline Jemma Pass — Lipat 4 dan gunakan sebagai lencana",
            "passport_title" to "PASPOR DARURAT OFFLINE",
            "mesh_tagline" to "📡 Jaringan Mesh Lokal & Dekripsi Offline",
            "caption_scan" to "📷 PINDAI DENGAN SMARTPHONE APA PUN (ID)",
            "caption_pruned" to "📷 PINDAIAN KLINIS · KEPADATAN TINGGI (PRUNED)",
            "page_title" to "JEMMA PASS — KUNCI DEKRIPSI",
            "card1_title" to "QR 1: RINGKASAN EMOJI UNIVERSAL",
            "card2_title" to "QR 2: REKAM MEDIS COMPACT FHIR",
            "page2_footer" to "Cadangan offline didekripsi langsung di perangkat dengan Database lokal & Google Gemma AI",
            "born" to "Lahir",
            "gender" to "Jenis Kelamin",
            "national_id" to "Nomor Identitas",
            "blood_type" to "Golongan Darah",
            "gender_m" to "L",
            "gender_f" to "P",
            "gender_o" to "O",
            "gender_u" to "U"
        ),
        Lang.SV to mapOf(
            "sub_title" to "MEDICINSKT NÖDKORT",
            "fold_instruction" to "Jemma Pass Offline-broschyr — Vik i 4 och bär som bricka",
            "passport_title" to "OFFLINE NÖDPASSPORT",
            "mesh_tagline" to "📡 Närfälts-Mesh & Offline-dekryptering",
            "caption_scan" to "📷 SCANNA MED VALFRI SMARTPHONE (SV)",
            "caption_pruned" to "📷 KLINISK SCAN · HÖG DENSITET (PRUNED)",
            "page_title" to "JEMMA PASS — DEKRYPTERINGSNYCKLAR",
            "card1_title" to "QR 1: UNIVERSELL EMOJI-SAMMANFATTNING",
            "card2_title" to "QR 2: KOMPAKT FHIR-JOURNAL",
            "page2_footer" to "Offline-backup dekrypterad på enheten med lokal kunskapsbas & Google Gemma AI",
            "born" to "Geboren",
            "gender" to "Kön",
            "national_id" to "Personnummer",
            "blood_type" to "Blodgrupp",
            "gender_m" to "M",
            "gender_f" to "K",
            "gender_o" to "A",
            "gender_u" to "O"
        ),
        Lang.NO to mapOf(
            "sub_title" to "MEDISINSK NØDKORT",
            "fold_instruction" to "Jemma Pass Offline-brosjyre — Brett i 4 og bær som merke",
            "passport_title" to "OFFLINE NØDPASS",
            "mesh_tagline" to "📡 Nærområde-Mesh & Offline-dekryptering",
            "caption_scan" to "📷 SKANN MED ENHVER SMARTTELEFON (NO)",
            "caption_pruned" to "📷 KLINISK SKANN · HØY TETTHET (PRUNED)",
            "page_title" to "JEMMA PASS — DEKRYPTERINGSNØKLER",
            "card1_title" to "QR 1: UNIVERSELL EMOSJI-SAMMENDRAG",
            "card2_title" to "QR 2: KOMPAKT FHIR-JOURNAL",
            "page2_footer" to "Offline-sikkerhetskopi dekryptert på enheten med lokal kunnskapsbase & Google Gemma AI",
            "born" to "Fødselsdato",
            "gender" to "Kjønn",
            "national_id" to "Fødselsnummer",
            "blood_type" to "Blodgruppe",
            "gender_m" to "M",
            "gender_f" to "K",
            "gender_o" to "A",
            "gender_u" to "U"
        ),
        Lang.DA to mapOf(
            "sub_title" to "MEDICINSK NØDKORT",
            "fold_instruction" to "Jemma Pass Offline-brochure — Fold i 4 og bær som badge",
            "passport_title" to "OFFLINE NØDPAS",
            "mesh_tagline" to "📡 Nærområde-Mesh & Offline-dekryptering",
            "caption_scan" to "📷 SCAN MED ENHVER SMARTPHONE (DA)",
            "caption_pruned" to "📷 KLINISK SCAN · HØJ TÆTHED (PRUNED)",
            "page_title" to "JEMMA PASS — DEKRYPTERINGSNØGLER",
            "card1_title" to "QR 1: UNIVERSELT EMOJI-RESUME",
            "card2_title" to "QR 2: KOMPAKT FHIR-JOURNAL",
            "page2_footer" to "Offline-backup dekrypteret på enheden med lokal vidensbase & Google Gemma AI",
            "born" to "Fødselsdato",
            "gender" to "Køn",
            "national_id" to "CPR-nummer",
            "blood_type" to "Blodtype",
            "gender_m" to "M",
            "gender_f" to "K",
            "gender_o" to "A",
            "gender_u" to "U"
        ),
        Lang.FI to mapOf(
            "sub_title" to "LÄÄKETIETEELLINEN HÄTÄKORTTI",
            "fold_instruction" to "Jemma Pass offline-esite — Taita neljään ja kanna merkkinä",
            "passport_title" to "OFFLINE-HÄTÄPASSI",
            "mesh_tagline" to "📡 Lähialueverkon Mesh & offline-salauksen purku",
            "caption_scan" to "📷 SKANNAA MILLÄ TAHANSA ÄLYPUHELIMELLA (FI)",
            "caption_pruned" to "📷 KLIININEN SKANNAUS · TIHEÄ KOODI (PRUNED)",
            "page_title" to "JEMMA PASS — SALAUKSENPURKUAVAIMET",
            "card1_title" to "QR 1: YLEISMAAILMALLINEN EMOJI-YHTEENVETO",
            "card2_title" to "QR 2: TIIVISTETTY FHIR-KORTTI",
            "page2_footer" to "Offline-varmuuskopio purettu laitteella käyttäen paikallista tietokantaa & Google Gemma AI",
            "born" to "Syntynyt",
            "gender" to "Sukupuoli",
            "national_id" to "Henkilötunnus",
            "blood_type" to "Veriryhmä",
            "gender_m" to "M",
            "gender_f" to "N",
            "gender_o" to "M",
            "gender_u" to "E"
        ),
        Lang.RO to mapOf(
            "sub_title" to "CARD MEDICAL DE URGENȚĂ",
            "fold_instruction" to "Broșură Jemma Pass Offline — Împăturește în 4 și poartă ca ecuson",
            "passport_title" to "PAȘAPORT MEDICAL OFFLINE",
            "mesh_tagline" to "📡 Rețea locală Mesh & Decriptare Offline",
            "caption_scan" to "📷 SCANEAZĂ CU ORICE SMARTPHONE (RO)",
            "caption_pruned" to "📷 SCANARE CLINICĂ · DENSITATE MARE (PRUNED)",
            "page_title" to "JEMMA PASS — CHEI DE DECRIPTARE",
            "card1_title" to "QR 1: REZUMAT EMOJI UNIVERSAL",
            "card2_title" to "QR 2: FIȘĂ COMPACTĂ FHIR",
            "page2_footer" to "Copie offline decriptată pe dispozitiv folosind baza de date locală & Google Gemma AI",
            "born" to "Născut",
            "gender" to "Sex",
            "national_id" to "CNP",
            "blood_type" to "Grupa Sanguină",
            "gender_m" to "M",
            "gender_f" to "F",
            "gender_o" to "O",
            "gender_u" to "N"
        )
    )

    fun getLabel(lang: Lang, key: String): String {
        return CLINICAL_SUMMARIES[lang]?.get(key)
            ?: CLINICAL_SUMMARIES[Lang.EN]?.get(key)
            ?: ""
    }

    fun getPdfLabel(lang: Lang, key: String): String {
        return PDF_LABELS[lang]?.get(key)
            ?: PDF_LABELS[Lang.EN]?.get(key)
            ?: ""
    }

    fun getDescLines1(lang: Lang): List<String> {
        return when (lang) {
            Lang.JA -> listOf(
                "このQRコードには、ユニバーサルな理解を目的として、",
                "アレルギー、服薬、疾患などの情報が医療用絵文字に",
                "翻訳されて格納されています。",
                "",
                "👉 iOS/Android等のあらゆるスマートフォンのカメラで",
                "スキャンするだけで、緊急医療情報が瞬時に表示されます。"
            )
            Lang.FR -> listOf(
                "Ce code QR contient votre profil patient (allergies,",
                "médicaments, antécédents) traduit sous forme d'émojis",
                "médicaux structurés pour une compréhension universelle.",
                "",
                "👉 Scannez avec l'appareil photo de N'IMPORTE QUEL",
                "smartphone pour afficher instantanément la fiche d'urgence."
            )
            Lang.ES -> listOf(
                "Este código QR contiene su perfil de paciente, alergias,",
                "medicamentos y condiciones traducidos a emojis médicos",
                "estructurados para una compreensão universal instantánea.",
                "",
                "👉 Escanee con CUALQUIER cámara de teléfono inteligente",
                "(iOS / Android) para ver el registro de salud de emergencia al instante."
            )
            Lang.DE -> listOf(
                "Dieser QR-Code enthält Ihr Patientenprofil, Allergien,",
                "Medikamente und Befunde, übersetzt in strukturierte",
                "medizinische Emojis für ein sofortiges universelles Verständnis.",
                "",
                "👉 Scannen Sie mit JEDER Smartphone-Kamera (iOS / Android),",
                "um die Notfallakte sofort anzuzeigen."
            )
            Lang.IT -> listOf(
                "Questo codice QR contiene il tuo profilo paziente, allergie,",
                "farmaci e condizioni tradotti in emoji medici strutturati",
                "per una comprensione universale istantanea.",
                "",
                "👉 Scansiona con QUALSIASI fotocamera di smartphone",
                "(iOS / Android) per visualizzare istantaneamente la cartella clinica."
            )
            Lang.PT -> listOf(
                "Este código QR contém o seu perfil do paciente, alergias,",
                "medicamentos e condições traduzidos em emojis médicos",
                "estruturados para compreensão universal imediata.",
                "",
                "👉 Digitalize com QUALQUER câmera de smartphone",
                "(iOS / Android) para ver o registro de saúde de emergência instantaneamente."
            )
            Lang.NL -> listOf(
                "Deze QR-code bevat uw patiëntenprofiel, allergieën,",
                "medicatie en aandoeningen vertaald in gestructureerde",
                "medische emoji's voor direct universeel begrip.",
                "",
                "👉 Scan met ELKE smartphonecamera (iOS / Android)",
                "om het medisch nooddossier direct te bekijken."
            )
            Lang.ZH -> listOf(
                "此 QR 码包含您的患者个人资料、过敏史、药物 and 病况，",
                "并已翻译为结构化的医疗表情符号，以便即时通用理解。",
                "",
                "👉 使用任何智能手机相机（iOS / Android）扫描，",
                "即可立即查看紧急健康记录。"
            )
            Lang.KO -> listOf(
                "이 QR 코드에는 즉각적인 보편적 이해를 위해 구조화된",
                "의료용 이모지로 번역된 귀하의 환자 프로필, 알레르기,",
                "복용 약물 및 질환 정보가 포함되어 있습니다.",
                "",
                "👉 모든 스마트폰 카메라(iOS / Android)로 스캔하여",
                "응급 건강 기록을 즉시 확인하십시오."
            )
            Lang.RU -> listOf(
                "Этот QR-код содержит ваш профиль пациента, аллергии,",
                "лекарства и заболевания, переведенные в структурированные",
                "медицинские эмодзи для мгновенного всеобщего понимания.",
                "",
                "👉 Отсканируйте камерой ЛЮБОГО смартфона (iOS / Android),",
                "чтобы мгновенно просмотреть карту здоровья."
            )
            Lang.AR -> listOf(
                "يحتوي رمز QR هذا على ملف المريض الخاص بك، والحساسية،",
                "والأدوية، والحالات الطبية المترجمة إلى إيموجي طبي موحد",
                "لفهم فوري وشامل.",
                "",
                "👉 امسح بكاميرا أي هاتف ذكي (iOS / Android)",
                "لعرض السجل الطبي للطوارئ فوراً."
            )
            else -> listOf(
                "This QR code contains your patient profile, allergies,",
                "medications, and conditions translated into structured",
                "medical emojis for instant universal comprehension.",
                "",
                "👉 Scan with ANY smartphone camera (iOS / Android)",
                "to view the emergency health record instantly."
            )
        }
    }

    fun getDescLines2(lang: Lang): List<String> {
        return when (lang) {
            Lang.JA -> listOf(
                "このQRコードには、完全に準拠した高密度の",
                "FHIR Patient Bundle（アレルギー、服薬、疾患）が",
                "高度なオンデバイスバイナリ圧縮で格納されています。",
                "",
                "👉 JEMMA対応の臨床アプリ、救助隊員の端末、",
                "および統合されたメッシュ救助ネットワーク向けに設計されています。"
            )
            Lang.FR -> listOf(
                "Ce code QR encode votre dossier complet FHIR Patient Bundle",
                "(allergies, médicaments, antécédents) conforme et à",
                "haute densité, via une compression binaire avancée.",
                "",
                "👉 Conçu pour les applications cliniques Jemma, les",
                "secouristes du réseau mesh et les services intégrés."
            )
            Lang.ES -> listOf(
                "Este código QR codifica su paquete de paciente FHIR completo,",
                "compatible y de alta densidad (alergias, medicamentos, condiciones)",
                "utilizando una compresión binaria avanzada en el dispositivo.",
                "",
                "👉 Diseñado para aplicaciones clínicas habilitadas para Jemma,",
                "personal de rescate de malla y redes de malla integradas."
            )
            Lang.DE -> listOf(
                "Dieser QR-Code kodiert Ihre vollständig konforme, hochdichte",
                "FHIR-Patientenakte (Allergien, Medikamente, Befunde) unter",
                "Verwendung fortschrittlicher binärer Komprimierung auf dem Gerät.",
                "",
                "👉 Entwickelt für Jemma-fähige klinische Apps,",
                "Rettungskräfte und integrierte Mesh-Netzwerke."
            )
            Lang.IT -> listOf(
                "Questo codice QR codifica il tuo fascicolo sanitario FHIR conforme",
                "e ad alta densità (allergie, farmaci, condizioni) utilizzando",
                "una compressione binaria avanzata sul dispositivo.",
                "",
                "👉 Progettato per app cliniche compatibili con Jemma,",
                "soccorritori e reti mesh integrate."
            )
            Lang.PT -> listOf(
                "Este código QR codifica o seu FHIR Patient Bundle completo,",
                "compatível e de alta qualidade (alergias, medicamentos, condições)",
                "usando compressão binária avançada no dispositivo.",
                "",
                "👉 Projetado para aplicativos clínicos compatíveis com Jemma,",
                "socorristas de malha e redes de malha integradas."
            )
            Lang.NL -> listOf(
                "Deze QR-code codeert uw volledig compatibele, high-density",
                "FHIR Patient Bundle (allergieën, medicatie, aandoeningen)",
                "met behulp van geavanceerde binaire compressie op het apparaat.",
                "",
                "👉 Ontworpen voor klinische Jemma-apps,",
                "reddingswerkers en geïntegreerde mesh-netwerken."
            )
            Lang.ZH -> listOf(
                "此 QR 码使用先进的设备端二进制压缩技术，对完全符合标准的",
                "紧凑型 FHIR 患者数据包（过敏史、药物、病况）进行编码。",
                "",
                "👉 专为支持 Jemma 的临床应用、网状网络救援人员和集成网状救援网络而设计。"
            )
            Lang.KO -> listOf(
                "이 QR 코드는 고성능 기기 자체 이진 압축을 사용하여 완전 준수",
                "고밀도 FHIR Patient Bundle(알레르기, 복용 약물, 질환)을 인코딩합니다.",
                "",
                "👉 Jemma 지원 임상 앱, 메쉬 구조 대원 및 통합 메쉬 구조 네트워크를 위해 설계되었습니다."
            )
            Lang.RU -> listOf(
                "Этот QR-код кодирует ваш полностью совместимый высокоплотный",
                "пакет пациентов FHIR (аллергии, лекарства, заболевания) с",
                "использованием передового бинарного сжатия на устройстве.",
                "",
                "👉 Разработано для медицинских приложений Jemma,",
                "спасателей и локальных Mesh-сетей."
            )
            Lang.AR -> listOf(
                "يهدد رمز QR هذا حزمة مريض FHIR المتوافقة تماماً وعالية الكثافة",
                "(الحساسية، الأدوية، الحالات الطبية) باستخدام ضغط ثنائي متقدم على الجهاز.",
                "",
                "👉 مصمم للتطبيقات الطبية المتوافقة مع جيما، ورجال الإنقاذ في شبكة ميش."
            )
            else -> listOf(
                "This QR code encodes your fully-compliant, high-density",
                "FHIR Patient Bundle (Allergies, Medications, Conditions)",
                "using advanced on-device binary compression.",
                "",
                "👉 Designed for Jemma-enabled clinical apps,",
                "mesh rescue responders, and integrated mesh networks."
            )
        }
    }

    fun getCoverDescLines(lang: Lang): List<String> {
        return when (lang) {
            Lang.JA -> listOf(
                "この冊子には、Google Gemma AI モデルを使用して",
                "デバイス上でデジタル化および最適化された",
                "重要な医療プロファイルが含まれています。",
                "",
                "救助者はインターネットや携帯電話の接続なしで、",
                "ローカルメッシュネットワークを介して QR コードを",
                "即座にスキャンし、健康状態を読み取ることができます。"
            )
            Lang.FR -> listOf(
                "Ce livret contient vos profils médicaux vitaux",
                "numérisés et optimisés sur l'appareil à l'aide",
                "des modèles d'IA Google Gemma.",
                "",
                "Les secouristes peuvent scanner instantanément",
                "vos QR codes et lire vos dossiers via le réseau local mesh",
                "SANS aucune connexion Internet ou cellulaire."
            )
            Lang.ES -> listOf(
                "Este folleto contiene sus perfiles médicos vitales digitalizados",
                "y optimizados en el dispositivo utilizando los modelos Google Gemma AI.",
                "",
                "El personal de rescate puede escanear instantáneamente sus códigos QR",
                "y leer sus registros a través de la red de malla local SIN Internet ni celular."
            )
            Lang.DE -> listOf(
                "Diese Broschüre enthält Ihre lebenswichtigen medizinischen Profile,",
                "die auf dem Gerät mithilfe von Google Gemma AI-Modellen digitalisiert wurden.",
                "",
                "Rettungskräfte können Ihre QR-Codes sofort scannen und Ihre Krankenakten",
                "über das lokale Mesh-Netzwerk OHNE Internet- oder Mobilfunkverbindung lesen."
            )
            Lang.IT -> listOf(
                "Questo libretto contiene i tuoi profili medici vitali digitalizzati",
                "e ottimizzati sul dispositivo utilizzando i modelli Google Gemma AI.",
                "",
                "I soccorritori possono scansionare istantaneamente i tuoi codici QR",
                "e leggere le cartelle cliniche via rete mesh locale SENZA Internet o cellulare."
            )
            Lang.PT -> listOf(
                "Este folheto contém seus perfis médicos vitais digitalizados e",
                "otimizados no dispositivo usando os modelos Google Gemma AI.",
                "",
                "Os socorristas podem digitalizar instantaneamente seus códigos QR e",
                "ler seus registros via rede mesh local SEM conexão de Internet ou celular."
            )
            Lang.NL -> listOf(
                "Dit boekje bevat uw vitale medische profielen gedigitaliseerd",
                "en geoptimaliseerd op het apparaat met behulp van Google Gemma AI-modellen.",
                "",
                "Reddingswerkers kunnen direct uw QR-codes scannen en uw dossiers",
                "lezen via het lokale mesh-netwerk ZONDER internet- of mobiele verbinding."
            )
            Lang.ZH -> listOf(
                "本手册包含使用 Google Gemma AI 模型在设备端数字化和优化的重要医疗档案。",
                "",
                "救援人员可以通过本地网状网络立即扫描您的 QR 码并读取您的健康记录，",
                "无需任何互联网或蜂窝网络连接。"
            )
            Lang.KO -> listOf(
                "이 책자에는 Google Gemma AI 모델을 사용하여 기기 자체에서 디지털화 및",
                "최적화된 귀하의 필수 의료 프로필이 포함되어 있습니다.",
                "",
                "구조 대원은 인터넷이나 셀룰러 연결 없이도 로컬 메쉬 네트워크를 통해",
                "귀하의 QR 코드를 즉시 스캔하고 건강 기록을 읽을 수 있습니다."
            )
            Lang.RU -> listOf(
                "Этот буклет содержит ваши жизненно важные медицинские профили,",
                "оцифрованные и оптимизированные на устройстве с помощью моделей Google Gemma AI.",
                "",
                "Спасатели могут мгновенно отсканировать ваши QR-коды и прочитать ваши карты",
                "через локальную Mesh-сеть БЕЗ какого-либо интернет- или мобильного соединения."
            )
            Lang.AR -> listOf(
                "يحتوي هذا الكتيب على ملفاتك الطبية الحيوية التي تم رقمنتها",
                "وتحسينها на الجهاز باستخدام نماذج ذكاء جيما الاصطناعي من جوجل.",
                "",
                "يمكن لرجال الإنقاذ مسح رموز QR الخاصة بك على الفور وقراءة سجلاتك الصحية",
                "عبر شبكة ميش المحلية دون أي اتصال بالإنترنت أو شبكة الهاتف المحمول."
            )
            else -> listOf(
                "This booklet contains your vital medical profiles",
                "digitized and optimized on-device using",
                "Google Gemma AI models.",
                "",
                "Rescuers can instantly scan your QR codes and",
                "read your health records via the local mesh network",
                "WITHOUT any internet or cellular connection."
            )
        }
    }

    fun genderToHuman(gender: String?, langTag: String): String? {
        val clean = gender?.uppercase() ?: return null
        val lang = try {
            Lang.values().firstOrNull { it.isoCode.lowercase() == langTag.lowercase() } ?: Lang.EN
        } catch (e: Exception) {
            Lang.EN
        }
        return when (clean) {
            "M" -> when (lang) {
                Lang.FR -> "homme"
                Lang.JA -> "男性"
                Lang.ES -> "hombre"
                Lang.DE -> "Mann"
                Lang.IT -> "uomo"
                Lang.PT -> "homem"
                Lang.NL -> "man"
                Lang.ZH -> "男"
                Lang.KO -> "남성"
                Lang.RU -> "мужчина"
                Lang.AR -> "رجل"
                Lang.HI -> "पुरुष"
                Lang.BN -> "পুরুষ"
                Lang.TR -> "erkek"
                Lang.PL -> "mężczyzna"
                Lang.UK -> "чоловік"
                Lang.VI -> "nam"
                Lang.TH -> "ชาย"
                Lang.ID -> "laki-laki"
                Lang.SV -> "man"
                Lang.NO -> "mann"
                Lang.DA -> "mand"
                Lang.FI -> "mies"
                Lang.RO -> "bărbat"
                else -> "man"
            }
            "F" -> when (lang) {
                Lang.FR -> "femme"
                Lang.JA -> "女性"
                Lang.ES -> "mujer"
                Lang.DE -> "Frau"
                Lang.IT -> "donna"
                Lang.PT -> "mulher"
                Lang.NL -> "vrouw"
                Lang.ZH -> "女"
                Lang.KO -> "여성"
                Lang.RU -> "женщина"
                Lang.AR -> "امرأة"
                Lang.HI -> "महिला"
                Lang.BN -> "নারী"
                Lang.TR -> "kadın"
                Lang.PL -> "kobieta"
                Lang.UK -> "жінка"
                Lang.VI -> "nữ"
                Lang.TH -> "หญิง"
                Lang.ID -> "perempuan"
                Lang.SV -> "kvinna"
                Lang.NO -> "kvinne"
                Lang.DA -> "kvinde"
                Lang.FI -> "nainen"
                Lang.RO -> "femeie"
                else -> "woman"
            }
            "O" -> when (lang) {
                Lang.FR -> "autre"
                Lang.JA -> "その他"
                Lang.ES -> "otro"
                Lang.DE -> "andere"
                Lang.IT -> "altro"
                Lang.PT -> "outro"
                Lang.NL -> "anders"
                Lang.ZH -> "其他"
                Lang.KO -> "기타"
                Lang.RU -> "другое"
                Lang.AR -> "آخر"
                Lang.HI -> "अन्य"
                Lang.BN -> "অন্যান্য"
                Lang.TR -> "diğer"
                Lang.PL -> "inny"
                Lang.UK -> "інше"
                Lang.VI -> "khác"
                Lang.TH -> "อื่นๆ"
                Lang.ID -> "lainnya"
                Lang.SV -> "annan"
                Lang.NO -> "annen"
                Lang.DA -> "anden"
                Lang.FI -> "muu"
                Lang.RO -> "altul"
                else -> "other"
            }
            else -> null
        }
    }

    fun getChatString(key: String, langTag: String): String {
        val lang = try {
            Lang.values().firstOrNull { it.isoCode.lowercase() == langTag.lowercase() } ?: Lang.EN
        } catch (e: Exception) {
            Lang.EN
        }
        return when (key) {
            "noted_visual" -> when (lang) {
                Lang.FR -> "Noté ! "
                Lang.JA -> "登録しました！ "
                Lang.ES -> "¡Registrado! "
                Lang.DE -> "Notiert! "
                Lang.IT -> "Annotato! "
                Lang.PT -> "Registado! "
                Lang.NL -> "Geregistreerd! "
                Lang.KO -> "등록 완료! "
                Lang.ZH -> "已记录！"
                Lang.RU -> "Записано! "
                Lang.AR -> "تم التسجيل! "
                Lang.HI -> "दर्ज! "
                Lang.BN -> "নিবন্ধিত! "
                Lang.TR -> "Kaydedildi! "
                Lang.PL -> "Zarejestrowano! "
                Lang.UK -> "Записано! "
                Lang.VI -> "Đã ghi nhận! "
                Lang.TH -> "บันทึกแล้ว! "
                Lang.ID -> "Tercatat! "
                Lang.SV -> "Noterat! "
                Lang.NO -> "Notert! "
                Lang.DA -> "Noteret! "
                Lang.FI -> "Kirjattu! "
                Lang.RO -> "Înregistrat! "
                else -> "Noted! "
            }
            "noted_tts" -> when (lang) {
                Lang.FR -> "Noté. "
                Lang.JA -> "了解しました。"
                Lang.ES -> "Registrado. "
                Lang.DE -> "Notiert. "
                Lang.IT -> "Annotato. "
                Lang.PT -> "Registado. "
                Lang.NL -> "Geregistreerd. "
                Lang.KO -> "알겠습니다. "
                Lang.ZH -> "已记录。"
                Lang.RU -> "Записано. "
                Lang.AR -> "تم التسجيل. "
                Lang.HI -> "दर्ज कर लिया गया है। "
                Lang.BN -> "নিবন্ধিত করা হয়েছে। "
                Lang.TR -> "Kaydedildi. "
                Lang.PL -> "Zapisano. "
                Lang.UK -> "Записано. "
                Lang.VI -> "Đã ghi nhận. "
                Lang.TH -> "บันทึกเรียบร้อยแล้ว. "
                Lang.ID -> "Tercatat. "
                Lang.SV -> "Noterat. "
                Lang.NO -> "Notert. "
                Lang.DA -> "Noteret. "
                Lang.FI -> "Kirjattu. "
                Lang.RO -> "Înregistrat. "
                else -> "Noted. "
            }
            "unknown_person" -> when (lang) {
                Lang.FR -> "personne inconnue"
                Lang.JA -> "登録されたお名前"
                Lang.ES -> "persona desconocida"
                Lang.DE -> "unbekannte Person"
                Lang.IT -> "persona sconosciuta"
                Lang.PT -> "pessoa desconhecida"
                Lang.NL -> "onbekend persoon"
                Lang.KO -> "알 수 없는 사용자"
                Lang.ZH -> "未知用户"
                Lang.RU -> "неизвестный пользователь"
                Lang.AR -> "شخص غير معروف"
                Lang.HI -> "अज्ञात व्यक्ति"
                Lang.BN -> "অজ্ঞাত ব্যক্তি"
                Lang.TR -> "bilinmeyen kişi"
                Lang.PL -> "nieznana osoba"
                Lang.UK -> "невідома особа"
                Lang.VI -> "người chưa rõ danh tính"
                Lang.TH -> "บุคคลที่ไม่รู้จัก"
                Lang.ID -> "orang tidak dikenal"
                Lang.SV -> "okänd person"
                Lang.NO -> "ukjent person"
                Lang.DA -> "ukendt person"
                Lang.FI -> "tuntematon henkilö"
                Lang.RO -> "persoană necunoscută"
                else -> "unknown person"
            }
            else -> ""
        }
    }
}
