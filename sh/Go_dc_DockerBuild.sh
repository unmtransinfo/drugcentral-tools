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
#sudo -u postgres pg_dump --no-privileges -Fc -d drugcentral_20200918 >/home/data/DrugCentral/drugcentral_2020.pgdump 
#sudo -u postgres pg_dump --no-privileges -Fc -d drugcentral_20211005 >/home/data/DrugCentral/drugcentral_20211005.pgdump 
DCRELEASE="20220822"
if [ ! -e /home/data/DrugCentral/drugcentral_${DCRELEASE}.pgdump  ]; then
	sudo -u postgres pg_dump --no-privileges -Fc -d drugcentral_${DCRELEASE} >/home/data/DrugCentral/drugcentral_${DCRELEASE}.pgdump 
fi
#cp /home/data/DrugCentral/drugcentral_20211005.pgdump ${cwd}/data/
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
#rm -f ${cwd}/data/drugcentral_20211005.pgdump
rm -f ${cwd}/data/drugcentral_${DCRELEASE}.pgdump
#
docker images
#
