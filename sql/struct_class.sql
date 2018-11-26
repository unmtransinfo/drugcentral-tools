--
SELECT DISTINCT
	s2a.struct_id,
	pa.type AS "class_type",
	pa.name AS "class_name",
	pa.class_code
FROM
	struct2atc s2a
JOIN
	pharma_class pa ON pa.struct_id = s2a.struct_id
ORDER BY
	s2a.struct_id
	;
--
