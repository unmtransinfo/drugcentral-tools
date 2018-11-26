#!/bin/sh
#############################################################################
### Problem with rdkit-Release_2016_03_1 and some Boost versions.
### Ok: rdkit-Release_2015_03_1, Boost 1.60, OpenSUSE Tumbleweed (2016)
### Ok: rdkit-Release_2015_03_1, Boost 1.61, OpenSUSE Leap 42.3 (early 2018)
#############################################################################
#
DB="drugcentral"
DBSCHEMA="public"
DBHOST="localhost"
#
sudo -u postgres psql -d $DB -c 'create extension rdkit'
#
### Create mols table for RDKit structural searching.
psql -d $DB <<__EOF__
SELECT
	id,
	mol
INTO
	${DBSCHEMA}.mols
FROM
	(SELECT
		id,
		mol_from_ctab(molfile::cstring) AS mol
	FROM
		${DBSCHEMA}.structures
	) tmp
WHERE
	mol IS NOT NULL
	;
__EOF__
#
#	mol_from_smiles(regexp_replace(cd_smiles,E'\\\\s+.*$','')::cstring) AS mol
#
#
psql -d $DB -c "CREATE INDEX molidx ON ${DBSCHEMA}.mols USING gist(mol)"
#
#
### Add FPs to mols table.
psql -d $DB -c "ALTER TABLE ${DBSCHEMA}.mols ADD COLUMN fp BFP"
psql -d $DB -c "ALTER TABLE ${DBSCHEMA}.mols ADD COLUMN mfp BFP"
psql -d $DB -c "ALTER TABLE ${DBSCHEMA}.mols ADD COLUMN ffp BFP"
psql -d $DB -c "ALTER TABLE ${DBSCHEMA}.mols ADD COLUMN torsionbv BFP"
psql -d $DB -c "UPDATE ${DBSCHEMA}.mols SET fp = rdkit_fp(mol)"
psql -d $DB -c "UPDATE ${DBSCHEMA}.mols SET mfp = morganbv_fp(mol)"
psql -d $DB -c "UPDATE ${DBSCHEMA}.mols SET ffp = featmorganbv_fp(mol)"
psql -d $DB -c "UPDATE ${DBSCHEMA}.mols SET torsionbv = torsionbv_fp(mol)"
#
psql -d $DB -c "CREATE INDEX fps_fp_idx ON ${DBSCHEMA}.mols USING gist(fp)"
psql -d $DB -c "CREATE INDEX fps_mfp_idx ON ${DBSCHEMA}.mols USING gist(mfp)"
psql -d $DB -c "CREATE INDEX fps_ffp_idx ON ${DBSCHEMA}.mols USING gist(ffp)"
psql -d $DB -c "CREATE INDEX fps_ttbv_idx ON ${DBSCHEMA}.mols USING gist(torsionbv)"
#
#
### Convenience function:
#
psql -d $DB <<__EOF__
CREATE OR REPLACE FUNCTION
	rdk_simsearch(smiles text)
RETURNS TABLE(id INTEGER, mol mol, similarity double precision) AS
	\$\$
	SELECT
		id,mol,tanimoto_sml(rdkit_fp(mol_from_smiles(\$1::cstring)),fp) AS similarity
	FROM
		${DBSCHEMA}.mols
	WHERE
		rdkit_fp(mol_from_smiles(\$1::cstring))%fp
	ORDER BY
		rdkit_fp(mol_from_smiles(\$1::cstring))<%>fp
		;
	\$\$
LANGUAGE SQL STABLE
	;
__EOF__
#
