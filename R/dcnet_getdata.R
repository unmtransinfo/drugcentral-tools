#!/usr/bin/env Rscript
###
library(readr)
#library(data.table, quietly=T)
library(RPostgreSQL, quietly=T)



dbcon <- dbConnect(PostgreSQL(), host="localhost", dbname="drugcentral")
sql <- paste0(readLines(file("sql/dcnet.sql")), collapse="\n")
writeLines(sql)

tdata <- dbGetQuery(dbcon, sql)

writeLines(sprintf("Target count: %d", length(unique(tdata$target_id))))
writeLines(sprintf("Drug compound count: %d", length(unique(tdata$struct_id))))


dbDisconnect(dbcon)
write_delim(tdata, "data/dtdata.tsv", "\t")



