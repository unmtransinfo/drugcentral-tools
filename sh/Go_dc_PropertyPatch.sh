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
# Kim
# DC.ID NAME SMILES CAS_REG_NO BA (%)
ifile_name="kim2014_mapping.tsv"
ifile="$SRCDATADIR/$ifile_name"
N=$(($(cat $ifile |grep -v '^\s*$' |wc -l) - 1))
printf "Loading %s: %d rows\n" "$ifile" "$N"
i=0
while [ "$i" -lt "$N" ]; do
	i=$(($i + 1))
	ii=$(($i + 1))
	line=$(cat $ifile |grep -v '^\s*$' |sed "${ii}q;d")
	struct_id=$(echo "$line" |awk -F '\t' '{print $1}')
	ba=$(echo "$line" |awk -F '\t' '{print $5}'|sed -e 's/\s//g')
	printf "%d. struct_id=%s; ba=%s\n" "$i" "$struct_id" "$ba"
	#
	if [ "$ba" ]; then
		psql -d $DBNAME -c "INSERT INTO property (struct_id, property_type_symbol, value, source) VALUES ($struct_id, 'BA', $ba, '$ifile_name')"
	fi
done
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
	printf "%d. struct_id=%s; vd=%s; cl=%s; fu=%s; t_half=%s\n" "$i" "$struct_id" "$vd" "$cl" "$fu" "$t_half"
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
###
N_refs_start=$(psql -d $DBNAME -c "SELECT COUNT(DISTINCT id) FROM reference")
printf "Refs: %d\n" "$N_refs_start"
#
# References:
# PMIDs: 
# 21818695: Benet LZ, Broccatelli F, Oprea TI
# 15546675: Hosey CM, Chan R, Benet LZ
# 26589308: Contrera JF, Matthews EJ, Kruhlak NL, Benz RD
# 24306326: Kim MT, Sedykh A, Chakravarti SK, Saiakhov RD, Zhu H
# 30115648: Lombardo F, Berellini G, Obach RS
# id, pmid, doi, document_id, type, authors, title, isbn10, url, journal, volume, issue, dp_year, pages
reffile_name="PK_references.tsv"
reffile="$SRCDATADIR/$reffile_name"
N=$(($(cat $reffile |grep -v '^\s*$' |wc -l) - 1))
printf "Loading %s: %d rows\n" "$reffile" "$N"
i=0
while [ "$i" -lt "$N" ]; do
	i=$(($i + 1))
	ii=$(($i + 1))
	line=$(cat $reffile |grep -v '^\s*$' |sed "${ii}q;d")
	pmid=$(echo "$line" |awk -F '\t' '{print $2}' |sed -e 's/\s//g')
	doi=$(echo "$line" |awk -F '\t' '{print $3}' |sed -e 's/\s//g')
	reftype=$(echo "$line" |awk -F '\t' '{print $5}' |sed -e 's/\s//g')
	authors=$(echo "$line" |awk -F '\t' '{print $6}' |sed -e 's/\s//g')
	title=$(echo "$line" |awk -F '\t' '{print $7}' |sed -e 's/\s//g')
	isbn10=$(echo "$line" |awk -F '\t' '{print $8}' |sed -e 's/\s//g')
	url=$(echo "$line" |awk -F '\t' '{print $9}' |sed -e 's/\s//g')
	journal=$(echo "$line" |awk -F '\t' '{print $10}' |sed -e 's/\s//g')
	volume=$(echo "$line" |awk -F '\t' '{print $11}' |sed -e 's/\s//g')
	issue=$(echo "$line" |awk -F '\t' '{print $12}' |sed -e 's/\s//g')
	dp_year=$(echo "$line" |awk -F '\t' '{print $13}' |sed -e 's/\s//g')
	pages=$(echo "$line" |awk -F '\t' '{print $14}' |sed -e 's/\s//g')
	printf "%d. pmid=%s; doi=%s; reftype=%s; authors=%s; title=%s; isbn10=%s; url=%s; journal=%s; volume=%s; issue=%s; dp_year=%s; pages=%s\n" "$i" "$pmid" "$doi" "$reftype" "$authors" "$title" "$isbn10" "$url" "$journal" "$volume" "$issue" "$dp_year" "$pages"
	#
	psql -d $DBNAME <<__EOF__ 
INSERT INTO reference (
	pmid,
	doi,
	type,
	authors,
	title,
	isbn10,
	url,
	journal,
	volume,
	issue,
	dp_year,
	pages)
VALUES (
	$pmid,
	'$doi',
	'$reftype',
	'$authors',
	'$title',
	'$isbn10',
	'$url',
	'$journal',
	'$volume',
	'$issue',
	'$dp_year',
	'$pages')
WHERE
	(SELECT id FROM reference WHERE pmid = $pmid) IS NULL
__EOF__
done
#
N_refs_final=$(psql -d $DBNAME -c "SELECT COUNT(DISTINCT id) FROM reference")
printf "Refs: %d\n" "$N_refs_final"
#
psql -d $DBNAME -c "UPDATE property SET reference_id = (SELECT id FROM reference WHERE pmid = 21818695) WHERE source = 'benet2009_mapping.tsv'"
psql -d $DBNAME -c "UPDATE property SET reference_id = (SELECT id FROM reference WHERE pmid = 26589308) WHERE source = 'contrera2004_mapping.tsv'"
psql -d $DBNAME -c "UPDATE property SET reference_id = (SELECT id FROM reference WHERE pmid = 15546675) WHERE source = 'hosey2016_mapping.tsv'"
psql -d $DBNAME -c "UPDATE property SET reference_id = (SELECT id FROM reference WHERE pmid = 24306326) WHERE source = 'kim2014_mapping.tsv'"
psql -d $DBNAME -c "UPDATE property SET reference_id = (SELECT id FROM reference WHERE pmid = 30115648) WHERE source = 'lombardo2018_mapping.tsv'"
#

