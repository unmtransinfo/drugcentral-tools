#!/bin/bash
#
#
DBNAME="drugcentral"
DBHOST="localhost"
DBPORT="5432"
DBUSER="$(whoami)"
DBSCHEMA="public"
ODIR="$(pwd)/data"
#
help() {
        echo "syntax: $(basename $0) [options]"
        echo ""
	echo "  operations (one of):"
        echo "        -x ............. execute process"
        echo "        -t ............. test connection"
        echo "  options:"
        echo "        -n DBNAME ........ Pg db name [$DBNAME]"
        echo "        -s DBSCHEMA ...... Pg db schema [$DBSCHEMA]"
        echo "        -u DBUSER ........ Pg user [$DBUSER]"
        echo "        -h DBHOST ........ Pg host [$DBHOST]"
        echo "        -p DBPORT ........ Pg port [$DBPORT]"
        echo "        -o ODIR .......... output directory [$ODIR]"
        echo ""
        echo "Db password should be read from \$HOME/.pgpass"
}
#
if [ $# -eq 0 ]; then
        help
        exit 1
fi
#
OPERATION=""
### Parse options
while getopts n:s:u:h:p:o:tx opt ; do
        case "$opt"
        in
        n)      DBNAME=$OPTARG ;;
        s)      DBSCHEMA=$OPTARG ;;
        u)      DBUSER=$OPTARG ;;
        h)      DBHOST=$OPTARG ;;
        p)      DBPORT=$OPTARG ;;
        o)      ODIR=$OPTARG ;;
        t)      OPERATION="TEST" ;;
        x)      OPERATION="EXECUTE" ;;
        \?)     help
                exit 1 ;;
        esac
done
#
#
set -x
###
printf "OPERATION: ${OPERATION}\n"
#
if [ $OPERATION = "TEST" ]; then
	ok=(psql -h $DBHOST -p $DBPORT -U $DBUSER -d $DBNAME)
	rval=$?
	if [ $rval = 0 ]; then
		printf "Connection ok.\n"
	else
		printf "Connection NOT OK.\n"
	fi
	if [ -d "${ODIR}" ]; then
		printf "ODIR FOUND: ${ODIR}\n"
	else
		printf "ERROR: ODIR NOT FOUND: ${ODIR}\n"
	fi
	exit
fi
#
PGARGS=" -h $DBHOST -p $DBPORT -U $DBUSER -d $DBNAME"
printf "PGARGS: ${PGARGS}\n"
#
echo '\d+' |psql $PGARGS \
	|grep '|' \
	|sed -e 's/^ *//' \
	|sed -e 's/ *$//' \
	|perl -pe 's/ *\| */\t/g' \
	>$ODIR/${DBNAME}_tables.tsv
#
###
tables=`psql $PGARGS -qAtc "SELECT table_name FROM information_schema.tables WHERE table_schema='$DBSCHEMA' AND table_name NOT ILIKE 'ijc_%' AND table_name !~ '^DEMO' AND table_name !~ '^SOL_' AND table_name !~ '^TEST' AND table_name !~ '^TUT' AND table_name !~ '^node' AND table_name !~ '^message' AND table_name !~ '^my_' AND table_name !~ '^test' AND table_name !~ '^snapshot' AND table_name !~ '^[A-Z]' ORDER BY table_name"`
#
printf "table\tnrow\n" >$ODIR/${DBNAME}_tables_nrow.tsv
# For data dictionary import TSV to worksheet.
printf "table_name\tcolumn_name\tdata_type\n" >$ODIR/${DBNAME}_tables_columns.tsv
#
for table in $tables ; do
	nrow=$(psql $PGARGS -tc "SELECT count(*) FROM $DBSCHEMA.$table")
	printf "%s\t%d\n" "$table" "$nrow" >>$ODIR/${DBNAME}_tables_nrow.tsv
	psql $PGARGS -F ',' -qAtc "SELECT table_name,column_name,data_type FROM information_schema.columns WHERE table_schema='$DBSCHEMA' AND table_name = '${table}'" \
		|grep -v '^(.* rows' \
		|perl -pe 's/,/\t/g' \
		>>$ODIR/${DBNAME}_tables_columns.tsv
done
#
###
#
