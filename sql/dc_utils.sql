-- ------------------------------------------------------------------------
-- These queries are from templates in dc_utils.java (JJY), used in the alpha DrugCentral webapp,
-- with example values added.
-- ------------------------------------------------------------------------
--
SELECT COUNT(id) FROM public.structures;
SELECT COUNT(id) FROM public.product;
SELECT COUNT(id) FROM public.active_ingredient;
SELECT COUNT(id) FROM public.target_dictionary;
SELECT COUNT(act_id) FROM public.activities;
SELECT COUNT(syn_id) FROM public.synonyms;
SELECT COUNT(id) FROM public.reference;
--
-- RDKit is the chemical cartridge.
SELECT rdkit_version();
--
-- ------------------------------------------------------------------------
-- COMPOUND queries (all return same fields):
-- ------------------------------------------------------------------------
--
-- SearchCompoundsByStructureName(qstr)
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	WHERE UPPER(s.name) = UPPER('statin') ;
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	WHERE UPPER(s.name) LIKE '%'||UPPER('statin')||'%' ;
--
-- SearchCompoundsByIngredientName(qstr)
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	JOIN public.active_ingredient AS ai ON s.id = ai.struct_id
	WHERE UPPER(ai.substance_name) = UPPER('statin') ;
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	JOIN public.active_ingredient AS ai ON s.id = ai.struct_id
	WHERE UPPER(ai.substance_name) LIKE '%'||UPPER('statin')||'%' ;
--
-- SearchCompoundsBySynonym(qstr)
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	JOIN public.synonyms AS syn ON s.id = syn.id
	WHERE UPPER(syn.name) =  UPPER('prozac') ;
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	JOIN public.synonyms AS syn ON s.id = syn.id
	WHERE UPPER(syn.name) LIKE  '%'||UPPER('prozac')||'%' ;
--
-- SearchCompoundsByProductName(qstr)
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	JOIN public.active_ingredient AS ai ON s.id = ai.struct_id JOIN public.product AS p ON ai.ndc_product_code = p.ndc_product_code
	WHERE UPPER(p.product_name) = UPPER('prozac')
	AND p.active_ingredient_count = 1 ;
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	JOIN public.active_ingredient AS ai ON s.id = ai.struct_id JOIN public.product AS p ON ai.ndc_product_code = p.ndc_product_code
	WHERE UPPER(p.product_name) LIKE '%'||UPPER('prozac')||'%'
	AND p.active_ingredient_count = 1 ;
--
-- SearchCompoundsByUNII(unii)
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	JOIN public.active_ingredient AS ai ON s.id = ai.struct_id
	WHERE ai.active_moiety_unii = '01K63SUP8D';
--
-- SearchCompoundsByATC(code,level)
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	JOIN public.struct2atc AS s2atc ON s.id = s2atc.struct_id JOIN public.atc AS atc ON atc.code = s2atc.atc_code
	WHERE atc.l4_code = 'N06AB';
--
-- SearchCompoundsByExtID(id,idtype)
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	JOIN public.identifier AS id ON s.id = id.struct_id
	WHERE id.identifier = '3386'
	AND UPPER(id.id_type) = UPPER('pubchem_cid');
--
-- SearchCompoundsByID(List<Integer> ids)
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	WHERE s.id IN ( 2436, 1209, 2068 ) ;
--
-- GetCompoundByID(id)
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	WHERE s.id = 2068 ;
--
-- ------------------------------------------------------------------------
-- Chemical structure searches, requires RDKit cartridge.
-- Search types: sub-structure, full-structure, similar-structure
-- ------------------------------------------------------------------------
--
-- SearchCompoundsByStructure(qtype,qtxt) - sub-structure
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	JOIN public.mols m ON s.id = m.id
	WHERE m.mol @> 'C12CCCC1CCC1C2CCC2=CCCCC12' ;
--
-- SearchCompoundsByStructure(qtype,qtxt) - full-structure
SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	JOIN public.mols m ON s.id = m.id
	WHERE m.mol @= 'C12CCCC1CCC1C2CCC2=CCCCC12' ;
