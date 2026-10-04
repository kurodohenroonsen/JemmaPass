/**
 * JemmaPass USB — core/session.js
 *
 * Gestionnaire de session du passeport de santé en mémoire vive uniquement.
 * Zéro persistance automatique sur l'ordinateur hôte.
 *
 * @param {Object} [storage] - Adaptateur de stockage injecté (ex: sessionStorage, localStorage, ou mock en test).
 *                            Ne jamais accéder à window.* en dur.
 * @returns {Object} Objet de session avec les méthodes contractuelles :
 *                   - open(bundleJson)
 *                   - current()
 *                   - close()
 *                   - tracesLeft()
 */
function getJemmaPassKeys(storage) {
  if (!storage) return [];
  const keys = [];
  if (typeof storage.keys === "function") {
    for (const k of storage.keys()) {
      if (typeof k === "string" && k.startsWith("jemmapass")) {
        keys.push(k);
      }
    }
  } else if (typeof storage.length === "number" && typeof storage.key === "function") {
    for (let i = 0; i < storage.length; i++) {
      const k = storage.key(i);
      if (typeof k === "string" && k.startsWith("jemmapass")) {
        keys.push(k);
      }
    }
  }
  return keys;
}

function createSession(storage) {
  let activePassport = null;

  return {
    /**
     * Ouvre et charge un passeport (Bundle FHIR JSON ou projection) en mémoire active.
     * Ne doit rien écrire dans le stockage persistant sans demande explicite.
     * @param {string|Object} bundleJson
     */
    open(bundleJson) {
      let parsed;
      if (typeof bundleJson === "string") {
        parsed = JSON.parse(bundleJson);
      } else if (typeof bundleJson === "object" && bundleJson !== null) {
        parsed = typeof structuredClone === "function"
          ? structuredClone(bundleJson)
          : JSON.parse(JSON.stringify(bundleJson));
      } else {
        throw new TypeError("Invalid bundle");
      }
      activePassport = parsed;
    },

    /**
     * Retourne l'état courant du passeport actif en mémoire, ou null si fermé.
     * Retourne une copie profonde afin de protéger l'état interne de la session.
     * @returns {Object|null}
     */
    current() {
      if (activePassport === null) return null;
      return typeof structuredClone === "function"
        ? structuredClone(activePassport)
        : JSON.parse(JSON.stringify(activePassport));
    },

    /**
     * Ferme la session active, purge la mémoire et garantit que rien ne subsiste
     * dans le stockage injecté (uniquement les clés débutant par "jemmapass").
     */
    close() {
      activePassport = null;
      if (storage) {
        const keysToRemove = getJemmaPassKeys(storage);
        for (const k of keysToRemove) {
          if (typeof storage.removeItem === "function") {
            storage.removeItem(k);
          }
        }
      }
    },

    /**
     * Vérifie si des traces ou résidus de la session subsistent dans le stockage.
     * Ne compte que les clés commençant par "jemmapass".
     * @returns {boolean} true si des traces subsistent, false si aucune trace JemmaPass n'est présente.
     */
    tracesLeft() {
      if (!storage) return false;
      const keys = getJemmaPassKeys(storage);
      return keys.length > 0;
    }
  };
}

// Export universel Node.js (CommonJS) et Navigateur
if (typeof module !== "undefined" && module.exports) {
  module.exports = { createSession };
}
if (typeof globalThis !== "undefined") {
  globalThis.createSession = createSession;
}
