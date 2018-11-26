-- Dump structures with ATCs.
-- s2a.atc_code,
--
SELECT DISTINCT
	s2a.struct_id,
	atc.l4_name
FROM
	struct2atc s2a
JOIN
	atc ON atc.code = s2a.atc_code
ORDER BY
	s2a.struct_id
	;
--
