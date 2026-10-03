/*
 * TEST (improvement USB-0001, privacy) — not to be edited by the implementer
 *
 * The passport belongs to the USB key, not to the computer it is plugged into. Measured in step 0 :
 * IndexedDB and localStorage of a file:// page live in the browser profile of the host and are shared
 * by every local page. So the session keeps the passport in memory only.
 *
 *   node --test JemmaPassUSB/tests/
 *
 * Keys written by JemmaPass in a storage start with "jemmapass" ; keys of other pages are not ours.
 */
"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { createSession } = require("../core/session.js");

function spyStorage(initial = {}) {
  const data = new Map(Object.entries(initial));
  const writes = [];
  return {
    writes,
    get length() { return data.size; },
    key(i) { return Array.from(data.keys())[i] ?? null; },
    getItem(k) { return data.has(k) ? data.get(k) : null; },
    setItem(k, v) { writes.push(k); data.set(k, String(v)); },
    removeItem(k) { data.delete(k); },
    clear() { data.clear(); },
    keys() { return Array.from(data.keys()); },
  };
}

const bundle = {
  resourceType: "Bundle",
  type: "document",
  entry: [{ fullUrl: "urn:uuid:00000000-0000-3000-8000-000000000001", resource: { resourceType: "Patient", name: [{ text: "Haru Tanaka" }] } }],
};

test("USB-S01 an opened passport is the current one, from an object or from JSON text", () => {
  const a = createSession(spyStorage());
  a.open(bundle);
  assert.deepEqual(a.current(), bundle);
  const b = createSession(spyStorage());
  b.open(JSON.stringify(bundle));
  assert.deepEqual(b.current(), bundle);
});

test("USB-S02 opening and reading a passport writes nothing in the storage", () => {
  const storage = spyStorage();
  const s = createSession(storage);
  s.open(bundle);
  s.current();
  assert.deepEqual(storage.writes, [], "the host computer must not receive the passport");
  assert.equal(storage.length, 0);
  assert.equal(s.tracesLeft(), false);
});

test("USB-S03 after close nothing is current and nothing is left", () => {
  const storage = spyStorage();
  const s = createSession(storage);
  s.open(bundle);
  s.close();
  assert.equal(s.current(), null);
  assert.equal(s.tracesLeft(), false);
  assert.equal(storage.length, 0);
});

test("USB-S04 traces of an earlier JemmaPass session are seen, then removed by close", () => {
  const storage = spyStorage({ "jemmapass.cache": "{\"old\":true}", "jemmapass-draft": "x" });
  const s = createSession(storage);
  assert.equal(s.tracesLeft(), true, "a passport left by an earlier session must be reported");
  s.close();
  assert.equal(s.tracesLeft(), false);
  assert.deepEqual(storage.keys(), []);
});

test("USB-S05 keys of other local pages are neither counted nor erased", () => {
  const storage = spyStorage({ "other-page-setting": "1" });
  const s = createSession(storage);
  assert.equal(s.tracesLeft(), false);
  s.open(bundle);
  s.close();
  assert.deepEqual(storage.keys(), ["other-page-setting"]);
});

test("USB-S06 without a storage the session still works, in memory only", () => {
  const s = createSession();
  assert.equal(s.current(), null);
  s.open(bundle);
  assert.deepEqual(s.current(), bundle);
  assert.equal(s.tracesLeft(), false);
  s.close();
  assert.equal(s.current(), null);
});

test("USB-S07 the session never reaches for the storages of the browser by itself", () => {
  const touched = [];
  const names = ["localStorage", "sessionStorage", "indexedDB"];
  const saved = {};
  for (const n of names) {
    saved[n] = Object.getOwnPropertyDescriptor(globalThis, n);
    Object.defineProperty(globalThis, n, { configurable: true, get() { touched.push(n); return undefined; } });
  }
  try {
    const s = createSession(spyStorage());
    s.open(bundle); s.current(); s.tracesLeft(); s.close();
    const t = createSession();
    t.open(bundle); t.tracesLeft(); t.close();
  } finally {
    for (const n of names) { if (saved[n]) Object.defineProperty(globalThis, n, saved[n]); else delete globalThis[n]; }
  }
  assert.deepEqual(touched, [], "only the injected storage may be used");
});

test("USB-S08 text that is not JSON is refused and leaves the session as it was", () => {
  const s = createSession(spyStorage());
  assert.throws(() => s.open("{not json"));
  assert.equal(s.current(), null);
  s.open(bundle);
  assert.throws(() => s.open("{not json"));
  assert.deepEqual(s.current(), bundle);
});

test("USB-S09 what current() returns cannot be used to change the passport behind the session's back", () => {
  const s = createSession(spyStorage());
  s.open(bundle);
  const copy = s.current();
  copy.entry[0].resource.name[0].text = "changed";
  assert.equal(s.current().entry[0].resource.name[0].text, "Haru Tanaka");
});
