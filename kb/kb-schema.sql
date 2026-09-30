CREATE TABLE terminology_codes (
        code             TEXT    PRIMARY KEY,
        atc_code         TEXT,
        atc_source       TEXT,                  -- ✨ NEW: 'umls_direct'|'ingredient'|'tradename'|'isa'|'form'
        rxnorm_cui       TEXT,
        snomed_code      TEXT,
        ips_validated    INTEGER NOT NULL DEFAULT 0,
        primary_display  TEXT,
        system           TEXT,
        category         TEXT
    , is_combo INTEGER DEFAULT 0) WITHOUT ROWID
    ;
CREATE TABLE IF NOT EXISTS 'terminology_latin_data'(id INTEGER PRIMARY KEY, block BLOB);
CREATE TABLE IF NOT EXISTS 'terminology_latin_idx'(segid, term, pgno, PRIMARY KEY(segid, term)) WITHOUT ROWID;
CREATE TABLE IF NOT EXISTS 'terminology_latin_content'(id INTEGER PRIMARY KEY, c0, c1, c2);
CREATE TABLE IF NOT EXISTS 'terminology_latin_docsize'(id INTEGER PRIMARY KEY, sz BLOB);
CREATE TABLE IF NOT EXISTS 'terminology_latin_config'(k PRIMARY KEY, v) WITHOUT ROWID;
CREATE TABLE IF NOT EXISTS 'terminology_cjk_data'(id INTEGER PRIMARY KEY, block BLOB);
CREATE TABLE IF NOT EXISTS 'terminology_cjk_idx'(segid, term, pgno, PRIMARY KEY(segid, term)) WITHOUT ROWID;
CREATE TABLE IF NOT EXISTS 'terminology_cjk_content'(id INTEGER PRIMARY KEY, c0, c1, c2);
CREATE TABLE IF NOT EXISTS 'terminology_cjk_docsize'(id INTEGER PRIMARY KEY, sz BLOB);
CREATE TABLE IF NOT EXISTS 'terminology_cjk_config'(k PRIMARY KEY, v) WITHOUT ROWID;
CREATE TABLE atc_hierarchy (
        atc_code     TEXT PRIMARY KEY,
        parent_atc   TEXT,
        level        INTEGER NOT NULL,
        name_en      TEXT NOT NULL,
        name_fr      TEXT,
        name_jp      TEXT,
        description  TEXT
    ) WITHOUT ROWID
    ;
CREATE TABLE dosages (
        dose_id         INTEGER PRIMARY KEY AUTOINCREMENT,
        drug_atc        TEXT NOT NULL REFERENCES atc_hierarchy(atc_code),
        route           TEXT,
        dose_ddd        REAL,
        dose_unit       TEXT,
        population      TEXT,
        note            TEXT,
        UNIQUE (drug_atc, route, population)
    );
CREATE TABLE sqlite_sequence(name,seq);
CREATE TABLE ddinter_drugs (
        ddinter_id   TEXT PRIMARY KEY,
        name         TEXT NOT NULL,
        primary_atc  TEXT,
        atc_codes    TEXT,                     -- CSV (multi-ATC)
        formula      TEXT,
        weight       REAL,
        cas          TEXT,
        smiles       TEXT,
        inchi        TEXT,
        iupac        TEXT,                     -- ✨ NEW: IUPAC chemical name (1714/2289 drugs)
        description  TEXT
    ) WITHOUT ROWID
    ;
CREATE TABLE ddi_facts (
        fact_id              INTEGER PRIMARY KEY AUTOINCREMENT,
        drug_a_ddinter_id    TEXT NOT NULL REFERENCES ddinter_drugs(ddinter_id),
        drug_b_ddinter_id    TEXT NOT NULL REFERENCES ddinter_drugs(ddinter_id),
        drug_a_name          TEXT,             -- ✨ NEW: libellé direct (évite JOIN)
        drug_b_name          TEXT,             -- ✨ NEW
        severity             TEXT NOT NULL,
        mechanism_category   TEXT,
        description_en       TEXT,
        management_en        TEXT,
        alternative_atc      TEXT,
        ddinter_interaction_id INTEGER UNIQUE,
        source               TEXT NOT NULL DEFAULT 'DDInter2'
    );
