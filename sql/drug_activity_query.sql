SELECT DISTINCT
	a.struct_id "cid",
	s.name "struct_name",
	a.act_id,
	a.act_type,
	a.act_value,
	a.act_unit,
	a.act_source,
	a.relation,
	a.moa,
	a.moa_source,
	a.moa_source2,
	a.ref_id,
	a.moa_ref_id,
	a.action_type,
	td.id "tid",
	td.name "target_name",
	td.target_class,
	td.protein_type,
	tc.accession,
	tc.swissprot,
	tc.organism,
	tc.gene "genesymbol",
	tc.geneid,
	tc.name "component_name"
FROM
	public.structures AS s
LEFT OUTER JOIN
	public.activities AS a ON s.id = a.struct_id
JOIN
	public.target_dictionary AS td ON a.target_id = td.id
JOIN
	public.td2tc AS td2tc ON td.id = td2tc.target_id
JOIN
	public.target_component AS tc ON tc.id = td2tc.component_id
WHERE
	UPPER(s.name) LIKE '%TAMOXIFEN%'
	;
