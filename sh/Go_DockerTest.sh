#!/bin/bash
###
# Instantiate and run containers.
# -dit = --detached --interactive --tty
###
set -e
#
if [ $(whoami) != "root" ]; then
	echo "${0} should be run as root or via sudo."
	exit
fi
#
cwd=$(pwd)
#
INAME_DB="drugcentral_db"
TAG="latest"
#
APPPORT_DB=5432
DOCKERPORT_DB=5433
#
docker container ls -a
docker container logs "${INAME_DB}_container"
#
###
# Test db.
docker exec "${INAME_DB}_container" sudo -u postgres psql -l
docker exec "${INAME_DB}_container" sudo -u postgres psql -d drugcentral -c "SELECT table_name FROM information_schema.tables WHERE table_schema='public'"
###
# Test
#
DOCKERHOST="localhost"
psql -h $DOCKERHOST -p 5433 -U drugman -l
psql -h $DOCKERHOST -p 5433 -U drugman -d drugcentral -c "SELECT COUNT(DISTINCT smiles) FROM structures"
#
python3 -m BioClients.drugcentral.Client -h
python3 -m BioClients.drugcentral.Client version \
	--dbhost $DOCKERHOST --dbport 5433 --dbname drugcentral --dbusr drugman --dbpw dosage

python3 -m BioClients.drugcentral.Client list_tables_rowCounts \
	--dbhost $DOCKERHOST --dbport 5433 --dbname drugcentral --dbusr drugman --dbpw dosage
#
