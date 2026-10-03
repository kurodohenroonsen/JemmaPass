/**
 * JemmaPassChrome/core/payload_codec.ts
 *
 * Encodage et décodage des formats QR JemmaPass :
 *   - COMPRESSED `_j2:<base64(deflate-raw(pruned_json))>` (RFC 1951)
 *   - LEGACY `{"_j":"1.2", ...}`
 * Fonctionne à la fois sous Node.js et dans le navigateur Chrome.
 */

import type { JemmaProfileJ } from "./types.ts";

export const MAGIC_PREFIX = "_j2:";

export const PayloadFormat = {
  COMPRESSED: "COMPRESSED",
  LEGACY: "LEGACY",
  UNKNOWN: "UNKNOWN",
} as const;
export type PayloadFormat = (typeof PayloadFormat)[keyof typeof PayloadFormat];

export type DecodeResult =
  | { success: true; profile: JemmaProfileJ; format: PayloadFormat; rawJson: string }
  | { success: false; reason: string; cause?: unknown };

export type EncodeResult =
  | { success: true; payload: string }
  | { success: false; reason: string };

const TOP_ARRAYS = [
  "al", "md", "cn", "ph", "im", "pr", "dv", "fs",
  "pg", "rs", "ad", "cs", "gl", "en", "oc", "pv"
] as const;

/**
 * Détecte le type de format sans décompression complète.
 */
