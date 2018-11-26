--
-- RDKit version
--
SELECT DISTINCT
	TO_CHAR(m.id,'FM999999')||','
	||'"'||mol_to_smiles(m.mol)||'",'
	||'"'||(CASE WHEN s.name IS NULL THEN '' ELSE s.name END)||'",'
	||'"'||(CASE WHEN s.cas_reg_no IS NULL THEN '' ELSE s.cas_reg_no END)||'",'
	||(CASE WHEN s.clogp IS NULL THEN '' ELSE TO_CHAR(s.clogp,'FM999999.99') END)||','
	||(CASE WHEN s.alogs IS NULL THEN '' ELSE TO_CHAR(s.alogs,'FM999999.99') END)||','
	||(CASE WHEN s.tpsa IS NULL THEN '' ELSE TO_CHAR(s.tpsa,'FM999999') END)||','
FROM
	public.structures s
JOIN
	public.mols m ON (s.id = m.id)
	;
--
