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
if [ ! -e /home/data/DrugCentral/drugcentral_20211005.pgdump  ]; then
	sudo -u postgres pg_dump --no-privileges -Fc -d drugcentral_20211005 >/home/data/DrugCentral/drugcentral_20211005.pgdump 
fi
#cp /home/data/DrugCentral/drugcentral_2018.pgdump ${cwd}/data/
cp /home/data/DrugCentral/drugcentral_2020.pgdump ${cwd}/data/
cp /home/data/DrugCentral/drugcentral_20211005.pgdump ${cwd}/data/drugcentral_2021.pgdump
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
#rm -f ${cwd}/data/drugcentral_2018.pgdump
rm -f ${cwd}/data/drugcentral_2020.pgdump
rm -f ${cwd}/data/drugcentral_2021.pgdump
#
docker images
#
