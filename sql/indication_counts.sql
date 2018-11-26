--
SELECT
	COUNT(t.struct_id) AS "struct_count",
	t.umls_cui_count AS "umls_cui_count_per_struct"
FROM
	(
	SELECT
		s.id AS "struct_id",
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
		s.id
	) t
	GROUP BY
	t.umls_cui_count
ORDER BY
	t.umls_cui_count DESC
	;
--
