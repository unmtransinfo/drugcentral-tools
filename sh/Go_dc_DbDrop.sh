#!/bin/sh
#############################################################################
# Pg server may need restart if there are current sessions.
# 
# $ sudo systemctl restart postgresql.service 
# 	OR
# $ sudo -u postgres /home/app/pgsql/bin/pg_ctl -D /home/postgres/pgdata stop
# OR MAYBE TO END SESSIONS: sudo -u postgres /home/app/pgsql/bin/pg_ctl -D /home/postgres/pgdata -m fast stop
# $ sudo -u postgres /home/app/pgsql/bin/pg_ctl -D /home/postgres/pgdata -o "-i" start
#############################################################################
#
DBNAME="drugcentral"
#
if [ -e "/home/app/pgsql/bin/dropdb" ]; then
	DROPDB="/home/app/pgsql/bin/dropdb"
elif [ -e "/usr/bin/dropdb" ]; then
	DROPDB="/usr/bin/dropdb"
elif [ `which dropdb` ]; then
	DROPDB="dropdb"
else
	echo 'ERROR: dropdb not found.'
	exit
fi
#
sudo -u postgres $DROPDB ${DBNAME}
#