CREATE TABLE ddi_atc_pairs (
        drug_a_atc  TEXT NOT NULL,
        drug_b_atc  TEXT NOT NULL,
        fact_id     INTEGER NOT NULL REFERENCES ddi_facts(fact_id),
        PRIMARY KEY (drug_a_atc, drug_b_atc, fact_id)
    ) WITHOUT ROWID
    ;
CREATE TABLE ddi_mechanism_flags (
        fact_id              INTEGER NOT NULL REFERENCES ddi_facts(fact_id),
        synergistic_effect   INTEGER NOT NULL DEFAULT 0,
        antagonistic_effect  INTEGER NOT NULL DEFAULT 0,
        absorption           INTEGER NOT NULL DEFAULT 0,
        distribution         INTEGER NOT NULL DEFAULT 0,
        metabolism           INTEGER NOT NULL DEFAULT 0,
        excretion            INTEGER NOT NULL DEFAULT 0,
        others               INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (fact_id)
    ) WITHOUT ROWID
    ;
CREATE TABLE ddi_alternatives (
        fact_id                  INTEGER NOT NULL REFERENCES ddi_facts(fact_id),
        for_ddinter_id           TEXT NOT NULL,    -- DDInter id du médoc à substituer
        alternative_ddinter_id   TEXT NOT NULL,    -- DDInter id de l'alternative
        alternative_atc_class    TEXT NOT NULL,    -- classe ATC commune (clé du dict)
        direction                TEXT NOT NULL,    -- 'for_drug' | 'for_source_drug'
        PRIMARY KEY (fact_id, for_ddinter_id, alternative_ddinter_id)
    ) WITHOUT ROWID
    ;
CREATE TABLE interactions_food (
        dfi_id              INTEGER PRIMARY KEY AUTOINCREMENT,
        drug_ddinter_id     TEXT REFERENCES ddinter_drugs(ddinter_id),
        drug_atc            TEXT,
        drug_name           TEXT,                   -- ✨ NEW
        food_name_en        TEXT NOT NULL,
        severity            TEXT NOT NULL,
        mechanism_category  TEXT,
        description_en      TEXT,
        management_en       TEXT,
        references_txt      TEXT,                   -- ✨ NEW
        source              TEXT NOT NULL DEFAULT 'DDInter2',
        UNIQUE (drug_ddinter_id, food_name_en, severity)
    );
CREATE TABLE drug_disease_interactions (
        ddsi_id          INTEGER PRIMARY KEY AUTOINCREMENT,
        drug_ddinter_id  TEXT REFERENCES ddinter_drugs(ddinter_id),
        drug_atc         TEXT,
        drug_name        TEXT,                      -- ✨ NEW
        disease_name_en  TEXT NOT NULL,
        severity         TEXT,
        description_en   TEXT,
        text_long        TEXT,                      -- ✨ NEW (full clinical text)
        management_en    TEXT,
        references_txt   TEXT,                      -- ✨ NEW
        source           TEXT NOT NULL DEFAULT 'DDInter2',
        UNIQUE (drug_ddinter_id, disease_name_en)
    );
CREATE TABLE therapeutic_duplications (
        td_id             INTEGER PRIMARY KEY AUTOINCREMENT,
        drug_b_ddinter_id TEXT REFERENCES ddinter_drugs(ddinter_id),
        drug_b_atc        TEXT,
        drug_b_name       TEXT,                     -- ✨ NEW
        pharma_class      TEXT NOT NULL,
        drugmulti         TEXT,                     -- ✨ NEW (combo INN string)
        drugmulti_trade   TEXT,                     -- ✨ NEW (combo brand name)
        warning_en        TEXT,
        note_en           TEXT,                     -- ✨ NEW
        source            TEXT NOT NULL DEFAULT 'DDInter2',
        UNIQUE (drug_b_ddinter_id, pharma_class)
    );
