SELECT count(*) AS fr_non_empty FROM atc_hierarchy WHERE name_fr IS NOT NULL AND trim(name_fr) != '';
SELECT count(*) AS jp_non_empty FROM atc_hierarchy WHERE name_jp IS NOT NULL AND trim(name_jp) != '';
SELECT atc_code, name_en, name_fr, name_jp FROM atc_hierarchy WHERE atc_code IN ('M01AE01', 'N02BA01', 'A12AA04', 'C03CA01');
