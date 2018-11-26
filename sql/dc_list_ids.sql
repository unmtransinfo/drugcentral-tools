-- ------------------------------------------------------------------------
-- List of all drugs with some standard IDs.
-- ------------------------------------------------------------------------
--
-- 
SELECT DISTINCT
	s.id AS "drugcentral_id",
	s.cas_reg_no,
	s.name,
	id.identifier AS "pubchem_cid",
	ai.active_moiety_unii AS "unii",
	apv.approval "approval_date",
	apv.type "approval_type",
	apv.applicant "approval_applicant"
FROM
	structures s
JOIN
	identifier id ON s.id = id.struct_id
JOIN
	active_ingredient AS ai ON s.id = ai.struct_id
JOIN
	approval apv ON apv.struct_id = s.id
WHERE
	UPPER(id.id_type) = 'PUBCHEM_CID'
ORDER BY
	s.name
	;
--
-- JOIN product p ON p.ndc_product_code = ai.ndc_product_code
-- p.generic_name,
-- p.product_name
