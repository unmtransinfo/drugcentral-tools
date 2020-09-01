#!/bin/bash
#
SRCDATADIR="${HOME}/../data/DrugCentral/PropertyPatch"
#
DBNAME="drugcentral"
SCHEMA="public"
#
# Temporary SUPERUSER to allow COPY:
#sudo -u postgres psql -c "ALTER USER $USER WITH SUPERUSER"
###
psql -t -d $DBNAME -c "DROP TABLE IF EXISTS property_type CASCADE"
psql -t -d $DBNAME -c "DROP TABLE IF EXISTS property CASCADE"
#
psql -d $DBNAME <<__EOF__
CREATE TABLE property_type (
    id serial PRIMARY KEY,
    category varchar(20),
    name varchar(80),
    symbol varchar(10),
    units varchar(10)
);
CREATE TABLE property (
    id serial PRIMARY KEY,
    property_type_id integer,
    property_type_symbol varchar(10),
    struct_id integer,
    value double precision,
    reference_id integer,
    reference_type varchar(50),
    source varchar(80)
);
__EOF__
###

#
psql -d $DBNAME <<__EOF__
COPY property_type(symbol, name, units, category)
FROM PROGRAM 'cat $SRCDATADIR/PK_property_type.tsv'
WITH (
        FORMAT CSV,
        HEADER true,
        NULL 'NA',
        DELIMITER E'\t',
        QUOTE '"'
);
__EOF__
#
psql -d $DBNAME -Atc "select * from property_type"
#
###
# Benet
# DC.ID   SMILES  NAME    BDDCS   S (mg/mL)       EoM (%)
ifile_name="benet2009_mapping.tsv"
ifile="$SRCDATADIR/$ifile_name"
N=$(($(cat $ifile |grep -v '^\s*$' |wc -l) - 1))
printf "Loading %s: %d rows\n" "$ifile" "$N"
i=0
while [ "$i" -lt "$N" ]; do
	i=$(($i + 1))
	ii=$(($i + 1))
	line=$(cat $ifile |grep -v '^\s*$' |sed "${ii}q;d")
	struct_id=$(echo "$line" |awk -F '\t' '{print $1}')
	bddcs=$(echo "$line" |awk -F '\t' '{print $4}'|sed -e 's/\s//g')
	s=$(echo "$line" |awk -F '\t' '{print $5}'|sed -e 's/\s//g')
	eom=$(echo "$line" |awk -F '\t' '{print $6}'|sed -e 's/\s//g')
	printf "%d. struct_id=%s; bddcs=%s; s=%s; eom=%s\n" "$i" "$struct_id" "$bddcs" "$s" "$eom"
	#
	if [ "$bddcs" ]; then
		psql -d $DBNAME -c "INSERT INTO property (struct_id, property_type_symbol, value, source) VALUES ($struct_id, 'BDDCS', $bddcs, '$ifile_name')"
	fi
	if [ "$s" ]; then
		psql -d $DBNAME -c "INSERT INTO property (struct_id, property_type_symbol, value, source) VALUES ($struct_id, 'S', $s, '$ifile_name')"
	fi
	if [ "$eom" ]; then
		psql -d $DBNAME -c "INSERT INTO property (struct_id, property_type_symbol, value, source) VALUES ($struct_id, 'EoM', $eom, '$ifile_name')"
	fi
done
#
#exit #DEBUG
###
# Contrera
# DC.ID   SMILES  NAME    MRTD (µM/kg/day)
ifile_name="contrera2004_mapping.tsv"
ifile="$SRCDATADIR/$ifile_name"
N=$(($(cat $ifile |grep -v '^\s*$' |wc -l) - 1))
printf "Loading %s: %d rows\n" "$ifile" "$N"
i=0
while [ "$i" -lt "$N" ]; do
	i=$(($i + 1))
	ii=$(($i + 1))
	line=$(cat $ifile |grep -v '^\s*$' |sed "${ii}q;d")
	struct_id=$(echo "$line" |awk -F '\t' '{print $1}')
	mrtd=$(echo "$line" |awk -F '\t' '{print $4}'|sed -e 's/\s//g')
	printf "%d. struct_id=%s; mrtd=%s\n" "$i" "$struct_id" "$mrtd"
	#
	if [ "$mrtd" ]; then
		psql -d $DBNAME -c "INSERT INTO property (struct_id, property_type_symbol, value, source) VALUES ($struct_id, 'MRTD', $mrtd, '$ifile_name')"
	fi
