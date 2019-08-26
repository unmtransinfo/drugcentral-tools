#!/bin/sh
#
CONFIGFILE="data/d2rq_config_drugcentral.ttl"
#
columns="\
active_ingredient.id,\
active_ingredient.substance_name,\
active_ingredient.struct_id,\
atc.id,\
atc.code,\
atc.l1_code,\
atc.l1_name,\
atc.l2_code,\
atc.l2_name,\
atc.l3_code,\
atc.l3_name,\
atc.l4_code,\
atc.l4_name,\
product.id,\
product.generic_name,\
product.product_name,\
product.ndc_product_code,\
structures.id,\
structures.cd_id,\
structures.cd_smiles,\
structures.name,\
target_dictionary.id,\
target_dictionary.name\
"
#
/home/app/d2rq/generate-mapping \
	-d org.postgresql.Driver \
	-u "www" \
	-p "foobar" \
	--columns "$columns" \
	-o $CONFIGFILE \
	--verbose \
	jdbc:postgresql://localhost/drugcentral
#
HOSTFQ=`hostname --fqdn`
#
###
# Edits:
#====================================================================================
#
TMPFILE="data/tmp.ttl"
rm -f $TMPFILE
touch $TMPFILE
#
cat $CONFIGFILE \
	| sed -e '/map:database/,$d' \
	>>$TMPFILE
#
cat <<__EOF__ >>$TMPFILE
#====================================================================================
@prefix d2r: <http://sites.wiwiss.fu-berlin.de/suhl/bizer/d2r-server/config.rdf#> .
  
<> a d2r:Server;
 	rdfs:label "D2RQ-DrugCentral Server";
 	d2r:baseURI <http://${HOSTFQ}:8080/d2rq_drugcentral/>;
 	d2r:port 8080;
 	d2r:vocabularyIncludeInstances true;
 	d2r:sparqlTimeout 300;
 	d2r:pageTimeout 5;
 	.
#====================================================================================
__EOF__
#
###
cat $CONFIGFILE \
	| sed -e '/map:database/,$!d' \
	>>$TMPFILE
#
###
#Joins:
#(Add PropertyBridges requiring d2rq:join statements)
#
cat <<__EOF__ >>$TMPFILE
#Manually added:
map:ProductActiveingredient a d2rq:PropertyBridge;
	d2rq:belongsToClassMap map:product;
	d2rq:property vocab:active_ingredient;
	d2rq:join "product.ndc_product_code => active_ingredient.ndc_product_code";
	.
__EOF__
#
#
mv $TMPFILE $CONFIGFILE
#
printf "D2RQ configfile: %s\n" $CONFIGFILE
#
#cp $CONFIGFILE ~/src/java/edu/unm/health/biocomp/drugcentral/d2rq/config.ttl
