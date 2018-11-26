-- 
SELECT DISTINCT
	s.id AS "struct_id",
	s.name AS "struct_name",
	p2l.label_id
FROM
	product AS p
JOIN
	prd2label p2l ON p.ndc_product_code = p2l.ndc_product_code
JOIN
	label l ON l.id = p2l.label_id
JOIN
	active_ingredient ai ON ai.ndc_product_code = p.ndc_product_code
JOIN
	structures s ON ai.struct_id = s.id
WHERE
	p.marketing_status IN ('NDA','ANDA')
	AND l.category LIKE '%HUMAN PRESCRIPTION%'
	AND p.active_ingredient_count = 1
ORDER BY
	s.id
	;
--
