#!/bin/bash
###
# Instantiate and run containers.
# -dit = --detached --interactive --tty
###
set -e
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
psql -h $DOCKERHOST -p 5433 -U drugman -d drugcentral -c "SELECT name,smiles FROM structures WHERE RANDOM()<0.01 LIMIT 12"
#
