/*
 * AllergyDraftExtractor — DEPRECATED.
 *
 * Auparavant (≤ lot 14.5c27) cet extracteur tentait de parser le texte STT
 * avec des regex hardcodées (patterns "allergique à X", listes de mois FR,
 * etc.) pour pré-remplir le draft IPS avant la réponse Gemma. Fragile :
 * tout adverbe inséré cassait le pattern, les variantes orthographiques
 * STT échappaient, les dates partielles tombaient sur le format brut.
 *
 * Au lot 14.5c28 on a essayé de déléguer le parsing à Gemma via un JSON
 * délimité par `---DRAFT---`. Gemma 4 E4B ignore ce format, ça n'a jamais
 * fonctionné de façon fiable.
 *
 * Au lot 14.5c30 on bascule sur l'approche correcte : function calling
 * natif de Gemma 4 via [AllergiesAgentTools] (16 @Tool). Gemma appelle
 * setSubstance / setCategory / etc. directement. Plus aucun parsing texte.
 *
 * Ce fichier est conservé vide pour que rsync nettoie l'ancien contenu
 * (sinon les anciennes définitions regex resteraient sur disque et
 * pourraient être ré-importées par accident).
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies.chat
