SELECT count(*) AS total_vaccines FROM ips_valuesets WHERE vs_id = 'vaccines-snomed-ct-ips-free-set';
SELECT lang, count(*) AS translated_count FROM ips_valuesets_translations WHERE vs_id = 'vaccines-snomed-ct-ips-free-set' GROUP BY lang ORDER BY translated_count DESC;
