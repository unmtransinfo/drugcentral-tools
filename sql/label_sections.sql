--
SELECT
	COUNT(id) AS "section_count",
	title
FROM
	section
GROUP BY
	title
ORDER BY
	title
	;
--
