SELECT DISTINCT
	a.moa,
	a.relation,
	a.struct_id,
	a.target_id,
	moat.action_type
FROM
	public.activities AS a,
	public.action_type moat
WHERE
	a.moa = moat.id
	AND a.moa IS NOT NULL
	;
--
SELECT DISTINCT
	a.moa,
	moat.action_type,
	a.struct_id,
	s.name,
	a.relation,
	a.act_type,
	td.id "target_id",
	td.name,
	td.target_class,
	td.protein_type,
	tc.accession,
	tc.name
FROM
	public.structures AS s,
	public.activities AS a,
	public.target_dictionary AS td,
	public.target_component AS tc,
	public.td2tc AS td2tc,
	public.action_type moat
WHERE
	s.id = a.struct_id
	AND a.target_id = td.id
	AND td.id = td2tc.target_id
	AND tc.id = td2tc.component_id
	AND a.moa = moat.id
	AND a.moa IS NOT NULL
ORDER BY
	a.moa
	;
--
