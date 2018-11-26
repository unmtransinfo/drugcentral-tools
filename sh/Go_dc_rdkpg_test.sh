#!/bin/sh
#############################################################################
# https://rdkit.readthedocs.org/en/latest/Cartridge.html#reference-guide
#
### operator "@>" : is substructure 
### operator "=>" : is exact structure 
### operator "%" : is tanimoto similarity > rdkit.tanimoto_threshold
#############################################################################
#
DB="drugcentral"
DBSCHEMA="public"
#
#
set -x
#
psql -P pager=off -d $DB -c "SELECT id,mol FROM ${DBSCHEMA}.mols WHERE mol@>'COC(=O)c1ccccc1O' LIMIT 10"
psql -P pager=off -d $DB -c "SELECT id,mol FROM ${DBSCHEMA}.mols WHERE mol@>'c1[o,s]ncn1'::qmol LIMIT 10"
psql -P pager=off -d $DB -c "SELECT * FROM rdk_simsearch('COC(=O)c1ccccc1O')"
#
psql -P pager=off -d $DB <<__EOF__
SET rdkit.tanimoto_threshold=0.0;
SELECT
	sim.id,
	s.name,
	sim.mol,
	TO_CHAR(sim.similarity,'0.99') AS sim_tanimoto
FROM
	rdk_simsearch('COC(=O)c1ccccc1O') sim
JOIN
	${DBSCHEMA}.structures s ON (s.id = sim.id)
WHERE
	similarity > 0.4
	;
__EOF__
#
