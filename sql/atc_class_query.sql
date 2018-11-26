SELECT
	s.name,
	atc.chemical_substance,
	atc.code atc_code,
	atc.l1_name atc_l1_name,
	atc.l2_name atc_l2_name,
	atc.l3_name atc_l3_name,
	atc.l4_name atc_l4_name
FROM
	atc,
	struct2atc,
	structures s
WHERE
	atc.code = struct2atc.atc_code
	AND struct2atc.struct_id = s.id
	AND l3_name ILIKE '%BLOOD GLUCOSE LOWERING%'
	AND atc.chemical_substance_count = 1
ORDER BY
	s.name
	;
--
