SELECT
	COUNT(DISTINCT s.id) "structure_count"
FROM
	public.structures AS s
	;
--
SELECT
	COUNT(DISTINCT s.id) "active_ingredient_structure_count"
FROM
	public.active_ingredient AS a,
	public.structures AS s
WHERE
	a.struct_id = s.id
	;
--
SELECT
	p.product_name,
	COUNT(DISTINCT s.id) "active_ingredient_count"
FROM
	public.product AS p,
	public.active_ingredient AS a,
	public.structures AS s
WHERE
	UPPER(p.product_name) LIKE '%ASPIRIN%'
	AND p.ndc_product_code = a.ndc_product_code
	AND a.struct_id = s.id
GROUP BY
	p.product_name
ORDER BY
	active_ingredient_count DESC
	;
--
-- UPPER(p.product_name) LIKE '%LIPITOR%'
--
