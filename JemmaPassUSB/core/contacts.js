/**
 * JemmaPass USB — core/contacts.js
 *
 * Traitement des contacts d'urgence (p.ct <-> Patient.contact).
 * Strictement aligné sur le contrat commun qa/vectors/contacts/ (ct-001..006).
 * Zéro dépendance externe, compatible Node CLI et Navigateur.
 */
"use strict";

const V3_ROLE_CODE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-RoleCode";

// Table des rôles officiels de relation personnelle (personal-relationship-uv-ips)
// Libellés traduits selon la langue du lecteur (PROTOCOL §9.1)
const V3_ROLE_CATALOG = {
  "SPS": { display: "spouse", labels: { en: "spouse", fr: "conjoint", ja: "配偶者" } },
  "DAUC": { display: "daughter", labels: { en: "daughter", fr: "fille", ja: "娘" } },
  "SONC": { display: "son", labels: { en: "son", fr: "fils", ja: "息子" } },
  "PRN": { display: "parent", labels: { en: "parent", fr: "parent", ja: "親" } },
  "MTH": { display: "mother", labels: { en: "mother", fr: "mère", ja: "母" } },
  "FTH": { display: "father", labels: { en: "father", fr: "père", ja: "父" } },
  "SIB": { display: "sibling", labels: { en: "sibling", fr: "fratrie", ja: "兄弟姉妹" } },
  "BRO": { display: "brother", labels: { en: "brother", fr: "frère", ja: "兄弟" } },
  "SIS": { display: "sister", labels: { en: "sister", fr: "sœur", ja: "姉妹" } },
  "FRND": { display: "unrelated friend", labels: { en: "unrelated friend", fr: "ami", ja: "友人" } },
  "NBOR": { display: "neighbor", labels: { en: "neighbor", fr: "voisin", ja: "隣人" } },
  "GUARD": { display: "guardian", labels: { en: "guardian", fr: "tuteur", ja: "後見人" } },
  "DOMPART": { display: "domestic partner", labels: { en: "domestic partner", fr: "partenaire", ja: "同居人" } },
  "C": { display: "Emergency Contact", labels: { en: "Emergency Contact", fr: "Contact d'urgence", ja: "緊急連絡先" } }
};

/**
 * Construit le tableau Patient.contact pour le Bundle FHIR R4 IPS depuis un tableau p.ct.
 *
 * @param {Array<Object>} [rawContacts] - Liste des contacts du profil _j (p.ct)
 * @param {string} [uiLang="en"] - Langue de l'interface / du lecteur
 * @returns {Array<Object>} Tableau Patient.contact conforme aux vecteurs
 */
function buildPatientContacts(rawContacts, uiLang = "en") {
  if (!Array.isArray(rawContacts) || rawContacts.length === 0) return [];
  const contacts = [];

  for (const c of rawContacts) {
    if (!c || typeof c !== "object") continue;

    const name = c.n && typeof c.n === "string" ? c.n.trim() : "";
    const phone = c.p && typeof c.p === "string" ? c.p.trim() : "";
    const email = c.e && typeof c.e === "string" ? c.e.trim() : "";
    const address = c.adr && typeof c.adr === "string" ? c.adr.trim() : "";
    const relRaw = c.r && typeof c.r === "string" ? c.r.trim() : "";

    // Règle pat-1 : un contact entièrement vide ou ne contenant aucune coordonnée ni nom est ignoré
    if (!name && !phone && !email && !address && !relRaw) continue;
    // Règle pat-1 stricte : au moins un parmi name, telecom, address doit exister pour créer un contact valide
    if (!name && !phone && !email && !address) continue;

    const contactObj = {};

    // 1. Relationship (si présente)
    if (relRaw) {
      const codeKey = relRaw.toUpperCase();
      const role = V3_ROLE_CATALOG[codeKey];
      if (role) {
        const textVal = (role.labels && role.labels[uiLang]) || role.display;
        contactObj.relationship = [
          {
            coding: [
              {
                system: V3_ROLE_CODE_SYSTEM,
                code: codeKey,
                display: role.display
              }
            ],
            text: textVal
          }
        ];
      } else {
        // Code inconnu ou texte libre -> text seul sans coding (ct-005)
        contactObj.relationship = [
          {
            text: relRaw
          }
        ];
      }
    }

    // 2. Name
    if (name) {
      contactObj.name = {
        text: name
      };
    }

    // 3. Telecom (phone d'abord, puis email, ordre conforme)
    const telecoms = [];
    if (phone) {
      // Décision Kudoro (cycle 26/27) : pas de champ "use": "mobile"
      telecoms.push({
        system: "phone",
        value: phone
      });
    }
    if (email) {
      telecoms.push({
        system: "email",
        value: email
      });
    }
    if (telecoms.length > 0) {
      contactObj.telecom = telecoms;
    }

    // 4. Address (ct-002)
    if (address) {
      contactObj.address = {
        text: address
      };
    }

    contacts.push(contactObj);
  }

  return contacts;
}

/**
 * Construit la liste des lignes formatées pour le canal QR texte.
 * L'adresse est exclue du QR texte (ct-002).
 * Format : "Nom (relation) +phone" ou "Nom (relation) email"
 *
 * @param {Array<Object>} [rawContacts]
 * @param {string} [uiLang="en"]
 * @returns {Array<string>}
 */
function buildTextQrContacts(rawContacts, uiLang = "en") {
  if (!Array.isArray(rawContacts) || rawContacts.length === 0) return [];
  const lines = [];

  for (const c of rawContacts) {
    if (!c || typeof c !== "object") continue;
    const name = c.n && typeof c.n === "string" ? c.n.trim() : "";
    const phone = c.p && typeof c.p === "string" ? c.p.trim() : "";
    const email = c.e && typeof c.e === "string" ? c.e.trim() : "";
    const relRaw = c.r && typeof c.r === "string" ? c.r.trim() : "";

    if (!name && !phone && !email) continue;

    let relLabel = "";
    if (relRaw) {
      const codeKey = relRaw.toUpperCase();
      const role = V3_ROLE_CATALOG[codeKey];
      relLabel = role ? ((role.labels && role.labels[uiLang]) || role.display) : relRaw;
    }

    const parts = [];
    if (name) parts.push(name);
    if (relLabel) parts.push(`(${relLabel})`);
    if (phone) parts.push(phone);
    else if (email) parts.push(email);

    if (parts.length > 0) {
      lines.push(parts.join(" "));
    }
  }

  return lines;
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    V3_ROLE_CODE_SYSTEM,
    V3_ROLE_CATALOG,
    buildPatientContacts,
    buildTextQrContacts
  };
}
if (typeof globalThis !== "undefined") {
  globalThis.JemmaContacts = {
    buildPatientContacts,
    buildTextQrContacts
  };
}
