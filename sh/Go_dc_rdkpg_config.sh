#!/bin/sh
#############################################################################
### Problem with rdkit-Release_2016_03_1 and some Boost versions.
### Ok: rdkit-Release_2015_03_1, Boost 1.60, OpenSUSE Tumbleweed (2016)
### Ok: rdkit-Release_2015_03_1, Boost 1.61, OpenSUSE Leap 42.3 (early 2018)
#############################################################################
#
cwd="$(pwd)"
#
DBNAME="drugcentral"
DBSCHEMA="public"
DBHOST="localhost"
#
sudo -u postgres psql -d $DBNAME -c 'CREATE EXTENSION rdkit'
#
### Create mols table for RDKit structural searching.
psql -d $DBNAME -f ${cwd}/sql/rdk_create_mol_table.sql
#
#	mol_from_smiles(regexp_replace(cd_smiles,E'\\\\s+.*$','')::cstring) AS mol
#
psql -d $DBNAME -c "CREATE INDEX molidx ON ${DBSCHEMA}.mols USING gist(mol)"
#
#
### Add FPs to mols table.
psql -d $DBNAME -c "ALTER TABLE ${DBSCHEMA}.mols ADD COLUMN fp BFP"
psql -d $DBNAME -c "ALTER TABLE ${DBSCHEMA}.mols ADD COLUMN mfp BFP"
psql -d $DBNAME -c "ALTER TABLE ${DBSCHEMA}.mols ADD COLUMN ffp BFP"
psql -d $DBNAME -c "ALTER TABLE ${DBSCHEMA}.mols ADD COLUMN torsionbv BFP"
psql -d $DBNAME -c "UPDATE ${DBSCHEMA}.mols SET fp = rdkit_fp(mol)"
psql -d $DBNAME -c "UPDATE ${DBSCHEMA}.mols SET mfp = morganbv_fp(mol)"
psql -d $DBNAME -c "UPDATE ${DBSCHEMA}.mols SET ffp = featmorganbv_fp(mol)"
psql -d $DBNAME -c "UPDATE ${DBSCHEMA}.mols SET torsionbv = torsionbv_fp(mol)"
#
psql -d $DBNAME -c "CREATE INDEX fps_fp_idx ON ${DBSCHEMA}.mols USING gist(fp)"
psql -d $DBNAME -c "CREATE INDEX fps_mfp_idx ON ${DBSCHEMA}.mols USING gist(mfp)"
psql -d $DBNAME -c "CREATE INDEX fps_ffp_idx ON ${DBSCHEMA}.mols USING gist(ffp)"
psql -d $DBNAME -c "CREATE INDEX fps_ttbv_idx ON ${DBSCHEMA}.mols USING gist(torsionbv)"
#
#
### Convenience function:
#
psql -d $DBNAME -f ${cwd}/sql/rdk_create_functions.sql
#