CREATE TABLE semantic_types (
        cui  TEXT NOT NULL REFERENCES terminology_codes(code),
        tui  TEXT NOT NULL,
        stn  TEXT,                                  -- ✨ NEW: Semantic Tree Number
        sty  TEXT NOT NULL,
        PRIMARY KEY (cui, tui)
    ) WITHOUT ROWID
    ;
CREATE TABLE concept_relations (
        cui1  TEXT NOT NULL,
        cui2  TEXT NOT NULL,
        rel   TEXT NOT NULL,
        rela  TEXT,
        sab   TEXT NOT NULL,
        rg    TEXT,                                 -- ✨ NEW: Relationship Group
        dir   TEXT,                                 -- ✨ NEW: Directionality (Y/N)
        PRIMARY KEY (cui1, rela, cui2)
    ) WITHOUT ROWID
    ;
CREATE TABLE drug_attributes (
        cui  TEXT NOT NULL REFERENCES terminology_codes(code),
        atn  TEXT NOT NULL,
        atv  TEXT NOT NULL,
        sab  TEXT NOT NULL,
        PRIMARY KEY (cui, atn, atv)
    ) WITHOUT ROWID
    ;
CREATE TABLE concept_definitions (
        cui         TEXT NOT NULL REFERENCES terminology_codes(code),
        sab         TEXT NOT NULL,
        definition  TEXT NOT NULL,
        language    TEXT DEFAULT 'ENG',
        PRIMARY KEY (cui, sab)
    ) WITHOUT ROWID
    ;
CREATE TABLE rxnorm_concepts (
        rxcui      TEXT NOT NULL,
        tty        TEXT NOT NULL,                   -- IN, BN, SCD, SBD, DF, etc.
        sab        TEXT NOT NULL,                   -- RXNORM, MTHSPL, MMSL, etc.
        str        TEXT NOT NULL,                   -- le libellé
        suppress   TEXT,
        is_pref    INTEGER NOT NULL DEFAULT 0,      -- 1 si ISPREF='Y'
        rxaui      TEXT,                            -- atom UI (utile pour FK)
        PRIMARY KEY (rxcui, tty, sab, str)
    ) WITHOUT ROWID
    ;
CREATE TABLE rxnorm_relations (
        rxcui1   TEXT NOT NULL,
        rxcui2   TEXT NOT NULL,
        rel      TEXT NOT NULL,
        rela     TEXT NOT NULL,
        dir      TEXT,                               -- directionality (Y/N)
        rg       TEXT,
        PRIMARY KEY (rxcui1, rela, rxcui2)
    ) WITHOUT ROWID
    ;
CREATE TABLE rxnorm_semantic_types (
        rxcui  TEXT NOT NULL,
        tui    TEXT NOT NULL,
        stn    TEXT,
        sty    TEXT NOT NULL,
        PRIMARY KEY (rxcui, tui)
    ) WITHOUT ROWID
    ;
CREATE TABLE word_index (
        word  TEXT NOT NULL,
        lang  TEXT NOT NULL,
        cui   TEXT NOT NULL,
        PRIMARY KEY (word, lang, cui)
    ) WITHOUT ROWID
    ;
CREATE TABLE substance_unii_bridge (
        unii_code         TEXT PRIMARY KEY,
        rxcui             TEXT,
        display_en        TEXT NOT NULL,
        inn_id            TEXT,
        usan_id           TEXT,                     -- ✨ NEW
        ingredient_type   TEXT,
        substance_type    TEXT,
        cas_rn            TEXT,                     -- ✨ NEW (CAS Registry Number)
        ncit_code         TEXT,                     -- ✨ NEW (NCI Thesaurus)
        pubchem_cid       TEXT,                     -- ✨ NEW (PubChem CID)
        epa_comptox       TEXT,                     -- ✨ NEW (EPA DTXSID)
        dailymed_setid    TEXT,                     -- ✨ NEW (FDA DailyMed → drug labels)
        molecular_formula TEXT,                     -- ✨ NEW (MF text)
        smiles            TEXT,
        inchikey          TEXT,
        snomed_code       TEXT,
        atc_code          TEXT,
        allergen_class    TEXT
    );