--
-- SearchCompoundsByStructure(qtype,qtxt) - similar-structure
SELECT DISTINCT tanimoto_sml(rdkit_fp(mol_from_smiles('C12CCCC1CCC1C2CCC2=CCCCC12'::cstring)),m.fp) AS "sim",
	s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.structures AS s
	JOIN public.mols m ON s.id = m.id
	WHERE rdkit_fp(mol_from_smiles('C12CCCC1CCC1C2CCC2=CCCCC12'::cstring))%m.fp ;
-- 
-- ------------------------------------------------------------------------
-- Misc
-- ------------------------------------------------------------------------
-- 
-- GetCompoundProductNames(id)
SELECT DISTINCT ai.active_moiety_name, ai.substance_name, p.id "product_id", p.generic_name, p.product_name, p.active_ingredient_count FROM public.active_ingredient AS ai JOIN public.product AS p ON ai.ndc_product_code =
p.ndc_product_code WHERE ai.struct_id = 2068 ;
-- 
-- GetCompoundSynonyms(id)
SELECT DISTINCT syn.name "synonym" FROM public.synonyms AS syn WHERE syn.id = 2406 ;
-- 
-- GetCompoundATCs(int id)
SELECT DISTINCT atc.code "atc_code", atc.chemical_substance, atc.l1_code, atc.l1_name, atc.l2_code, atc.l2_name, atc.l3_code, atc.l3_name, atc.l4_code, atc.l4_name FROM public.atc AS atc JOIN public.struct2atc AS s2atc ON atc.code = s2atc.atc_code WHERE s2atc.struct_id = 2068 ;
-- 
-- GetCompoundIDs(int id)
SELECT DISTINCT identifier "id_val", id_type FROM public.identifier AS id WHERE id.struct_id = 2406 ;
-- 
-- GetCompoundUniis(id)
SELECT DISTINCT active_moiety_unii FROM public.active_ingredient AS ai WHERE ai.struct_id = 2406 ;
-- 
-- GetCompoundApprovals(id)
SELECT DISTINCT apv.approval "approval_date", apv.type "approval_type", apv.applicant "approval_applicant" FROM public.approval AS apv WHERE apv.struct_id = 2406 ;
--
-- ------------------------------------------------------------------------
-- PRODUCT queries (all return same fields):
-- ------------------------------------------------------------------------
--
-- SearchProductsByProductName(qstr)
SELECT p.id, p.ndc_product_code, p.form, p.generic_name, p.product_name, p.route, p.marketing_status, p.active_ingredient_count, ai.active_moiety_name, ai.substance_name, s.cas_reg_no, s.molfile, s.cd_molweight, n.name "synonym" FROM public.product AS p JOIN public.active_ingredient AS ai ON p.ndc_product_code = ai.ndc_product_code JOIN public.structures AS s ON ai.struct_id = s.id JOIN public.synonyms AS n ON n.id = s.id
	WHERE UPPER(p.product_name) = UPPER('crestor') ;
SELECT p.id, p.ndc_product_code, p.form, p.generic_name, p.product_name, p.route, p.marketing_status, p.active_ingredient_count, ai.active_moiety_name, ai.substance_name, s.cas_reg_no, s.molfile, s.cd_molweight, n.name "synonym" FROM public.product AS p JOIN public.active_ingredient AS ai ON p.ndc_product_code = ai.ndc_product_code JOIN public.structures AS s ON ai.struct_id = s.id JOIN public.synonyms AS n ON n.id = s.id
	WHERE UPPER(p.product_name) LIKE '%'||UPPER('crestor')||'%' ;
