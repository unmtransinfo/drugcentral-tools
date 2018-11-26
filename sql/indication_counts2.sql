--
SELECT
	COUNT(t.struct_id) AS "struct_count",
	CASE
	WHEN t.umls_cui_count = 1 THEN '001'
	WHEN t.umls_cui_count = 2 THEN '002'
	WHEN t.umls_cui_count = 3 THEN '003'
	WHEN t.umls_cui_count = 4 THEN '004'
	WHEN t.umls_cui_count = 5 THEN '005'
	WHEN t.umls_cui_count BETWEEN 6 and 10 THEN '006-010'
	WHEN t.umls_cui_count BETWEEN 11 and 49 THEN '011-049'
	WHEN t.umls_cui_count BETWEEN 50 and 99 THEN '050-099'
	WHEN t.umls_cui_count >= 100 THEN '100+'
	ELSE 'Unknown'
	END AS umls_cui_count_range
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
	umls_cui_count_range
ORDER BY
	umls_cui_count_range
	;
--
