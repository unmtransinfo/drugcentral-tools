CREATE OR REPLACE FUNCTION
	rdk_simsearch(smiles TEXT)
RETURNS TABLE(id INTEGER, mol MOL, similarity DOUBLE PRECISION) AS
	$$
	SELECT
		id,
		mol,
		tanimoto_sml(rdkit_fp(mol_from_smiles($1::cstring)), fp) AS similarity
	FROM
		public.mols
	WHERE
		rdkit_fp(mol_from_smiles($1::cstring))%fp
	ORDER BY
		rdkit_fp(mol_from_smiles($1::cstring))<%>fp
		;
	$$
LANGUAGE SQL STABLE
	;
