--
SELECT
	s.id AS "struct_id",
	s.name AS "struct_name",
	COUNT(sno.umls_cui) AS "umls_cui_count"
FROM
	struct2snomed s2sno,
	snomedct sno,
	structures s
WHERE
	s2sno.struct_id = s.id
	AND s2sno.concept_id = sno.concept_id
	AND s2sno.relation = 'indication'
GROUP BY
	s.id, s.name
ORDER BY
	umls_cui_count DESC
LIMIT 100
	;
--
