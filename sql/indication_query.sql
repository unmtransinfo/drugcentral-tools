--
SELECT
	s.name,
	omop.concept_name,
	omop.umls_cui
FROM
	omop_relationship omop,
	structures s
WHERE
	omop.struct_id = s.id
	AND omop.relationship_name = 'indication'
	AND RANDOM() < 0.01
LIMIT 100
	;
--
-- ORDER BY s.name
