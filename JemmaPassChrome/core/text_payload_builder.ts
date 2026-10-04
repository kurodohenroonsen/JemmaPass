/**
 * JemmaPassChrome/core/text_payload_builder.ts
 *
 * Constructeur du QR Texte Universel lisible par tout scanner (canal 2 JemmaPass).
 * Plafonné à 1800 octets UTF-8 max avec éviction prioritaire par rangs (12 à 1).
 * Conforme à JemmaTextPayloadBuilder.kt sur Android.
 */

import type {
  JemmaProfileJ,
  JPatient,
  JContact,
  JAllergy,
  JMedication,
  JCondition,
  JEntryGeneric,
} from "./types.ts";
import { getTextLabels } from "./translations.ts";
import type { CodeLabelResolver } from "./code_label_resolver.ts";
import { codeLabelResourceName } from "./code_label_resolver.ts";
import { SYS_V3_ROLECODE, getRoleCodeTranslation, getOfficialRoleCodeDisplay } from "./fhir_builder.ts";
import { labelFromSnomedCode } from "./blood_group.ts";

export const MAX_BYTES = 1800;
export const TRUNCATION_MARK = "✂️ …";
const EOL = "\r\n";
const BULLET = "  ▪️ ";

const RANK_ALLERGIES = 1;
const RANK_MEDICATIONS = 2;
const RANK_CONDITIONS = 3;
const RANK_PREGNANCY = 4;
const RANK_FUNCTIONAL = 5;
const RANK_CONTACTS = 6;
const RANK_DEVICES = 7;
const RANK_PATIENT_EXTRA = 8;
const RANK_PAST_PROBLEMS = 9;
const RANK_PROCEDURES = 10;
const RANK_RESULTS = 11;
const RANK_IMMUNIZATIONS = 12;

interface Part {
  icon: string;
  title: string | null;
  rank: number;
  lines: string[];
  dropped: number;
}

export function utf8ByteLength(str: string): number {
  return new TextEncoder().encode(str).length;
}

/**
 * Découpe une chaîne pour qu'elle tienne dans maxBytes sans casser de point de code.
 */
export function cutUtf8(text: string, maxBytes: Int32): string {
  const encoder = new TextEncoder();
  let left = 0;
  let right = text.length;
  let best = 0;

  while (left <= right) {
    const mid = Math.floor((left + right) / 2);
    const sub = text.slice(0, mid);
    if (encoder.encode(sub).length <= maxBytes) {
      best = mid;
      left = mid + 1;
    } else {
      right = mid - 1;
    }
  }
  return text.slice(0, best);
}

type Int32 = number;

function safePhone(phone: string): string {
  return phone.replace(/(\d{2})/g, "$1.").replace(/\.$/, "");
}

function formatContact(c: JContact, lang: string, labels?: CodeLabelResolver): string | null {
  const name = (c.n || "").trim();
  const relRaw = (c.r || "").trim();
  let relation = "";
  if (relRaw) {
    const translated = labels?.getLabel(SYS_V3_ROLECODE, relRaw, lang) || getRoleCodeTranslation(relRaw, lang);
    relation = translated || relRaw;
  }
  const reach = (c.p || "").trim() || (c.e || "").trim();

  const parts = [
    name || null,
    relation ? `(${relation})` : null,
    reach || null,
  ].filter(Boolean);

  return parts.length > 0 ? parts.join(" ") : null;
}

function formatAllergy(a: JAllergy): string {
  const name = a.d_display || a.c || "";
  const crit = a.s === "H" ? "HIGH" : a.s === "L" ? "LOW" : "UNABLE-TO-ASSESS";
  return `${name} (${crit})`;
}

function formatMedication(m: JMedication): string {
  const name = m.d_display || m.c || "";
  const dose = [m.v, m.u].filter(Boolean).join("");
  const timing = m.t || "";
  return [name, dose, timing].filter(Boolean).join(" ");
}

function formatResult(rs: JEntryGeneric): string {
  const name = rs.d_display || rs.c || "";
  const bloodLabel = rs.vc ? labelFromSnomedCode(rs.vc) : null;
  const val = bloodLabel || [rs.v, rs.u].filter(Boolean).join(" ");
  let s = name;
  if (val) s += `: ${val}`;
  if (rs.ip && rs.ip !== "N") s += ` (${rs.ip})`;
  if (rs.dt) s += ` — ${rs.dt}`;
  return s;
}

