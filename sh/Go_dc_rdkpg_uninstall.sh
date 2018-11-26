#!/bin/sh
#
#
DB="drugcentral"
DBSCHEMA="public"
DBHOST="localhost"
#
psql -d $DB -c "DROP FUNCTION ${DBSCHEMA}_simsearch_mfp"
#
psql -d $DB -c "DROP TABLE ${DBSCHEMA}.mols CASCADE" $DB
#
sudo -u postgres psql -d $DB -c 'DROP EXTENSION rdkit CASCADE'
#
