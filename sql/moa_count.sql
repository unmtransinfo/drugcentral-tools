--
SELECT
	COUNT(a.moa) moa_type_count,
	a.action_type
FROM
	act_table_full AS a
WHERE
	a.moa IS NOT NULL
GROUP BY
	a.action_type
ORDER BY
	moa_type_count DESC
	;
--
SELECT
	COUNT(a.moa) moa_source_count,
	a.moa_source
FROM
	act_table_full AS a
WHERE
	a.moa IS NOT NULL
GROUP BY
	a.moa_source
ORDER BY
	moa_source_count DESC
	;
--
