#!/bin/sh
#
#
#
DBHOST="localhost"
DB="drugcentral"
DBSCHEMA="public"
PGSU="postgres"
#
#
usrs="jjyang www"
#
sudo -u "$PGSU" psql -d "$DB" -c "CREATE ROLE www WITH LOGIN PASSWORD 'foobar'"
#
for usr in $usrs ; do
	sudo -u "$PGSU" psql -d "$DB" -c "GRANT SELECT ON ALL TABLES IN SCHEMA $DBSCHEMA TO $usr"
	sudo -u "$PGSU" psql -d "$DB" -c "GRANT USAGE ON SCHEMA $DBSCHEMA TO $usr"
	sudo -u "$PGSU" psql -d "$DB" -c "GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA $DBSCHEMA TO $usr"
done
#
