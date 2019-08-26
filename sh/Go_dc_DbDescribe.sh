#!/bin/bash
#
DBNAME="drugcentral"
SCHEMA="public"
#
###
echo '\d+' |psql -d $DBNAME \
	|grep '|' \
	|sed -e 's/^ *//' \
	|sed -e 's/ *$//' \
	|perl -pe 's/ *\| */\t/g' \
	>data/${DBNAME}_tables.tsv
#
###
tables=`psql -q -d $DBNAME -tAc "SELECT table_name FROM information_schema.tables WHERE table_schema='$SCHEMA'"`
#
printf "table\tnrow\n" \
	>data/${DBNAME}_tables_nrow.tsv
for table in $tables ; do
	nrow=$(psql -t -d $DBNAME -c "SELECT count(*) FROM $SCHEMA.$table")
	printf "%s\t%d\n" "$table" "$nrow" \
		>>data/${DBNAME}_tables_nrow.tsv
done
#
###
# For data dictionary import TSV to worksheet.
psql -qAF ',' -d $DBNAME -c "SELECT table_name,column_name,data_type FROM information_schema.columns WHERE table_schema='$SCHEMA' ORDER BY table_name" \
	|perl -pe 's/,/\t/g' \
	>data/${DBNAME}_tables_columns.tsv
#
