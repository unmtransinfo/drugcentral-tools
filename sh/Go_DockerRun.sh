#!/bin/bash
###
# Instantiate and run containers.
# -dit = --detached --interactive --tty
###
set -e
#
cwd=$(pwd)
#
VTAG="v0.0.1-SNAPSHOT"
#
###
# PostgreSQL db
INAME_DB="drugcentral_db"
#
DOCKERPORT_DB=5050
APPPORT_DB=5432
#
sudo docker run -dit \
	--name "${INAME_DB}_container" \
	-p ${DOCKERPORT_DB}:${APPPORT_DB} \
	${INAME_DB}:${VTAG}
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
sudo docker container ls -a
#
#
