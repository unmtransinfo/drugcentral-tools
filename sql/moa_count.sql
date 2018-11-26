--
SELECT
	COUNT(a.moa)
FROM
	activities AS a,
	action_type moat
WHERE
	a.moa = moat.id
	AND a.moa IS NOT NULL
	;
--
SELECT DISTINCT
	COUNT(a.moa),
	moat.action_type
FROM
	activities AS a,
	action_type moat
WHERE
	a.moa = moat.id
	AND a.moa IS NOT NULL
GROUP BY
	moat.action_type
	;
--
