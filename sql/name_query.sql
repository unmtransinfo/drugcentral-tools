--
SELECT DISTINCT
	ai.struct_id,
	ai.active_moiety_name,
	ai.substance_name
FROM
	public.active_ingredient AS ai
WHERE
	UPPER(ai.substance_name) LIKE '%ASPIRIN%'
ORDER BY
	ai.struct_id
	;
--
SELECT DISTINCT
	ai.struct_id,
	ai.active_moiety_name,
	ai.substance_name,
	s.cas_reg_no,
	s.cd_smiles
FROM
	public.active_ingredient AS ai,
	public.structures AS s
WHERE
	ai.struct_id = s.id
	AND UPPER(ai.substance_name) LIKE '%ASPIRIN%'
ORDER BY
	ai.struct_id
	;
--
SELECT
	s.id,
	SUBSTR(s.name,1,80) "struct_name",
	syn.name "synonym"
FROM
	public.structures AS s,
	public.synonyms AS syn
WHERE
	syn.id = s.id
	AND UPPER(syn.name) LIKE '%ASPIRIN%'
	;
--
SELECT
	s.id,
	SUBSTR(s.name,1,80) "struct_name",
	syn.name "synonym"
FROM
	public.structures AS s,
	public.synonyms AS syn
WHERE
	syn.id = s.id
	AND UPPER(syn.name) LIKE '%KETOROLAC%'
	;
--
