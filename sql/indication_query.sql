--
SELECT
	s.name,
	sno.concept_name,
	sno.umls_cui
FROM
	struct2snomed s2sno,
	snomedct sno,
	structures s
WHERE
	s2sno.struct_id = s.id
	AND s2sno.concept_id = sno.concept_id
	AND s2sno.relation = 'indication'
	AND RANDOM() < 0.01
LIMIT 100
	;
--
-- ORDER BY s.name