export function buildTextPayload(
  profile: JemmaProfileJ,
  lang: string = "en",
  maxBytes: number = MAX_BYTES,
  labels?: CodeLabelResolver
): string {
  const l = getTextLabels(lang);
  const p = profile.p;

  const patientCore: string[] = [];
  const patientExtra: Part = {
    icon: "👤",
    title: null,
    rank: RANK_PATIENT_EXTRA,
    lines: [],
    dropped: 0,
  };

  if (p) {
    const name = [p.gn, p.fn].filter(Boolean).join(" ");
    let genderLabel = "";
    if (p.gs) {
      const gs = p.gs.toUpperCase();
      genderLabel = gs === "M" ? l.gender_m : gs === "F" ? l.gender_f : gs === "O" ? l.gender_o : l.gender_u;
    }
    patientCore.push(` 🔹 ${name}${genderLabel ? ` (${genderLabel})` : ""}`);
    if (p.bd) patientCore.push(` 📅 ${l.patient_birth}: ${p.bd}`);
    if (p.bt) patientCore.push(` 🩸 ${l.patient_blood}: ${p.bt}`);
    if (p.lang) patientCore.push(` 🗣 ${l.patient_lang}: ${p.lang}`);

    if (p.adr) patientExtra.lines.push(` 📍 ${l.patient_addr}: ${p.adr}`);
    if (p.tel) patientExtra.lines.push(` 📞 ${l.patient_phone}: ${safePhone(p.tel)}`);
    if (p.eml) patientExtra.lines.push(` 📧 ${l.patient_email}: ${p.eml}`);
    if (p.idn) patientExtra.lines.push(` 🆔 ${l.patient_id}: ${p.idn}`);
  }

  function makePart<T>(
    icon: string,
    title: string,
    rank: number,
    items: T[],
    formatter: (item: T) => string | null
  ): Part {
    const lines: string[] = [];
    for (const it of items) {
      const formatted = formatter(it);
      if (formatted) lines.push(BULLET + formatted);
    }
    return { icon, title, rank, lines, dropped: 0 };
  }

  const byDateDesc = (a: JEntryGeneric, b: JEntryGeneric) => {
    const da = a.dt || "";
    const db = b.dt || "";
    return db.localeCompare(da);
  };

  const sections: Part[] = [
    makePart("⚠️", l.allergies_title, RANK_ALLERGIES, profile.al || [], formatAllergy),
    makePart("💊", l.medications_title, RANK_MEDICATIONS, profile.md || [], formatMedication),
    makePart("🩺", l.conditions_title, RANK_CONDITIONS, profile.cn || [], c => c.d_display || c.c || ""),
    makePart("☎️", l.contacts_title, RANK_CONTACTS, p?.ct || [], c => formatContact(c, lang, labels)),
    makePart("💉", l.immunizations_title, RANK_IMMUNIZATIONS, [...(profile.im || [])].sort(byDateDesc), im => {
      let s = im.d_display || im.c || "";
      if (im.dt) s += ` — ${im.dt}`;
      if (im.dn != null) s += ` · #${im.dn}`;
      if (im.st && im.st !== "completed") s += ` (${im.st})`;
      return s;
    }),
    makePart("🏥", l.procedures_title, RANK_PROCEDURES, [...(profile.pr || [])].sort(byDateDesc), pr => {
      let s = pr.d_display || pr.c || "";
      if (pr.dt) s += ` — ${pr.dt}`;
      if (pr.st && pr.st !== "completed") s += ` (${pr.st})`;
      return s;
    }),
    makePart("📟", l.devices_title, RANK_DEVICES, profile.dv || [], dv => {
      let s = dv.d_display || dv.c || "";
      if (dv.dt) s += ` — ${dv.dt}`;
      if (dv.st && dv.st !== "active") s += ` (${dv.st})`;
      return s;
    }),
    makePart("🧪", l.results_title, RANK_RESULTS, [...(profile.rs || [])].sort(byDateDesc), formatResult),
    makePart("📜", l.past_problems_title, RANK_PAST_PROBLEMS, [...(profile.ph || [])].sort(byDateDesc), ph => {
      let s = ph.d_display || ph.c || "";
      if (ph.dt && ph.ab) s += ` — ${ph.dt} → ${ph.ab}`;
      else if (ph.dt) s += ` — ${ph.dt}`;
      else if (ph.ab) s += ` — → ${ph.ab}`;
      if (ph.st && ph.st !== "resolved") s += ` (${ph.st})`;
      return s;
    }),
    makePart("🤰", l.pregnancy_title, RANK_PREGNANCY, profile.pg || [], pg => {
      let s = pg.d_display || pg.c || "";
      if (pg.v) s += `: ${pg.v}`;
      if (pg.dt) s += ` — ${pg.dt}`;
      return s;
    }),
    makePart("♿", l.functional_title, RANK_FUNCTIONAL, profile.fs || [], fs => {
      let s = fs.d_display || fs.c || "";
      if (fs.dt) s += ` — ${fs.dt}`;
      if (fs.st && fs.st !== "active") s += ` (${fs.st})`;
      return s;
    }),
  ];

  const droppable: Part[] = [...sections, patientExtra];

  function render(): string {
    const sb: string[] = [];
    sb.push(l.header, EOL, EOL);

    if (p) {
      sb.push("👤 [ ", l.patient_title, " ]", EOL);
      for (const line of patientCore) sb.push(line, EOL);
      for (const line of patientExtra.lines) sb.push(line, EOL);
      sb.push(EOL);
    }

    for (const s of sections) {
      if (s.lines.length === 0) continue;
      sb.push(s.icon, " [ ", s.title || "", " ]", EOL);
      for (const line of s.lines) sb.push(line, EOL);
      if (s.dropped > 0) sb.push("  ", TRUNCATION_MARK, ` +${s.dropped}`, EOL);
      sb.push(EOL);
    }

    const cut = droppable.filter(d => d.dropped > 0);
    if (cut.length > 0) {
      const cutIcons = cut.sort((a, b) => a.rank - b.rank).map(c => c.icon).join(" ");
      sb.push(TRUNCATION_MARK, " [ ", l.truncated, " ] ", cutIcons, EOL, EOL);
    }

    sb.push(l.footer, EOL);
    return sb.join("");
  }

  let capped = render();
  while (utf8ByteLength(capped) > maxBytes) {
    // Trouve la section qui a encore des lignes au rang le plus élevé
    const victim = droppable
      .filter(d => d.lines.length > 0)
      .sort((a, b) => b.rank - a.rank)[0];

    if (!victim) break;
    victim.lines.pop();
    victim.dropped++;
    capped = render();
  }

  if (utf8ByteLength(capped) > maxBytes) {
    const tail = `${EOL}${TRUNCATION_MARK} [ ${l.truncated} ]${EOL}`;
    const tailBytes = utf8ByteLength(tail);
    if (tailBytes <= maxBytes) {
      capped = cutUtf8(capped, maxBytes - tailBytes) + tail;
    } else {
      capped = cutUtf8(capped, maxBytes);
    }
  }

  return capped;
}