--
-- SearchProductsByIngredientName(qstr)
SELECT p.id, p.ndc_product_code, p.form, p.generic_name, p.product_name, p.route, p.marketing_status, p.active_ingredient_count, ai.active_moiety_name, ai.substance_name, s.cas_reg_no, s.molfile, s.cd_molweight, n.name "synonym" FROM public.product AS p JOIN public.active_ingredient AS ai ON p.ndc_product_code = ai.ndc_product_code JOIN public.structures AS s ON ai.struct_id = s.id JOIN public.synonyms AS n ON n.id = s.id
	WHERE UPPER(ai.substance_name) = UPPER('crestor') ;
SELECT p.id, p.ndc_product_code, p.form, p.generic_name, p.product_name, p.route, p.marketing_status, p.active_ingredient_count, ai.active_moiety_name, ai.substance_name, s.cas_reg_no, s.molfile, s.cd_molweight, n.name "synonym" FROM public.product AS p JOIN public.active_ingredient AS ai ON p.ndc_product_code = ai.ndc_product_code JOIN public.structures AS s ON ai.struct_id = s.id JOIN public.synonyms AS n ON n.id = s.id
	WHERE UPPER(ai.substance_name) LIKE '%'||UPPER('crestor')||'%' ;
--
-- SearchProductsByUNII(unii)
SELECT p.id, p.ndc_product_code, p.form, p.generic_name, p.product_name, p.route, p.marketing_status, p.active_ingredient_count, ai.active_moiety_name, ai.substance_name, s.cas_reg_no, s.molfile, s.cd_molweight, n.name "synonym" FROM public.product AS p JOIN public.active_ingredient AS ai ON p.ndc_product_code = ai.ndc_product_code JOIN public.structures AS s ON ai.struct_id = s.id JOIN public.synonyms AS n ON n.id = s.id
	WHERE ai.active_moiety_unii = '01K63SUP8D';
--
-- GetProductByID(id)
SELECT p.id, p.ndc_product_code, p.form, p.generic_name, p.product_name, p.route, p.marketing_status, p.active_ingredient_count, ai.active_moiety_name, ai.substance_name, s.cas_reg_no, s.molfile, s.cd_molweight, n.name "synonym" FROM public.product AS p JOIN public.active_ingredient AS ai ON p.ndc_product_code = ai.ndc_product_code JOIN public.structures AS s ON ai.struct_id = s.id JOIN public.synonyms AS n ON n.id = s.id
	WHERE p.id = 1272595 ;
--
-- GetCompoundProducts(id)
SELECT p.id, p.ndc_product_code, p.form, p.generic_name, p.product_name, p.route, p.marketing_status, p.active_ingredient_count, ai.active_moiety_name, ai.substance_name, s.cas_reg_no, s.molfile, s.cd_molweight, n.name "synonym" FROM public.product AS p JOIN public.active_ingredient AS ai ON p.ndc_product_code = ai.ndc_product_code JOIN public.structures AS s ON ai.struct_id = s.id JOIN public.synonyms AS n ON n.id = s.id
	WHERE s.id = 2406 ;
--
-- SearchProductsByCompoundID(List<Integer> ids)
SELECT p.id, p.ndc_product_code, p.form, p.generic_name, p.product_name, p.route, p.marketing_status, p.active_ingredient_count, ai.active_moiety_name, ai.substance_name, s.cas_reg_no, s.molfile, s.cd_molweight, n.name "synonym" FROM public.product AS p JOIN public.active_ingredient AS ai ON p.ndc_product_code = ai.ndc_product_code JOIN public.structures AS s ON ai.struct_id = s.id JOIN public.synonyms AS n ON n.id = s.id
	WHERE s.id IN ( 2436, 1209, 2068 ) ;
