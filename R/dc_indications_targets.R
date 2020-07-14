#!/usr/bin/env Rscript
###
### DrugCentral indications analysis
###
library(RPostgreSQL, quietly = T)
library(readr, quietly = T)
library(data.table, quietly = T)
library(plotly, quietly = T)


t0 <- proc.time()

###
#
###
dbcon <- dbConnect(PostgreSQL(), host="unmtid-dbs.net", port=5433, dbname="drugcentral", user = "drugman", password = "dosage")
dbver <- dbGetQuery(dbcon, "SELECT * FROM dbversion", colClasses="character")
print(sprintf("DrugCentral version: %s (%s)\n", dbver$version[1], dbver$dtime[1]))
#
###
#
sql <- read_file("sql/indication_targets.sql")
intgts <- dbGetQuery(dbcon, sql, colClasses="character")
setDT(intgts)
intgts[, moa := as.logical(!is.na(moa))]
message(sprintf("Total indications: %d; drugs: %d; targets: %d; ind-tgt-pairs: %d", 
                intgts[, uniqueN(umls_cui)], intgts[, uniqueN(struct_id)], intgts[, uniqueN(gene)], nrow(unique(intgts[, .(umls_cui, gene)]))))

message(sprintf("Total MOA indications: %d; drugs: %d; targets: %d; ind-tgt-pairs: %d", 
                intgts[(moa), uniqueN(umls_cui)], intgts[(moa), uniqueN(struct_id)], intgts[(moa), uniqueN(gene)], nrow(unique(intgts[(moa), .(umls_cui, gene)]))))
#
intgts_moa <- unique(intgts[(moa) & !is.na(gene), .(n_drug = uniqueN(struct_id)), by = c("omop_concept", "umls_cui", "gene", "target_id", "target_name")])[order(omop_concept, gene)]
#
ok <- dbDisconnect(dbcon)
#
print(sprintf("elapsed time (total): %.2fs",(proc.time()-t0)[3]))
#
