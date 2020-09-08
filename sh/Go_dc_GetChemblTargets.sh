#!/bin/bash
###
# From DrugCentral IDs, get ChEMBL targets via activities.
# Note: in many cases, multiple CHEMBL_MOLECULE_IDs per DC struct_id.

cwd=$(pwd)
DATADIR=${cwd}/data
#
DBHOST="localhost"
DBNAME="drugcentral"
#
###
psql -qAF $'\t' -h $DBHOST -d $DBNAME -c "SELECT DISTINCT struct_id, identifier AS chembl_id FROM identifier WHERE identifier.id_type = 'ChEMBL_ID' ORDER BY struct_id" \
	>$DATADIR/dc_struct2chemblid.tsv
#
###
cat $DATADIR/dc_struct2chemblid.tsv \
	|sed -e '1d' \
	|awk -F '\t' '{print $2}' \
	|grep -v '^$' \
	|sort -u \
	>$DATADIR/dc_struct.chemblid
#
python3 -m BioClients.chembl.Client get_activity_by_mol \
	--i $DATADIR/dc_struct.chemblid \
	--o $DATADIR/dc_struct_chembl_act.tsv
#

${cwd}/python/pandas_utils.py selectcols \
	--coltags "target_chembl_id,target_organism,target_pref_name,target_tax_id" \
	--i $DATADIR/dc_struct_chembl_act.tsv \
	--o $DATADIR/dc_struct_chembl_tgt.tsv
${cwd}/python/pandas_utils.py deduplicate \
	--i $DATADIR/dc_struct_chembl_tgt.tsv \
	--o $DATADIR/dc_struct_chembl_tgt.tsv
cat $DATADIR/dc_struct_chembl_tgt.tsv \
	|sed -e '1d' \
	|awk -F '\t' '{print $1}' \
	|sort -u \
	>$DATADIR/dc_struct_chembl_tgt.chemblid
#
python3 -m BioClients.chembl.Client get_target \
	--i $DATADIR/dc_struct_chembl_tgt.chemblid \
	--o $DATADIR/dc_struct_chembl_target_data.tsv
#