CREATE TABLE ips_field_rules (
        resource          TEXT NOT NULL,
        field_path        TEXT NOT NULL,
        cardinality       TEXT NOT NULL,
        required          INTEGER NOT NULL,
        binding_vs        TEXT NOT NULL DEFAULT '',
        binding_strength  TEXT,
        binding_purpose   TEXT,
        data_type         TEXT,
        short             TEXT,
        definition        TEXT,
        PRIMARY KEY (resource, field_path, binding_vs)
    ) WITHOUT ROWID
    ;
CREATE TABLE ips_valuesets (
        vs_id          TEXT NOT NULL,
        code           TEXT NOT NULL,
        code_system    TEXT NOT NULL,
        display_en     TEXT NOT NULL,
        ips_required   INTEGER DEFAULT 0,
        PRIMARY KEY (vs_id, code, code_system)
    ) WITHOUT ROWID
    ;
CREATE TABLE ips_valuesets_translations (
        vs_id        TEXT NOT NULL,
        code         TEXT NOT NULL,
        code_system  TEXT NOT NULL,
        lang         TEXT NOT NULL,
        display      TEXT NOT NULL,
        PRIMARY KEY (vs_id, code, code_system, lang),
        FOREIGN KEY (vs_id, code, code_system)
            REFERENCES ips_valuesets(vs_id, code, code_system)
    ) WITHOUT ROWID
    ;
CREATE TABLE ips_concept_maps (
        map_id          TEXT NOT NULL,
        source_system   TEXT NOT NULL,
        source_code     TEXT NOT NULL,
        target_system   TEXT NOT NULL,
        target_code     TEXT NOT NULL,
        target_display  TEXT,
        equivalence     TEXT,
        PRIMARY KEY (map_id, source_code, target_code)
    ) WITHOUT ROWID
    ;
CREATE TABLE allergy_cross_reactivity (
        allergen_class       TEXT NOT NULL,         -- ex 'J01C' (penicillins)
        cross_reactive_class TEXT NOT NULL,         -- ex 'J01D' (cephalosporins)
        risk_level           TEXT NOT NULL,         -- 'HIGH' | 'MODERATE' | 'LOW' | 'NONE'
        percent_estimate     TEXT,                  -- '1-10%', '<1%', '100%', '0%'
        notes                TEXT,
        source               TEXT,                  -- 'WHO_2019' | 'ANSM_2020' | 'UpToDate' | 'BNF'
        PRIMARY KEY (allergen_class, cross_reactive_class)
    ) WITHOUT ROWID
    ;
CREATE TABLE atc_alternatives (
        source_atc       TEXT NOT NULL,             -- ex 'J01CR02'
        alternative_atc  TEXT NOT NULL,             -- ex 'J01FA10' (azithromycin)
        similarity_score REAL NOT NULL,             -- 0.0-1.0
        relation_type    TEXT NOT NULL,             -- 'sibling_l5'|'sibling_l4'|'sibling_l3'|'cross_class_eml'|'ddinter_alt'
        eml_priority     INTEGER DEFAULT 0,         -- 1 si dans WHO EML core (post-hackathon)
        aware_category   TEXT,                      -- 'Access'|'Watch'|'Reserve' (post-hackathon)
        avoid_if_allergic_to TEXT,                  -- CSV des classes ATC à éviter
        notes            TEXT,
        PRIMARY KEY (source_atc, alternative_atc)
    ) WITHOUT ROWID
    ;
CREATE TABLE drug_names_meta (
        code             TEXT PRIMARY KEY,
        atc_code         TEXT NOT NULL,
        cui              TEXT,
        canonical_name   TEXT NOT NULL,
        ddinter_id       TEXT,
        n_langs          INTEGER DEFAULT 0,
        source           TEXT DEFAULT 'cui'
    ) WITHOUT ROWID
    ;
