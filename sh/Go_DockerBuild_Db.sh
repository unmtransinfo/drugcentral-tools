#!/bin/bash
###
# Takes ~5-20min, depending on server, mostly pg_restore.
###
#
set -e
#
#
if [ $(whoami) != "root" ]; then
	echo "${0} should be run as root or via sudo."
	exit
fi
cwd=$(pwd)
#
docker version
#
INAME="drugcentral_db"
TAG="latest"
#
if [ ! -e "${cwd}/data" ]; then
	mkdir ${cwd}/data/
fi
#
cp /home/data/DrugCentral/drugcentral.pgdump ${cwd}/data/
#
T0=$(date +%s)
#
###
# Build image from Dockerfile.
dockerfile="${cwd}/Dockerfile_Db"
docker build -f ${dockerfile} -t ${INAME}:${TAG} .
#
printf "Elapsed time: %ds\n" "$[$(date +%s) - ${T0}]"
#
rm -f ${cwd}/data/drugcentral.pgdump
#
docker images
#
