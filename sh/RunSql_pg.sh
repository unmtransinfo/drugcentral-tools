#!/bin/sh
#
#
DBH="chiltepin"
DBP="5432"
DBU="jjyang"
DB="drugcentral"
PSQLOPTS=''
#
if [ $# -eq 0 ]; then
	echo "syntax: $0 <sqlfile> [DB] [HOST] [PSQLOPTS]"
	exit
fi
if [ $# -gt 0 ]; then
	SQLFILE=$1
fi
if [ $# -gt 1 ]; then
	DB=$2
fi
if [ $# -gt 2 ]; then
	DBH=$3
fi
if [ $# -gt 3 ]; then
	PSQLOPTS=$4
fi
#
cmd="psql $PSQLOPTS -h $DBH -p $DBP -U $DBU $DB"
echo "$cmd <$SQLFILE"
#
$cmd <$SQLFILE
#