CREATE TABLE IF NOT EXISTS 'drug_names_multilingual_data'(id INTEGER PRIMARY KEY, block BLOB);
CREATE TABLE IF NOT EXISTS 'drug_names_multilingual_idx'(segid, term, pgno, PRIMARY KEY(segid, term)) WITHOUT ROWID;
CREATE TABLE IF NOT EXISTS 'drug_names_multilingual_content'(id INTEGER PRIMARY KEY, c0, c1, c2, c3);
CREATE TABLE IF NOT EXISTS 'drug_names_multilingual_docsize'(id INTEGER PRIMARY KEY, sz BLOB);
CREATE TABLE IF NOT EXISTS 'drug_names_multilingual_config'(k PRIMARY KEY, v) WITHOUT ROWID;
CREATE TABLE IF NOT EXISTS 'drug_names_cjk_data'(id INTEGER PRIMARY KEY, block BLOB);
CREATE TABLE IF NOT EXISTS 'drug_names_cjk_idx'(segid, term, pgno, PRIMARY KEY(segid, term)) WITHOUT ROWID;
CREATE TABLE IF NOT EXISTS 'drug_names_cjk_content'(id INTEGER PRIMARY KEY, c0, c1, c2, c3);
CREATE TABLE IF NOT EXISTS 'drug_names_cjk_docsize'(id INTEGER PRIMARY KEY, sz BLOB);
CREATE TABLE IF NOT EXISTS 'drug_names_cjk_config'(k PRIMARY KEY, v) WITHOUT ROWID;
CREATE TABLE kb_sources (
        source_id     TEXT PRIMARY KEY,
        full_name     TEXT NOT NULL,
        url           TEXT,
        license       TEXT,
        version       TEXT,
        record_count  INTEGER,
        ingested_at   TEXT DEFAULT (datetime('now'))
    );
CREATE TABLE build_metadata (
        key    TEXT PRIMARY KEY,
        value  TEXT
    );
CREATE TABLE atc_combinations_index (
                atc_code            TEXT NOT NULL,
                l3_family           TEXT NOT NULL,
                l4_family           TEXT NOT NULL,
                name_en             TEXT NOT NULL,
                ingredient_lc       TEXT NOT NULL,
                ingredient_position INTEGER NOT NULL,
                n_ingredients       INTEGER NOT NULL,
                detection_marker    TEXT NOT NULL,
                PRIMARY KEY (atc_code, ingredient_position)
            );
CREATE TABLE sqlite_stat1(tbl,idx,stat);
CREATE TABLE combo_drug_resolutions (
                code             TEXT PRIMARY KEY,
                rxcui            TEXT,
                primary_atc      TEXT,
                ingredient_atcs  TEXT,
                ingredient_cuis  TEXT,
                n_ingredients    INTEGER,
                detection_method TEXT
            ) WITHOUT ROWID
        ;
CREATE TABLE who_eml_2023 (
                atc_code   TEXT PRIMARY KEY,
                eml_section TEXT,
                note        TEXT,
                source      TEXT DEFAULT 'WHO_EML_23rd_List_2023'
            ) WITHOUT ROWID
        ;
CREATE TABLE who_aware_2024 (
                atc_code        TEXT NOT NULL,
                category        TEXT NOT NULL CHECK (category IN ('Access','Watch','Reserve')),
                drug_name       TEXT,
                key_indication  TEXT,
                source          TEXT DEFAULT 'WHO_AWaRe_2024',
                PRIMARY KEY (atc_code, category)
            ) WITHOUT ROWID
        ;
CREATE TABLE atc_family_stats (
                atc_code           TEXT PRIMARY KEY,
                name_en            TEXT,
                parent_l4          TEXT,
                parent_l3          TEXT,
                parent_l2          TEXT,
                parent_l1          TEXT,
                n_drugs_ddinter    INTEGER DEFAULT 0,
                n_drugs_terminol   INTEGER DEFAULT 0,
                top_brands         TEXT,
                eml_status         TEXT,
                aware_category     TEXT,
                avoid_if_allergic  TEXT
            ) WITHOUT ROWID
        ;
