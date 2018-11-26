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
	m.mol @> 'C12CCCC1CCC1C2CCC2=CCCCC12'
	;
--
