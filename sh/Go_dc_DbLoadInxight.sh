#!/bin/bash
#
#
DBNAME="drugcentral"
DBHOST="localhost"
DBPORT="5432"
DBUSER="$(whoami)"
DBSCHEMA="public"
IDIR="$(pwd)/data/inxight"
#
#
PGARGS=" -h $DBHOST -p $DBPORT -U $DBUSER -d $DBNAME"
printf "PGARGS: ${PGARGS}\n"
#
###
#
ifile="${IDIR}/dc_inx_drug_mapped.tsv"
tname="inxight_drugs"
psql $PGARGS -c "DROP TABLE IF EXISTS  $tname"
(cat <<__EOF__
CREATE TABLE public.${tname} (
	dc_struct_id VARCHAR(24),
	dc_xref_unii VARCHAR(24),
	dc_dc_struct_name VARCHAR(1024),
	inx_unii VARCHAR(24),
	inx_drug_name VARCHAR(1024)
);
__EOF__
	) >$IDIR/${tname}_create.sql
psql $PGARGS <$IDIR/${tname}_create.sql
#
python3 -m BioClients.util.pandas.Csv2Sql insert \
	--fixtags --nullify --tsv \
	--tablename ${tname} \
	--i $ifile \
	>$IDIR/${tname}_insert.sql
psql $PGARGS <$IDIR/${tname}_insert.sql
#
psql $PGARGS -c "COMMENT ON TABLE $tname IS 'Inxight: Drugs'";
###
ifile="${IDIR}/dc_inx_target_mapped.tsv"
tname="inxight_targets"
psql $PGARGS -c "DROP TABLE IF EXISTS  $tname"
(cat <<__EOF__
CREATE TABLE public.inxight_targets (
	dc_target_class VARCHAR(24),
	dc_component_id VARCHAR(24),
	dc_target_uniprot VARCHAR(24),
	dc_target_organism VARCHAR(128),
	dc_component_name VARCHAR(1024),
	dc_gene_symbol VARCHAR(24),
	inx_target_uniprot_id VARCHAR(24)
);
__EOF__
	) >$IDIR/${tname}_create.sql
psql $PGARGS <$IDIR/${tname}_create.sql
#
python3 -m BioClients.util.pandas.Csv2Sql insert \
	--fixtags --nullify --tsv \
	--tablename ${tname} \
	--i $ifile \
	>$IDIR/${tname}_insert.sql
psql $PGARGS <$IDIR/${tname}_insert.sql
#
psql $PGARGS -c "COMMENT ON TABLE $tname IS 'Inxight: Targets'";
###
ifile="${IDIR}/dc_inx_act_mapped.tsv"
tname="inxight_activity"
psql $PGARGS -c "DROP TABLE IF EXISTS  $tname"
(cat <<__EOF__
CREATE TABLE public.inxight_activity (
	inx_unii VARCHAR(24),
	inx_drug_name VARCHAR(1024),
	inx_target_uniprot_id VARCHAR(24),
	inx_target_label VARCHAR(1024),
	inx_pharmacology VARCHAR(1024),
	inx_potency_type VARCHAR(24),
	inx_potency_value FLOAT,
	inx_potency_unit VARCHAR(24),
	dc_struct_id VARCHAR(24),
	dc_xref_unii VARCHAR(24),
	dc_component_id VARCHAR(24),
	dc_target_uniprot VARCHAR(24),
	dc_component_name VARCHAR(1024)
);
__EOF__
	) >$IDIR/${tname}_create.sql
psql $PGARGS <$IDIR/${tname}_create.sql
#
python3 -m BioClients.util.pandas.Csv2Sql insert \
	--fixtags --nullify --tsv \
	--tablename ${tname} \
	--i $ifile \
	>$IDIR/${tname}_insert.sql
psql $PGARGS <$IDIR/${tname}_insert.sql
#
psql $PGARGS -c "COMMENT ON TABLE $tname IS 'Inxight: Bioactivity Data'";
###
