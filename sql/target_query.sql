SELECT DISTINCT
	a.struct_id,
	s.name,
	a.relation,
	a.act_type,
	a.act_value,
	a.act_unit,
	td.id "target_id",
	td.name,
	td.target_class,
	td.protein_type,
	tc.accession,
	tc.swissprot,
	tc.organism,
	tc.gene,
	tc.geneid,
	tc.name
FROM
	public.structures AS s,
	public.activities AS a,
	public.target_dictionary AS td,
	public.target_component AS tc,
	public.td2tc AS td2tc
WHERE
	s.id = a.struct_iD
	AND a.target_id = td.id
	AND td.id = td2tc.target_id
	AND tc.id = td2tc.component_id
	AND UPPER(s.name) LIKE '%TAMOXIFEN%'
	;
