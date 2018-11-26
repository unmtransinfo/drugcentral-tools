--
SELECT DISTINCT
	s.id,
	st.type
FROM
	structures s
JOIN
	structure_type st ON st.struct_id = s.id
ORDER BY
	s.id
	;
--
