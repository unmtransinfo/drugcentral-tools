--
SELECT
	COUNT(t.struct_id) AS "struct_count",
	t.umls_cui_count AS "umls_cui_count_per_struct"
FROM
	(SELECT
		s.id AS "struct_id",
		COUNT(omop.umls_cui) AS "umls_cui_count"
	FROM
		omop_relationship omop,
		structures s
	WHERE
		omop.struct_id = s.id
		AND omop.relationship_name = 'indication'
	GROUP BY
		s.id
	) t
GROUP BY
	t.umls_cui_count
ORDER BY
	t.umls_cui_count DESC
	;
--
