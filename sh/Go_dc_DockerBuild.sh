#!/bin/bash
###
# Takes ~5-20min, depending on server, mostly pg_restore.
# Docker should be configured so root privileges are not required.
###
#
set -e
#
#
#if [ $(whoami) != "root" ]; then
#	echo "${0} should be run as root or via sudo."
#	exit
#fi
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
#DCRELEASE="20230510"
DCRELEASE="20231101"
if [ ! -e /home/data/DrugCentral/drugcentral_${DCRELEASE}.pgdump  ]; then
	pg_dump --no-privileges -Fc -d drugcentral_${DCRELEASE} >/home/data/DrugCentral/drugcentral_${DCRELEASE}.pgdump 
fi
cp /home/data/DrugCentral/drugcentral_${DCRELEASE}.pgdump ${cwd}/data/drugcentral.pgdump
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
#rm -f ${cwd}/data/drugcentral_${DCRELEASE}.pgdump
#
docker images
#
