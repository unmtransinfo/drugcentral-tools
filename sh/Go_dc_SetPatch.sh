#!/bin/bash
#
SRCDATADIR="${HOME}/../data/DrugCentral/SI_Sets"
#
DBNAME="drugcentral"
SCHEMA="public"
#
###
function strstrip {
  echo "$1" |sed -e 's/^\s*//' |sed -e 's/\s*$//'
}
#
psql -d $DBNAME -c "ALTER TABLE structures DROP COLUMN status"
psql -d $DBNAME -c "ALTER TABLE structures ADD COLUMN status varchar(10)"
psql -d $DBNAME -c "COMMENT ON COLUMN structures.status IS 'Added by Go_dc_SetPatch.sh for OrangeBook sets (OFP, ONP, OFM) from 2020 paper.'"
###
# Set	DrugCentral_ID	Name
ifile_name="SI_Sets_OBSET2STRUCT.tsv"
ifile="$SRCDATADIR/$ifile_name"
N=$(($(cat $ifile |grep -v '^\s*$' |wc -l) - 1))
printf "Loading %s: %d rows\n" "$ifile" "$N"
i=0
n_err=0
n_val=0
while [ "$i" -lt "$N" ]; do
	i=$(($i + 1))
	ii=$(($i + 1))
	line=$(cat $ifile |grep -v '^\s*$' |sed "${ii}q;d")
	s=$(echo "$line" |awk -F '\t' '{print $1}'|sed -e 's/\s//g')
	struct_id=$(echo "$line" |awk -F '\t' '{print $2}')
	name=$(echo "$line" |awk -F '\t' '{print $3}')
	dc_name="$(strstrip "$(psql -d $DBNAME -tc "SELECT name FROM structures WHERE id = $struct_id")")"
	printf "%d. struct_id=\"%s\"; set=\"%s\"; name=\"%s\"; dc_name=\"%s\"\n" "$i" "$struct_id" "$s" "$name" "$dc_name"
	#
	if [ "$name" != "$dc_name" ]; then
		printf "%d. WARNING: name=\"%s\" != dc_name=\"%s\"\n" "$i" "$name" "$dc_name"
		n_err=$(($n_err + 1))
	fi
	if [ "$struct_id" -a "$s" ]; then
		psql -q -d $DBNAME -c "UPDATE structures SET status = '$s' WHERE id = $struct_id"
		n_val=$(($n_val + 1))
	fi
done
#
printf "N_row: %d; n_val: %d; n_err: %d\n" "$N" "$n_val" "$n_err"
#
#
