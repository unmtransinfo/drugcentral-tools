#!/bin/sh
#
if [ "`uname -s`" = "Darwin" ]; then
	APPDIR="/Users/app"
elif [ "`uname -s`" = "Linux" ]; then
	APPDIR="/home/app"
else
	APPDIR="/home/app"
fi
#
#
LIBDIR=$APPDIR/lib
#LIBDIR=$HOME/src/java/lib
CLASSPATH=$LIBDIR/unm_biocomp_drugcentral.jar
CLASSPATH=$CLASSPATH:$LIBDIR/unm_biocomp_cdk.jar
CLASSPATH=$CLASSPATH:$LIBDIR/unm_biocomp_db.jar
CLASSPATH=$CLASSPATH:$LIBDIR/unm_biocomp_text.jar
CLASSPATH=$CLASSPATH:$LIBDIR/unm_biocomp_util.jar
#
#CLASSPATH="$CLASSPATH:$APPDIR/cdk/jar/*"
#CLASSPATH="$CLASSPATH:$APPDIR/cdk/dist/jar/*"
CLASSPATH="$CLASSPATH:$APPDIR/lib/cdk-1.5.13-SNAPSHOT.jar"
CLASSPATH="$CLASSPATH:$APPDIR/lib/cdk-depict-1.5.13-SNAPSHOT.jar"
#
CLASSPATH="$CLASSPATH:$APPDIR/lib/postgresql-9.1-903.jdbc3.jar"
#
java $JAVA_OPTS -classpath $CLASSPATH edu.unm.health.biocomp.drugcentral.dc_utils $*
#
