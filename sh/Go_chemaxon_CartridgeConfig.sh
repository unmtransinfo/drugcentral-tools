#!/bin/bash
###
# https://docs.chemaxon.com/display/docs/getting-started-jchem-postgresql-cartridge.md
# https://docs.chemaxon.com/display/docs/jchem-postgresql-cartridge-manual.md
###
#sudo dpkg -i jchem-psql_20.18.0.r12267_amd64.deb
#sudo service jchem-psql init
#sudo cp ~/.chemaxon/license.cxl /etc/chemaxon
#sudo service jchem-psql manual-start
#sudo -u postgres createuser chemaxon
#sudo -u postgres createdb chemaxon -O chemaxon
#sudo -u postgres psql -d chemaxon -c "CREATE EXTENSION chemaxon_type"
#sudo -u postgres psql -d chemaxon -c "CREATE EXTENSION hstore"
#sudo -u postgres psql -d chemaxon -c "CREATE EXTENSION chemaxon_framework"
#sudo -u postgres psql -d chemaxon -c "SELECT 'C'::Molecule('sample') |<| 'CC'::Molecule"
###
# Modify existing drugcentral db:
# Molecule type "sample" defined by /etc/chemaxon/types/sample.type
# Create custom types standardizer options as needed.
#
DBNAME="drugcentral"
#
sudo -u postgres psql -d $DBNAME -c "CREATE EXTENSION chemaxon_type"
sudo -u postgres psql -d $DBNAME -c "CREATE EXTENSION hstore"
sudo -u postgres psql -d $DBNAME -c "CREATE EXTENSION chemaxon_framework"
sudo -u postgres psql -d $DBNAME -c "SELECT 'C'::Molecule('sample') |<| 'CC'::Molecule"
psql -d $DBNAME -c "SELECT 'C'::Molecule('sample') |<| 'CC'::Molecule"
#
###
sudo -u postgres psql -d $DBNAME -c "ALTER TABLE structures ADD COLUMN cx_mol MOLECULE('sample')"
sudo -u postgres psql -d $DBNAME -c "UPDATE structures SET cx_mol = smiles::Molecule('sample')"
#
qsmi="NCCc1ccc(O)c(O)c1"
psql -qAF $'\t' -d $DBNAME -c "SELECT name,smiles FROM structures WHERE 'NCCc1ccc(O)c(O)c1'::Molecule |=| cx_mol"
psql -qAF $'\t' -d $DBNAME -c "SELECT name,smiles FROM structures WHERE 'NCCc1ccc(O)c(O)c1'::Molecule |>| cx_mol"
psql -qAF $'\t' -d $DBNAME -c "SELECT name,smiles FROM structures WHERE 'NCCc1ccc(O)c(O)c1'::Molecule |<| cx_mol ORDER BY cd_molweight LIMIT 10"
psql -qAF $'\t' -d $DBNAME -c "SELECT name, smiles, (cx_mol |~| '${qsmi}') AS similarity FROM structures WHERE ('${qsmi}', 0.6)::sim_filter |<~| cx_mol ORDER BY similarity DESC LIMIT 10"
#
psql -qAF $'\t' -d $DBNAME -c "SELECT molconvert(cx_mol,'sdf') FROM structures WHERE name = 'dopamine'"
psql -qAF $'\t' -d $DBNAME -c "SELECT molconvert(standardize('C1=CC=CC=C1CC[N+](=O)[O-]'::Molecule('sample'))::Molecule('sample'), 'smiles')"
#
