SELECT '260548002' AS query_code, 'Oral route' AS description, count(*) AS in_ips_valuesets FROM ips_valuesets WHERE code = '260548002';
SELECT code, snomed_code, primary_display FROM terminology_codes 
WHERE snomed_code IN ('260548002', '26643006', '447694001', '47625008', '78421000', '37839007', '34206005', '6064005') 
   OR code IN ('260548002', '26643006', '447694001', '47625008', '78421000', '37839007', '34206005', '6064005')
ORDER BY primary_display;
