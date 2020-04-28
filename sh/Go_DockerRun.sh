#!/bin/bash
###
# Instantiate and run containers.
# -dit = --detached --interactive --tty
###
set -e
#
cwd=$(pwd)
#
ORG="unmtransinfo"
INAME_DB="drugcentral_db"
VTAG="v0.0.1-SNAPSHOT"
#
APPPORT_DB=5432
DOCKERPORT_DB=5050
#
# Note that "run" is equivalent to "create" + "start".
sudo docker run -dit \
	--name "${INAME_DB}_container" \
	-p ${DOCKERPORT_DB}:${APPPORT_DB} \
	${ORG}/${INAME_DB}:${VTAG}
#
sudo docker container ls -a
#
sudo docker container logs "${INAME_DB}_container"
#
###
echo "Sleep while db server starting up..."
sleep 10
###
# Test db.
sudo docker exec "${INAME_DB}_container" sudo -u postgres psql -l
sudo docker exec "${INAME_DB}_container" sudo -u postgres psql -d drugcentral -c "SELECT table_name FROM information_schema.tables WHERE table_schema='public'"
###
# Test from localhost.
psql -h localhost -p 5050 -U drugman -l
psql -h localhost -p 5050 -U drugman -d drugcentral
#
python3 -m BioClients.drugcentral.Client -h
python3 -m BioClients.drugcentral.Client version \
	--dbhost localhost --dbname drugcentral --dbusr drugman --dbpw dosage

python3 -m BioClients.drugcentral.Client counts \
	--dbhost localhost --dbname drugcentral --dbusr drugman --dbpw dosage
#
