--
-- RDKit version
--
--Exact structure search:
SELECT
	m.id,
	s.name,
	mol_to_smiles(m.mol) AS "smiles"
FROM
	public.mols m
JOIN
	public.structures s ON (s.id = m.id)
WHERE
	m.mol @= 'COC(=O)c1ccccc1O'
	;
--
--Substructure search:
SELECT
	m.id,
	s.name,
	mol_to_smiles(m.mol) AS "smiles"
FROM
	public.mols m
JOIN
	public.structures s ON (s.id = m.id)
WHERE
	m.mol @> 'COC(=O)c1ccccc1O'
	;
--
--Substructure Smarts search:
SELECT
	m.id,
	s.name,
	mol_to_smiles(m.mol) AS "smiles"
FROM
	public.mols m
JOIN
	public.structures s ON (s.id = m.id)
WHERE
	m.mol @> 'c1[o,s]ncn1'::qmol
	;
--
--
--Fingerprint similarity search, using cartridge only:
SET rdkit.tanimoto_threshold=0.5;
SELECT
	m.id,
	mol_to_smiles(m.mol) AS "smiles",
	TO_CHAR(tanimoto_sml(morganbv_fp(mol_from_smiles('COC(=O)c1ccccc1O'::cstring)),m.mfp),'0.99') AS sim_mfp
FROM
	public.mols m
WHERE
	morganbv_fp(mol_from_smiles('COC(=O)c1ccccc1O'::cstring))%m.mfp
ORDER BY
	morganbv_fp(mol_from_smiles('COC(=O)c1ccccc1O'::cstring))<%>m.mfp
	;
--
SET rdkit.tanimoto_threshold=0.6;
SELECT
	m.id,
	mol_to_smiles(m.mol) AS "smiles",
	TO_CHAR(tanimoto_sml(rdkit_fp(mol_from_smiles('COC(=O)c1ccccc1O'::cstring)),m.fp),'0.99') AS sim
FROM
	public.mols m
WHERE
	rdkit_fp(mol_from_smiles('COC(=O)c1ccccc1O'::cstring))%m.fp
ORDER BY
	rdkit_fp(mol_from_smiles('COC(=O)c1ccccc1O'::cstring))<%>m.fp
	;
--
