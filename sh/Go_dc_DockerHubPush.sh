#!/bin/bash
###
#
INAME="drugcentral_db"
TAG="latest"
#
DOCKER_ID_USER="jeremyjyang"
DOCKER_ORGANIZATION="unmtransinfo"
#
###
if [ ! "$DOCKER_ID_USER" ]; then
	echo "ERROR: \$DOCKER_ID_USER not defined."
	exit
fi
#
docker login
#
docker images
#
docker tag ${INAME}:${TAG} ${DOCKER_ORGANIZATION}/${INAME}:${TAG}
#
docker push ${DOCKER_ORGANIZATION}/${INAME}:${TAG}
#
