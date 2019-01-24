#!/bin/sh
#
#
#
DBHOST="localhost"
DBNAME="drugcentral"
DBSCHEMA="public"
PGSU="postgres"
#
dumpfile="/home/data/drugcentral/drugcentral.dump.sql.gz"
#
sudo -u "$PGSU" psql -c "CREATE DATABASE $DBNAME"
#
gunzip -c "$dumpfile" |sudo -u "$PGSU" psql -d "$DBNAME"
#
sudo -u "$PGSU" psql -d "$DBNAME" -c "CREATE ROLE jjyang WITH LOGIN PASSWORD 'assword'"
#
sudo -u "$PGSU" psql -c "ALTER DATABASE $DBNAME OWNER TO jjyang"
sudo -u "$PGSU" psql -c "GRANT ALL PRIVILEGES ON DATABASE $DBNAME TO jjyang"
sudo -u "$PGSU" psql -d $DBNAME -c "GRANT ALL PRIVILEGES ON SCHEMA $DBSCHEMA TO jjyang"
sudo -u "$PGSU" psql -d $DBNAME -c "GRANT USAGE ON SCHEMA $DBSCHEMA TO jjyang"
sudo -u "$PGSU" psql -d $DBNAME -c "GRANT SELECT ON ALL TABLES IN SCHEMA $DBSCHEMA TO jjyang"
sudo -u "$PGSU" psql -d $DBNAME -c "GRANT SELECT ON ALL SEQUENCES IN SCHEMA $DBSCHEMA TO jjyang"
sudo -u "$PGSU" psql -d $DBNAME -c "GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA $DBSCHEMA TO jjyang"
###
DBVER=`psql -d $DBNAME -Atc "SELECT version FROM dbversion"`
DBDATE=`psql -d $DBNAME -Atc "SELECT dtime FROM dbversion"`
#
psql -d $DBNAME -c "COMMENT ON DATABASE $DBNAME IS 'DrugCentral db version ${DBVER} (${DBDATE})'"
#
###
# Install rdkit, create mol table, FPs, etc.
./Go_dc_rdkpg_config.sh
#
###
# User "www":
./Go_dc_DbUsers.sh
#