--
-- ------------------------------------------------------------------------
-- ACTIVITY - TARGET queries:
-- ------------------------------------------------------------------------
--
-- GetCompoundActivities(cid)
SELECT s.id "cid", a.act_id, a.act_type, a.act_value, a.act_unit, actsrc.source_name "act_source", a.act_comment, 
a.relation, a.moa, moasrc.source_name "moa_source", a.ref_id, a.moa_ref_id, a.action_type "moa_type", 
td.id "tid", td.name "target_name", td.target_class, td.protein_type, tc.id "tcid", 
tc.accession "protein_accession", tc.swissprot, tc.organism, tc.gene "gene_symbol", 
tc.geneid, tc.name "protein_name",
ref.id "ref_id", ref.pmid "ref_pmid", ref.title "ref_title", ref.dp_year "ref_year", 
ref.journal "ref_journal", ref.authors "ref_authors", ref.doi "ref_doi", ref.url "ref_url", 
moaref.id "moa_ref_id", moaref.pmid "moa_ref_pmid", moaref.title "moa_ref_title", moaref.dp_year "moa_ref_year", 
moaref.journal "moa_ref_journal", moaref.authors "moa_ref_authors", moaref.doi "moa_ref_doi", moaref.url "moa_ref_url" 
FROM public.structures AS s 
LEFT OUTER JOIN public.activities AS a ON s.id = a.struct_id 
LEFT OUTER JOIN public.reference AS ref ON a.ref_id = ref.id 
LEFT OUTER JOIN public.reference AS moaref ON a.ref_id = moaref.id 
JOIN public.target_dictionary AS td ON a.target_id = td.id 
JOIN public.td2tc AS td2tc ON td.id = td2tc.target_id 
JOIN public.target_component AS tc ON tc.id = td2tc.component_id 
JOIN public.data_source AS actsrc ON actsrc.src_id = a.act_source 
JOIN public.data_source AS moasrc ON moasrc.src_id = a.act_source 
	WHERE s.id = 1209 ;
-- 
-- 
-- GetCompoundTargetActivities(cid,tid)
SELECT s.id "cid", a.act_id, a.act_type, a.act_value, a.act_unit, actsrc.source_name "act_source", a.act_comment, 
a.relation, a.moa, moasrc.source_name "moa_source", a.ref_id, a.moa_ref_id, a.action_type "moa_type", 
td.id "tid", td.name "target_name", td.target_class, td.protein_type, tc.id "tcid", 
tc.accession "protein_accession", tc.swissprot, tc.organism, tc.gene "gene_symbol", 
tc.geneid, tc.name "protein_name",
ref.id "ref_id", ref.pmid "ref_pmid", ref.title "ref_title", ref.dp_year "ref_year", 
ref.journal "ref_journal", ref.authors "ref_authors", ref.doi "ref_doi", ref.url "ref_url", 
moaref.id "moa_ref_id", moaref.pmid "moa_ref_pmid", moaref.title "moa_ref_title", moaref.dp_year "moa_ref_year", 
moaref.journal "moa_ref_journal", moaref.authors "moa_ref_authors", moaref.doi "moa_ref_doi", moaref.url "moa_ref_url" 
FROM public.structures AS s 
LEFT OUTER JOIN public.activities AS a ON s.id = a.struct_id 
LEFT OUTER JOIN public.reference AS ref ON a.ref_id = ref.id 
LEFT OUTER JOIN public.reference AS moaref ON a.ref_id = moaref.id 
JOIN public.target_dictionary AS td ON a.target_id = td.id 
JOIN public.td2tc AS td2tc ON td.id = td2tc.target_id 
JOIN public.target_component AS tc ON tc.id = td2tc.component_id 
JOIN public.data_source AS actsrc ON actsrc.src_id = a.act_source 
JOIN public.data_source AS moasrc ON moasrc.src_id = a.act_source 
	WHERE s.id = 1209 AND td.id = 110 ;
-- ------------------------------------------------------------------------
-- 
-- GetProductIngredients(id)
SELECT ai.id, ai.active_moiety_unii, ai.active_moiety_name, ai.unit, ai.quantity, ai.substance_unii, ai.substance_name, ai.ndc_product_code, ai.struct_id, ai.quantity_denom_unit, ai.quantity_denom_value, s.molfile, s.cd_molweight, s.cas_reg_no, s.name "struct_name" FROM public.active_ingredient AS ai, public.product p, public.structures s WHERE ai.ndc_product_code = p.ndc_product_code AND ai.struct_id = s.id AND p.id = 1272595 ;

