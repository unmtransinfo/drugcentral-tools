#!/bin/bash
#
cwd="$(pwd)"
TMPDATADIR="${cwd}/data"

LIBDIR="$(cd $HOME/../app/lib; pwd)"
UTIL_JARFILE="${LIBDIR}/unm_biocomp_util-0.0.1-SNAPSHOT-jar-with-dependencies.jar"

DATADIR="$(cd $HOME/../data/DrugCentral; pwd)"
DBDIR="${DATADIR}/drugdb/.config/localdb"

#
java -classpath ${UTIL_JARFILE} edu.unm.health.biocomp.util.db.derby_utils \
	-dbdir $DBDIR -dbname db \
	-list_tables \
	>${TMPDATADIR}/drugdb_tables.txt
#
n_table="$(cat ${TMPDATADIR}/drugdb_tables.txt |wc -l)"
printf "n_table: ${n_table}\n"
#
i_t="0"
while [ $i_t -lt $n_table ]; do
	i_t=$[$i_t + 1]
	line=$(cat ${TMPDATADIR}/drugdb_tables.txt |sed "${i_t}q;d")
	SNAME=$(echo $line |sed 's/\..*$//')
	TNAME=$(echo $line |sed 's/^.*\.//')
	if [[ $TNAME =~ ^IJC_ ]]; then
		printf "${TNAME} is InstantJChem system table; thus NOT EXPORTING.\n"
		continue
	fi
	tname=$(echo $TNAME |tr '[:upper:]' '[:lower:]')
	printf "${i_t}: schema:${SNAME} table:${TNAME}\n"
	ofile="${TMPDATADIR}/${tname}.csv"
	if [ -e "$ofile" ]; then
		if [ "$(cat $ofile |wc -l)" -gt 0 ]; then
			printf "${ofile} exists and is NOT EMPTY; thus NOT RE-GENERATING.\n"
			continue
		elif [ "$(cat $ofile |wc -l)" -eq 0 ]; then
			printf "${ofile} exists and IS EMPTY; thus RE-GENERATING.\n"
			rm -f $ofile
		fi
	fi
	printf "Writing: ${ofile}\n"
	java -classpath ${UTIL_JARFILE} edu.unm.health.biocomp.util.db.derby_utils \
		-v \
		-dbdir $DBDIR -dbname db \
		-dbschema $SNAME \
		-dbtable $TNAME \
		-export_table \
		-o ${ofile}
	sleep 3 # Give Derby time to close the connection.
done
#
