--
SELECT DISTINCT
	REPLACE(p.ndc_product_code,'-','')::CHAR(8) AS "ndc",
	p.id AS "product_id",
	p.product_name,
	p.generic_name,
	p.form,
	p.route,
	p.marketing_status,
	s2a.atc_code
FROM
	product AS p
JOIN
	prd2label p2l ON p.ndc_product_code = p2l.ndc_product_code
JOIN
	label l ON l.id = p2l.label_id
JOIN
	active_ingredient ai ON ai.ndc_product_code = p.ndc_product_code
JOIN
	struct2atc s2a ON s2a.struct_id = ai.struct_id
WHERE
	p.marketing_status IN ('NDA','ANDA')
	AND l.category LIKE '%HUMAN PRESCRIPTION%'
	AND p.active_ingredient_count = 1
ORDER BY
	REPLACE(p.ndc_product_code,'-','')::CHAR(8)
	;
--
--	AND s2a.atc_code LIKE 'L01%'