export function detectKind(text?: string | null): PayloadFormat {
  if (!text) return PayloadFormat.UNKNOWN;
  const trimmed = text.trim();
  if (trimmed.startsWith(MAGIC_PREFIX)) {
    return PayloadFormat.COMPRESSED;
  }
  if (/^\{\s*["']?_j["']?\s*:\s*["']?1\.2/.test(trimmed)) {
    return PayloadFormat.LEGACY;
  }
  return PayloadFormat.UNKNOWN;
}

/**
 * Inflate raw (RFC 1951) universel Node + Browser.
 */
export async function inflateRaw(bytes: Uint8Array): Promise<Uint8Array> {
  // 1. Essai avec node:zlib si disponible
  try {
    const zlibModule = await import("node:zlib");
    if (zlibModule && typeof zlibModule.inflateRawSync === "function") {
      return new Uint8Array(zlibModule.inflateRawSync(bytes));
    }
  } catch {
    // Non-node environment
  }

  // 2. Web Stream API (Chrome, Safari, modern browsers)
  if (typeof DecompressionStream !== "undefined") {
    const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
    const arrayBuffer = await new Response(stream).arrayBuffer();
    return new Uint8Array(arrayBuffer);
  }

  throw new Error("No deflate-raw decompression implementation available in environment");
}

/**
 * Deflate raw (RFC 1951) universel Node + Browser.
 */
export async function deflateRaw(bytes: Uint8Array): Promise<Uint8Array> {
  // 1. Essai avec node:zlib si disponible
  try {
    const zlibModule = await import("node:zlib");
    if (zlibModule && typeof zlibModule.deflateRawSync === "function") {
      return new Uint8Array(zlibModule.deflateRawSync(bytes));
    }
  } catch {
    // Non-node environment
  }

  // 2. Web Stream API
  if (typeof CompressionStream !== "undefined") {
    const stream = new Blob([bytes]).stream().pipeThrough(new CompressionStream("deflate-raw"));
    const arrayBuffer = await new Response(stream).arrayBuffer();
    return new Uint8Array(arrayBuffer);
  }

  throw new Error("No deflate-raw compression implementation available in environment");
}

/**
 * Re-hydrate les tableaux vides du profil _j 1.2.
 */
export function rehydrateProfile(raw: any): JemmaProfileJ {
  const profile: JemmaProfileJ = { ...raw };
  profile._j = "1.2";
  if (!profile.p) {
    profile.p = {};
  }
  if (!Array.isArray(profile.p.ct)) {
    profile.p.ct = [];
  }
  if (!Array.isArray(profile.p.ids)) {
    profile.p.ids = [];
  }
  if (!Array.isArray(profile.p.adrs)) {
    profile.p.adrs = [];
  }
  if (!Array.isArray(profile.p.tels)) {
    profile.p.tels = [];
  }

  for (const arrKey of TOP_ARRAYS) {
    if (!Array.isArray((profile as any)[arrKey])) {
      (profile as any)[arrKey] = [];
    }
  }
  return profile;
}

/**
 * Décode un payload QR JemmaPass (COMPRESSED _j2: ou LEGACY JSON).
 */
export async function decodePayload(text?: string | null): Promise<DecodeResult> {
  if (!text || !text.trim()) {
    return { success: false, reason: "payload empty" };
  }
  const trimmed = text.trim();
  const kind = detectKind(trimmed);

  try {
    if (kind === PayloadFormat.COMPRESSED) {
      const b64 = trimmed.slice(MAGIC_PREFIX.length).trim();
      const binaryString = typeof atob === "function"
        ? atob(b64)
        : Buffer.from(b64, "base64").toString("binary");
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      const inflatedBytes = await inflateRaw(bytes);
      const jsonStr = new TextDecoder("utf-8").decode(inflatedBytes);
      const parsed = JSON.parse(jsonStr);
      return {
        success: true,
        profile: rehydrateProfile(parsed),
        format: PayloadFormat.COMPRESSED,
        rawJson: jsonStr,
      };
    } else if (kind === PayloadFormat.LEGACY) {
      const parsed = JSON.parse(trimmed);
      return {
        success: true,
        profile: rehydrateProfile(parsed),
        format: PayloadFormat.LEGACY,
        rawJson: trimmed,
      };
    } else {
      return {
        success: false,
        reason: "not a JEMMA payload (no _j2: prefix, no _j 1.2 marker)",
      };
    }
  } catch (err: any) {
    return {
      success: false,
      reason: `decode failed: ${err?.message || String(err)}`,
      cause: err,
    };
  }
}

/**
 * Nettoie un profil _j 1.2 pour compacter le JSON (omission des champs vides).
 */
export function pruneProfile(profile: JemmaProfileJ): any {
  const out: Record<string, any> = { _j: "1.2" };
  if (profile.sid) out.sid = profile.sid;

  if (profile.p) {
    const p: Record<string, any> = {};
    if (profile.p.gn) p.gn = profile.p.gn;
    if (profile.p.fn) p.fn = profile.p.fn;
    if (profile.p.gs) p.gs = profile.p.gs;
    if (profile.p.bd) p.bd = profile.p.bd;
    if (profile.p.nat) p.nat = profile.p.nat;
    if (profile.p.bt) p.bt = profile.p.bt;
    if (profile.p.adr) p.adr = profile.p.adr;
    if (profile.p.tel) p.tel = profile.p.tel;
    if (profile.p.eml) p.eml = profile.p.eml;
    if (profile.p.idn) p.idn = profile.p.idn;
    if (profile.p.lang) p.lang = profile.p.lang;
    if (profile.p.gp) p.gp = profile.p.gp;
    if (profile.p.ids && profile.p.ids.length > 0) p.ids = profile.p.ids;
    if (profile.p.adrs && profile.p.adrs.length > 0) p.adrs = profile.p.adrs;
    if (profile.p.tels && profile.p.tels.length > 0) p.tels = profile.p.tels;

    if (profile.p.ct && profile.p.ct.length > 0) {
      p.ct = profile.p.ct.map(c => {
        const outC: Record<string, any> = {};
        if (c.n && c.n.trim()) outC.n = c.n.trim();
        if (c.r && c.r.trim()) outC.r = c.r.trim();
        if (c.p && c.p.trim()) outC.p = c.p.trim();
        if (c.e && c.e.trim()) outC.e = c.e.trim();
        if (c.adr && c.adr.trim()) outC.adr = c.adr.trim();
        return outC;
      }).filter(c => Object.keys(c).length > 0);
    }
    out.p = p;
  }

  for (const k of TOP_ARRAYS) {
    const arr = (profile as any)[k];
    if (Array.isArray(arr) && arr.length > 0) {
      out[k] = arr;
    }
  }

  return out;
}

/**
 * Encode un profil en payload compact `_j2:<base64(deflate-raw(json))>`.
 */
export async function encodeCompressed(profile: JemmaProfileJ): Promise<EncodeResult> {
  try {
    const pruned = pruneProfile(profile);
    const jsonStr = JSON.stringify(pruned);
    const utf8Bytes = new TextEncoder().encode(jsonStr);
    const deflated = await deflateRaw(utf8Bytes);
    let binary = "";
    for (let i = 0; i < deflated.length; i++) {
      binary += String.fromCharCode(deflated[i]);
    }
    const b64 = typeof btoa === "function"
      ? btoa(binary)
      : Buffer.from(binary, "binary").toString("base64");
    return { success: true, payload: `${MAGIC_PREFIX}${b64}` };
  } catch (err: any) {
    return { success: false, reason: err?.message || String(err) };
  }
}
