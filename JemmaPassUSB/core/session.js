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
function createSession(storage) {
  return {
    /**
     * Ouvre et charge un passeport (Bundle FHIR JSON ou projection) en mémoire active.
     * Ne doit rien écrire dans le stockage persistant sans demande explicite.
     * @param {string|Object} bundleJson
     */
    open(bundleJson) {
      throw new Error("TODO: open(bundleJson)");
    },

    /**
     * Retourne l'état courant du passeport actif en mémoire, ou null si fermé.
     * @returns {Object|null}
     */
    current() {
      throw new Error("TODO: current()");
    },

    /**
     * Ferme la session active, purge la mémoire et garantit que rien ne subsiste
     * dans le stockage injecté.
     */
    close() {
      throw new Error("TODO: close()");
    },

    /**
     * Vérifie si des traces ou résidus de la session subsistent dans le stockage.
     * @returns {boolean} true si des traces subsistent, false si le support est parfaitement vierge.
     */
    tracesLeft() {
      throw new Error("TODO: tracesLeft()");
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
