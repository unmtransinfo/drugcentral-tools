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
ORG="unmtransinfo"
INAME_DB="drugcentral_db"
TAG="latest"
#
APPPORT_DB=5432
DOCKERPORT_DB=5433
#
# Note that "run" is equivalent to "create" + "start".
docker run -dit \
	--name "${INAME_DB}_container" \
	-p ${DOCKERPORT_DB}:${APPPORT_DB} \
	${ORG}/${INAME_DB}:${TAG}
#
docker container ls -a
#
docker container logs "${INAME_DB}_container"
#
###
echo "Sleep while db server starting up..."
sleep 10
###
# Test db.
docker exec "${INAME_DB}_container" sudo -u postgres psql -l
docker exec "${INAME_DB}_container" sudo -u postgres psql -d drugcentral -c "SELECT table_name FROM information_schema.tables WHERE table_schema='public'"
###
# Test
#
DOCKERHOST="localhost"
psql -h $DOCKERHOST -p 5433 -U drugman -l
psql -h $DOCKERHOST -p 5433 -U drugman -d drugcentral
#
python3 -m BioClients.drugcentral.Client -h
python3 -m BioClients.drugcentral.Client version \
	--dbhost $DOCKERHOST --dbname drugcentral --dbusr drugman --dbpw dosage

python3 -m BioClients.drugcentral.Client counts \
	--dbhost $DOCKERHOST --dbname drugcentral --dbusr drugman --dbpw dosage
#
