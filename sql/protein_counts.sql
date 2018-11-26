--
SELECT
	td.protein_type,
	COUNT(DISTINCT tc.accession) AS "nproteins"
FROM
	target_component tc,
	target_dictionary td,
	td2tc
WHERE
	td.id = td2tc.target_id
	AND tc.id = td2tc.component_id
GROUP BY
	td.protein_type
	;
--
SELECT
	t.pcount AS "proteins_per_target",
	COUNT(t.id) AS "target_count"
FROM
	(
	SELECT
		COUNT(DISTINCT tc.accession) AS "pcount",
		td.id AS "id"
	FROM
		target_component tc,
		target_dictionary td,
		td2tc
	WHERE
		td.id = td2tc.target_id
		AND tc.id = td2tc.component_id
		AND td.protein_type LIKE 'PROTEIN COMPLEX%'
	GROUP BY
		td.id
	) t
GROUP BY
	t.pcount
ORDER BY
	t.pcount
	;
--
