SELECT lang, count(*) AS count FROM ips_valuesets_translations GROUP BY lang ORDER BY count DESC;
