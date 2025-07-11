#!/bin/bash
###
#
INAME="drugcentral_db"
CNAME="${INAME}_container"
#
###
# Stop and clean up.
docker stop ${CNAME}
docker ps -a
docker rm ${CNAME}
docker rmi ${INAME}
#
IIDS=$(docker images -f dangling=true \
	|sed -e '1d' \
	|awk -e '{print $3}')
for iid in $IIDS ; do
	docker rmi ${iid}
done
#
#
docker container ls -a
#
