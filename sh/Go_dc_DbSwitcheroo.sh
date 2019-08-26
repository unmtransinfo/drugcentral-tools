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
sudo -u "$PGSU" psql -c "ALTER DATABASE $DB RENAME TO ${DB}_old"
#
sudo -u "$PGSU" psql -c "ALTER DATABASE ${DB}_new RENAME TO ${DB}"
#
