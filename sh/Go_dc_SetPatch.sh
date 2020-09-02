#!/bin/bash
#
SRCDATADIR="${HOME}/../data/DrugCentral/SI_Sets"
#
DBNAME="drugcentral"
SCHEMA="public"
#
###
#
psql -d $DBNAME -c "ALTER TABLE structures ADD COLUMN status varchar(10)"
###
# Set	DrugCentral_ID	Name
ifile_name="SI_Sets_OBSET2STRUCT.tsv"
ifile="$SRCDATADIR/$ifile_name"
N=$(($(cat $ifile |grep -v '^\s*$' |wc -l) - 1))
printf "Loading %s: %d rows\n" "$ifile" "$N"
i=0
while [ "$i" -lt "$N" ]; do
	i=$(($i + 1))
	ii=$(($i + 1))
	line=$(cat $ifile |grep -v '^\s*$' |sed "${ii}q;d")
	s=$(echo "$line" |awk -F '\t' '{print $1}'|sed -e 's/\s//g')
	struct_id=$(echo "$line" |awk -F '\t' '{print $2}')
	name=$(echo "$line" |awk -F '\t' '{print $3}')
	printf "%d. struct_id=\"%s\"; name=\"%s\"; set=\"%s\"\n" "$i" "$struct_id" "$name" "$s"
	#
#	if [ "$struct_id" -a "$s" ]; then
#		psql -q -d $DBNAME -c "UPDATE structures SET status = '$s' WHERE id = $struct_id"
#	fi
done
#
