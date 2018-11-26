--
--Exact structure search:
--NOTE: @= operator does not work?  But = does.
--
--	m.mol @= 'NCCc1ccc(O)c(O)c1'
--	m.mol @= 'CNC[C@H](O)c1ccc(O)c(O)c1'
--
SELECT
	m.id,
	s.name,
	mol_to_smiles(m.mol) AS "smiles"
FROM
	public.mols m
JOIN
	public.structures s ON (s.id = m.id)
WHERE
	m.mol = 'NCCc1ccc(O)c(O)c1'
	;
--
