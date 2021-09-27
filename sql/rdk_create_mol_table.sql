SELECT
	id,
	mol
INTO
	public.mols
FROM
	(SELECT
		id,
		mol_from_ctab(molfile::cstring) AS mol
	FROM
		public.structures
	) tmp
WHERE
	mol IS NOT NULL
	;
