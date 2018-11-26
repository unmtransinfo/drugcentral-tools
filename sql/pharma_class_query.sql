SELECT
	pc.type class_type,
	pc.name class_name,
	pc.class_code,
	pc.source class_source,
	pc.struct_id,
	s.name,
	s.smiles
FROM
	pharma_class pc
JOIN
	structures s ON s.id = pc.struct_id
WHERE
	pc.name ILIKE '%sulfonylurea%'
	AND pc.type = 'Chemical/Ingredient'
ORDER BY
	s.name
	;
--
