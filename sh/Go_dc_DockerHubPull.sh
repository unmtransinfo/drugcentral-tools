#!/bin/bash
###
#
INAME="drugcentral_db"
TAG="latest"
#
DOCKER_ORGANIZATION="unmtransinfo"
#
###
#
docker pull ${DOCKER_ORGANIZATION}/${INAME}:${TAG}
#
docker image ls
#
