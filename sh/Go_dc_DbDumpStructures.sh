#!/bin/sh
#
#
#
DBHOST="localhost"
DB="drugcentral"
DBSCHEMA="public"
PGSU="postgres"
#
DATADIR="data"
###
###
sdfile="$DATADIR/${DB}.sdf"
#
(
psql -qAt -d "$DB" <<__EOF__
SELECT
	id||molfile||E'\$\$\$\$'
FROM
	structures
WHERE
	molfile NOT LIKE '%V3000%'
__EOF__
) \
	|perl -pe 'BEGIN{undef $/;} s/\$\$\$\$\n\n*/\$\$\$\$\n/smg' \
	>$sdfile
#
smifile="$DATADIR/${DB}.smiles"
#molconvert 'smiles:-r1T*' $sdfile -o $smifile
cdk_utils.sh -i $sdfile -oisosmi -o $smifile -vv
#
###
###
sdfile="$DATADIR/${DB}_natural_products.sdf"
#
(
psql -qAt -d "$DB" <<__EOF__
SELECT
	s.id||s.molfile||E'\$\$\$\$'
FROM
	structures s
JOIN
	struct2atc s2atc ON s2atc.struct_id = s.id
JOIN
	atc ON atc.code = s2atc.atc_code
WHERE
	molfile NOT LIKE '%V3000%'
	AND (
		UPPER(atc.l1_name) LIKE '%NATURAL%'
		OR UPPER(atc.l2_name) LIKE '%NATURAL%'
		OR UPPER(atc.l3_name) LIKE '%NATURAL%'
		OR UPPER(atc.l4_name) LIKE '%NATURAL%'
	)
__EOF__
) \
	|perl -pe 'BEGIN{undef $/;} s/\$\$\$\$\n\n*/\$\$\$\$\n/smg' \
	>$sdfile
#
smifile="$DATADIR/${DB}_natural_products.smiles"
#molconvert 'smiles:-r1T*' $sdfile -o $smifile
cdk_utils.sh -i $sdfile -oisosmi -o $smifile -vv
