SELECT DISTINCT
	p.id,
	p.product_name,
	p.generic_name,
	s.id "struct_id",
	s.name,
	s.cd_smiles
FROM
	public.product AS p,
	public.active_ingredient AS a,
	public.structures AS s
WHERE
	UPPER(p.product_name) LIKE '%ASPIRIN%'
	AND p.ndc_product_code = a.ndc_product_code
	AND a.struct_id = s.id
	;
--
-- UPPER(p.product_name) LIKE '%LIPITOR%'
--
SELECT
	p.id,
	p.ndc_product_code,
	p.form,
	p.generic_name,
	p.product_name,
	p.route,
	p.marketing_status,
	p.active_ingredient_count,
	ai.active_moiety_name,
	ai.substance_name,
	s.cas_reg_no,
	s.cd_smiles,
	s.cd_structure,
	n.name "synonym"
FROM
	public.product AS p,
	public.active_ingredient AS ai,
	public.structures AS s,
	public.synonyms AS n
WHERE 
	p.ndc_product_code = ai.ndc_product_code
	AND ai.struct_id = s.id
	AND n.id = s.id
	AND UPPER(p.product_name) LIKE '%LIPITOR%'
	;
--
--
SELECT
	p.id,
	p.ndc_product_code,
	p.form,
	p.generic_name,
	p.product_name,
	p.route,
	p.marketing_status,
	p.active_ingredient_count,
	ai.active_moiety_name,
	ai.substance_name,
	s.cas_reg_no,
	s.cd_smiles,
	s.cd_structure,
	n.name "synonym"
FROM
	public.product AS p,
	public.active_ingredient AS ai,
	public.structures AS s,
	public.synonyms AS n
WHERE 
	p.ndc_product_code = ai.ndc_product_code
	AND ai.struct_id = s.id
	AND n.id = s.id
	AND p.id = 1092986
	;
--