done
#
###
# Hosey
# DC.ID   SMILES  NAME    BDDCS   S (mg/mL)       EoM (%)
ifile_name="hosey2016_mapping.tsv"
ifile="$SRCDATADIR/$ifile_name"
N=$(($(cat $ifile |grep -v '^\s*$' |wc -l) - 1))
printf "Loading %s: %d rows\n" "$ifile" "$N"
i=0
while [ "$i" -lt "$N" ]; do
	i=$(($i + 1))
	ii=$(($i + 1))
	line=$(cat $ifile |grep -v '^\s*$' |sed "${ii}q;d")
	struct_id=$(echo "$line" |awk -F '\t' '{print $1}'|sed -e 's/\s//g')
	bddcs=$(echo "$line" |awk -F '\t' '{print $4}'|sed -e 's/\s//g')
	s=$(echo "$line" |awk -F '\t' '{print $5}'|sed -e 's/\s//g')
	eom=$(echo "$line" |awk -F '\t' '{print $6}'|sed -e 's/\s//g')
	printf "%d. struct_id=%s; bddcs=%s; s=%s; eom=%s\n" "$i" "$struct_id" "$bddcs" "$s" "$eom"
	#
	if [ "$bddcs" ]; then
		psql -d $DBNAME -c "INSERT INTO property (struct_id, property_type_symbol, value, source) VALUES ($struct_id, 'BDDCS', $bddcs, '$ifile_name')"
	fi
	if [ "$s" ]; then
		psql -d $DBNAME -c "INSERT INTO property (struct_id, property_type_symbol, value, source) VALUES ($struct_id, 'S', $s, '$ifile_name')"
	fi
	if [ "$eom" ]; then
		psql -d $DBNAME -c "INSERT INTO property (struct_id, property_type_symbol, value, source) VALUES ($struct_id, 'EoM', $eom, '$ifile_name')"
	fi
done
#
###
# Lombardo
# DC.ID   NAME    SMILES  CAS_REG_NO      Vd (L/kg)       CL (mL/min/kg)  fu (%)  t1/2 (h)
ifile_name="lombardo2018_mapping.tsv"
ifile="$SRCDATADIR/$ifile_name"
N=$(($(cat $ifile |grep -v '^\s*$' |wc -l) - 1))
printf "Loading %s: %d rows\n" "$ifile" "$N"
i=0
while [ "$i" -lt "$N" ]; do
	i=$(($i + 1))
	ii=$(($i + 1))
	line=$(cat $ifile |grep -v '^\s*$' |sed "${ii}q;d")
	struct_id=$(echo "$line" |awk -F '\t' '{print $1}')
	vd=$(echo "$line" |awk -F '\t' '{print $5}'|sed -e 's/\s//g')
	cl=$(echo "$line" |awk -F '\t' '{print $6}'|sed -e 's/\s//g')
	fu=$(echo "$line" |awk -F '\t' '{print $7}'|sed -e 's/\s//g')
	t_half=$(echo "$line" |awk -F '\t' '{print $8}'|sed -e 's/\s//g')
	printf "%d. struct_id=%s; vd=%s; cl=%s; fu=%s; thalf=%s\n" "$i" "$struct_id" "$vd" "$cl" "$fu" "$thalf"
	#
	if [ "$vd" ]; then
		psql -d $DBNAME -c "INSERT INTO property (struct_id, property_type_symbol, value, source) VALUES ($struct_id, 'Vd', $vd, '$ifile_name')"
	fi
	if [ "$cl" ]; then
		psql -d $DBNAME -c "INSERT INTO property (struct_id, property_type_symbol, value, source) VALUES ($struct_id, 'CL', $cl, '$ifile_name')"
	fi
	if [ "$fu" ]; then
		psql -d $DBNAME -c "INSERT INTO property (struct_id, property_type_symbol, value, source) VALUES ($struct_id, 'fu', $fu, '$ifile_name')"
	fi
	if [ "$t_half" ]; then
		psql -d $DBNAME -c "INSERT INTO property (struct_id, property_type_symbol, value, source) VALUES ($struct_id, 't_half', $t_half, '$ifile_name')"
	fi
done
#
#
###
tables="property property_type"
#
for table in $tables ; do
	nrow=$(psql -t -d $DBNAME -c "SELECT count(*) FROM $SCHEMA.$table")
	printf "%s\tnrow=%d\n" "$table" "$nrow"
	psql -qAF ',' -d $DBNAME -c "SELECT table_name,column_name,data_type FROM information_schema.columns WHERE table_schema='$SCHEMA' AND table_name='$table'"
done
#
#sudo -u postgres psql -c "ALTER USER $USER WITH NOSUPERUSER"
#
