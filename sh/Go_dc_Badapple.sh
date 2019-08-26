#!/bin/sh
#
# Badapple analysis for DrugCentral compounds.
#
#
DATADIR="data"
smifile="$DATADIR/drugcentral.smiles"
outfile="$DATADIR/drugcentral_ba.sdf"
#
badapple.sh \
	-process_mols \
	-i $smifile \
	-o $outfile \
	-v
#
###
#
ba_sdf_utils.py
	--i $outfile \
	--o $DATADIR/drugcentral_ba_scaf.csv \
	--o_m2s $DATADIR/drugcentral_ba_m2s.csv
#
