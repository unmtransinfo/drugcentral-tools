SELECT DISTINCT
	active_moiety_name,
	substance_name,
	active_moiety_unii,
	substance_unii,
	cd_smiles
FROM
	public.active_ingredient,
	public.structureS
WHERE
	active_moiety_unii IN ( 'U5N7SU872W', 'R16CO5Y76E' )
	AND active_ingredient.struct_id = structures.id
	;
