--
SELECT
	COUNT(DISTINCT td.id) "target_count"
FROM
	public.target_dictionary AS td
	;
--
SELECT
	td.id,
	td.name,
	COUNT(tc.id) "component_count"
FROM
	public.target_dictionary AS td,
	public.target_component AS tc,
	public.td2tc AS td2tc
WHERE
	td.id = td2tc.target_id
	AND tc.id = td2tc.component_id
GROUP BY td.id,td.name
ORDER BY component_count DESC
	;
--
SELECT
	COUNT(DISTINCT tc.gene) "genesymbol_count"
FROM
	public.target_component AS tc
	;
--
--
-- SELECT COUNT(DISTINCT moa.target_id) "moa_target_count" FROM public.act_moa_freeze AS moa ;
-- SELECT COUNT(DISTINCT moa.struct_id) "moa_struct_count" FROM public.act_moa_freeze AS moa ;
--