CREATE INDEX idx_tc_atc      ON terminology_codes(atc_code)        WHERE atc_code IS NOT NULL;
CREATE INDEX idx_tc_atc_src  ON terminology_codes(atc_source)      WHERE atc_source IS NOT NULL;
CREATE INDEX idx_tc_rxnorm   ON terminology_codes(rxnorm_cui)      WHERE rxnorm_cui IS NOT NULL;
CREATE INDEX idx_tc_snomed   ON terminology_codes(snomed_code)     WHERE snomed_code IS NOT NULL;
CREATE INDEX idx_tc_ips      ON terminology_codes(ips_validated)   WHERE ips_validated = 1;
CREATE INDEX idx_tc_category ON terminology_codes(category)        WHERE category IS NOT NULL;
CREATE INDEX idx_atc_parent ON atc_hierarchy(parent_atc);
CREATE INDEX idx_atc_level  ON atc_hierarchy(level);
CREATE INDEX idx_dose_atc ON dosages(drug_atc);
CREATE INDEX idx_dd_atc        ON ddinter_drugs(primary_atc);
CREATE INDEX idx_dd_name_lower ON ddinter_drugs(name COLLATE NOCASE);
CREATE INDEX idx_dd_cas        ON ddinter_drugs(cas) WHERE cas IS NOT NULL AND cas != '-';
CREATE INDEX idx_ddf_a   ON ddi_facts(drug_a_ddinter_id);
CREATE INDEX idx_ddf_b   ON ddi_facts(drug_b_ddinter_id);
CREATE INDEX idx_ddf_sev ON ddi_facts(severity);
CREATE INDEX idx_dap_pair ON ddi_atc_pairs(drug_a_atc, drug_b_atc);
CREATE INDEX idx_dap_fact ON ddi_atc_pairs(fact_id);
CREATE INDEX idx_ddim_syn  ON ddi_mechanism_flags(synergistic_effect)  WHERE synergistic_effect = 1;
CREATE INDEX idx_ddim_ant  ON ddi_mechanism_flags(antagonistic_effect) WHERE antagonistic_effect = 1;
CREATE INDEX idx_ddim_met  ON ddi_mechanism_flags(metabolism)          WHERE metabolism = 1;
CREATE INDEX idx_ddim_abs  ON ddi_mechanism_flags(absorption)          WHERE absorption = 1;
CREATE INDEX idx_ddialt_for     ON ddi_alternatives(for_ddinter_id);
CREATE INDEX idx_ddialt_alt     ON ddi_alternatives(alternative_ddinter_id);
CREATE INDEX idx_ddialt_class   ON ddi_alternatives(alternative_atc_class);
CREATE INDEX idx_ddialt_fact    ON ddi_alternatives(fact_id);
CREATE INDEX idx_if_drug_atc ON interactions_food(drug_atc);
CREATE INDEX idx_if_food     ON interactions_food(food_name_en);
CREATE INDEX idx_dd_drug ON drug_disease_interactions(drug_atc);
CREATE INDEX idx_dd_dis  ON drug_disease_interactions(disease_name_en);
CREATE INDEX idx_td_class ON therapeutic_duplications(pharma_class);
CREATE INDEX idx_st_tui ON semantic_types(tui);
CREATE INDEX idx_st_stn ON semantic_types(stn) WHERE stn IS NOT NULL;
CREATE INDEX idx_cr_cui1 ON concept_relations(cui1, rela);
CREATE INDEX idx_cr_cui2 ON concept_relations(cui2, rela);
CREATE INDEX idx_cr_rg   ON concept_relations(rg) WHERE rg IS NOT NULL AND rg != '';
CREATE INDEX idx_da_cui_atn ON drug_attributes(cui, atn);
CREATE INDEX idx_rxc_rxcui    ON rxnorm_concepts(rxcui);
CREATE INDEX idx_rxc_tty      ON rxnorm_concepts(tty);
CREATE INDEX idx_rxc_str_nc   ON rxnorm_concepts(str COLLATE NOCASE);
CREATE INDEX idx_rxc_brand    ON rxnorm_concepts(str COLLATE NOCASE) WHERE tty = 'BN';
CREATE INDEX idx_rxr_rxcui1 ON rxnorm_relations(rxcui1, rela);
CREATE INDEX idx_rxr_rxcui2 ON rxnorm_relations(rxcui2, rela);
CREATE INDEX idx_rxsty_tui ON rxnorm_semantic_types(tui);
CREATE INDEX idx_wi_lang ON word_index(lang, word);
CREATE INDEX idx_sub_rxcui    ON substance_unii_bridge(rxcui);
CREATE INDEX idx_sub_atc      ON substance_unii_bridge(atc_code);
CREATE INDEX idx_sub_snomed   ON substance_unii_bridge(snomed_code);
CREATE INDEX idx_sub_class    ON substance_unii_bridge(allergen_class);
CREATE INDEX idx_sub_cas      ON substance_unii_bridge(cas_rn)        WHERE cas_rn IS NOT NULL;
CREATE INDEX idx_sub_ncit     ON substance_unii_bridge(ncit_code)     WHERE ncit_code IS NOT NULL;
CREATE INDEX idx_sub_pubchem  ON substance_unii_bridge(pubchem_cid)   WHERE pubchem_cid IS NOT NULL;
CREATE INDEX idx_sub_dailymed ON substance_unii_bridge(dailymed_setid) WHERE dailymed_setid IS NOT NULL;
CREATE INDEX idx_ifr_resource ON ips_field_rules(resource, required);
CREATE INDEX idx_ifr_binding  ON ips_field_rules(binding_vs);
CREATE INDEX idx_ivs_vs   ON ips_valuesets(vs_id);
CREATE INDEX idx_ivs_code ON ips_valuesets(code);
CREATE INDEX idx_ivst_lang ON ips_valuesets_translations(lang);
CREATE INDEX idx_ivst_code ON ips_valuesets_translations(code, lang);
CREATE INDEX idx_icm_source ON ips_concept_maps(source_system, source_code);
CREATE INDEX idx_icm_target ON ips_concept_maps(target_system, target_code);
CREATE INDEX idx_axr_allergen ON allergy_cross_reactivity(allergen_class);
CREATE INDEX idx_axr_cross    ON allergy_cross_reactivity(cross_reactive_class);
CREATE INDEX idx_axr_risk     ON allergy_cross_reactivity(risk_level);
CREATE INDEX idx_aca_source     ON atc_alternatives(source_atc);
CREATE INDEX idx_aca_alt        ON atc_alternatives(alternative_atc);
CREATE INDEX idx_aca_score      ON atc_alternatives(source_atc, similarity_score DESC);
CREATE INDEX idx_aca_relation   ON atc_alternatives(relation_type);
CREATE INDEX idx_aca_eml        ON atc_alternatives(source_atc, eml_priority) WHERE eml_priority = 1;
CREATE INDEX idx_aca_aware      ON atc_alternatives(source_atc, aware_category) WHERE aware_category IS NOT NULL;
CREATE INDEX idx_aca_avoid      ON atc_alternatives(source_atc) WHERE avoid_if_allergic_to IS NULL;
CREATE INDEX idx_ddialt_pair    ON ddi_alternatives(for_ddinter_id, alternative_ddinter_id);
CREATE INDEX idx_tc_atc_source  ON terminology_codes(atc_code, atc_source) WHERE atc_code IS NOT NULL;
CREATE INDEX idx_tc_rxnorm_atc  ON terminology_codes(rxnorm_cui, atc_code) WHERE rxnorm_cui IS NOT NULL AND atc_code IS NOT NULL;
CREATE INDEX idx_dnm_atc ON drug_names_meta(atc_code);
CREATE INDEX idx_dnm_dd  ON drug_names_meta(ddinter_id) WHERE ddinter_id IS NOT NULL;
CREATE INDEX idx_aci_l3 ON atc_combinations_index(l3_family);
CREATE INDEX idx_aci_l4 ON atc_combinations_index(l4_family);
CREATE INDEX idx_aci_ing ON atc_combinations_index(ingredient_lc);
CREATE INDEX idx_aci_atc ON atc_combinations_index(atc_code);
CREATE INDEX idx_tc_combo ON terminology_codes(is_combo) WHERE is_combo=1;
CREATE INDEX idx_cdr_rxcui ON combo_drug_resolutions(rxcui);
CREATE INDEX idx_cdr_atc ON combo_drug_resolutions(primary_atc);
CREATE INDEX idx_eml_section ON who_eml_2023(eml_section);
CREATE INDEX idx_aware_cat ON who_aware_2024(category);
CREATE INDEX idx_afs_l4 ON atc_family_stats(parent_l4);
CREATE INDEX idx_afs_l3 ON atc_family_stats(parent_l3);
CREATE INDEX idx_afs_eml ON atc_family_stats(eml_status) WHERE eml_status IS NOT NULL;
CREATE INDEX idx_afs_aware ON atc_family_stats(aware_category) WHERE aware_category IS NOT NULL;
CREATE VIRTUAL TABLE terminology_latin USING fts5(
        code      UNINDEXED,
        lang      UNINDEXED,
        display,
        tokenize  = "unicode61 remove_diacritics 2"
    );
