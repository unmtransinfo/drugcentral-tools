-- ------------------------------------------------------------------------
-- List of all generic names with some standard IDs.
-- ------------------------------------------------------------------------
-- 
SELECT DISTINCT
	p.generic_name,
	ai.active_moiety_unii AS "unii",
	id.identifier AS "pubchem_cid",
	s.cas_reg_no,
	s.smiles,
	s.inchi
FROM
	product p
JOIN
	active_ingredient ai ON p.ndc_product_code = ai.ndc_product_code
JOIN
	structures s ON s.id = ai.struct_id
JOIN
	identifier id ON s.id = id.struct_id
WHERE
	UPPER(id.id_type) = 'PUBCHEM_CID'
ORDER BY
	p.generic_name
	;
--
-- p.ndc_product_code,
-- p.product_name,
