--
-- Select only targets with activity.
--
SELECT DISTINCT
	td.id "target_id",
	td.name,
	td.target_class,
	td.protein_type,
	tc.accession AS "uniprot",
	tc.swissprot,
	tc.organism,
	tc.gene,
	tc.geneid,
	tc.name
FROM
	public.target_dictionary AS td,
	public.target_component AS tc,
	public.td2tc AS td2tc,
	public.act_table_full AS a
WHERE
	td.id = td2tc.target_id
	AND tc.id = td2tc.component_id
	AND a.target_id = td.id
ORDER BY
	td.id
	;
--
