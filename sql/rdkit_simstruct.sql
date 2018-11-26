--
--Similarity search:
--
SELECT
	tanimoto_sml(rdkit_fp(mol_from_smiles('NCCc1ccc(O)c(O)c1'::cstring)),m.fp) AS "sim",
	m.id,
	s.name,
	mol_to_smiles(m.mol) AS "smiles"
FROM
	public.mols m
JOIN
	public.structures s ON (s.id = m.id)
WHERE
	rdkit_fp(mol_from_smiles('NCCc1ccc(O)c(O)c1'::cstring))%m.fp
ORDER BY
	sim DESC
	;
--
