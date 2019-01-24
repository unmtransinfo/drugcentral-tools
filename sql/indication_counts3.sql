--
SELECT
	s.id AS "struct_id",
	s.name AS "struct_name",
	COUNT(omop.umls_cui) AS "umls_cui_count"
FROM
	omop_relationship omop,
	structures s
WHERE
	omop.struct_id = s.id
	AND omop.relationship_name = 'indication'
GROUP BY
	s.id, s.name
ORDER BY
	umls_cui_count DESC
LIMIT 100
	;
--
