--
SELECT
	omop.relationship_name,
	COUNT(omop.umls_cui) AS "umls_cui_count",
	COUNT(omop.struct_id) AS "struct_count"
FROM
	omop_relationship omop
GROUP BY
	omop.relationship_name
	;
--
