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
LIBDIR=$HOME/src/java/lib
CLASSPATH=$LIBDIR/unm_biocomp_drugdb.jar
CLASSPATH=$CLASSPATH:$LIBDIR/unm_biocomp_jchemdb.jar
CLASSPATH=$CLASSPATH:$LIBDIR/unm_biocomp_db.jar
CLASSPATH=$CLASSPATH:$LIBDIR/unm_biocomp_text.jar
CLASSPATH=$CLASSPATH:$LIBDIR/unm_biocomp_util.jar
#
#CLASSPATH=$CLASSPATH:$APPDIR/ChemAxon-5.8.3/JChem/lib/jchem.jar
#
#(Derby lib included via jchem.jar.)
#
for fjar in `ls $APPDIR/ChemAxon/JChem/lib/jchem-*.jar` ; do
	CLASSPATH=$CLASSPATH:$fjar
done
for fjar in `ls $APPDIR/ChemAxon/JChem/lib/MarvinBeans-*.jar` ; do
	CLASSPATH=$CLASSPATH:$fjar
done
#
#CLASSPATH="$CLASSPATH:$APPDIR/ChemAxon/JChem/lib/jchem-*.jar"
#CLASSPATH="$CLASSPATH:$APPDIR/ChemAxon/JChem/lib/MarvinBeans-*.jar"
#
JAVA_OPTS="-Dderby.stream.error.field=System.err"
#JAVA_OPTS="-Dderby.stream.error.file=data/derby_utils.log"
#
java $JAVA_OPTS -classpath $CLASSPATH edu.unm.health.biocomp.drugdb.drugdb_utils $*
#