CREATE VIRTUAL TABLE terminology_cjk USING fts5(
        code      UNINDEXED,
        lang      UNINDEXED,
        display,
        tokenize  = "trigram case_sensitive 0"
    );
CREATE VIEW v_interactions_drug AS
    SELECT
        p.drug_a_atc, p.drug_b_atc,
        f.fact_id, f.severity, f.mechanism_category,
        f.description_en, f.management_en, f.alternative_atc,
        f.drug_a_ddinter_id, f.drug_b_ddinter_id, f.source
    FROM   ddi_atc_pairs p
    JOIN   ddi_facts f USING (fact_id)
/* v_interactions_drug(drug_a_atc,drug_b_atc,fact_id,severity,mechanism_category,description_en,management_en,alternative_atc,drug_a_ddinter_id,drug_b_ddinter_id,source) */;
CREATE VIEW v_ddi_emergency AS
    SELECT  p.drug_a_atc, p.drug_b_atc, f.severity, f.mechanism_category,
            f.description_en, f.management_en, f.alternative_atc
    FROM    ddi_atc_pairs p
    JOIN    ddi_facts f USING (fact_id)
    WHERE   f.severity IN ('Major', 'Moderate')
/* v_ddi_emergency(drug_a_atc,drug_b_atc,severity,mechanism_category,description_en,management_en,alternative_atc) */;
CREATE VIEW v_safe_alternatives AS
    SELECT
        da.for_ddinter_id        AS unsafe_drug,
        da.alternative_ddinter_id AS safe_drug,
        da.alternative_atc_class  AS atc_class,
        alt.name                  AS alt_name,
        alt.primary_atc           AS alt_primary_atc,
        f.severity                AS source_severity,
        f.description_en          AS source_interaction
    FROM ddi_alternatives da
    JOIN ddi_facts f          ON f.fact_id = da.fact_id
    JOIN ddinter_drugs alt    ON alt.ddinter_id = da.alternative_ddinter_id
/* v_safe_alternatives(unsafe_drug,safe_drug,atc_class,alt_name,alt_primary_atc,source_severity,source_interaction) */;
CREATE VIRTUAL TABLE drug_names_multilingual USING fts5(
        code       UNINDEXED,
        atc_code   UNINDEXED,
        lang       UNINDEXED,
        display,
        tokenize  = "unicode61 remove_diacritics 2"
    );
CREATE VIRTUAL TABLE drug_names_cjk USING fts5(
        code       UNINDEXED,
        atc_code   UNINDEXED,
        lang       UNINDEXED,
        display,
        tokenize  = "trigram case_sensitive 0"
    );
