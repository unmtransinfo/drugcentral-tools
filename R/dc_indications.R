#############################################################################################
### DrugCentral indications analysis
#############################################################################################
library(RPostgreSQL, quietly = T)
library(data.table, quietly = T)
library(dplyr, quietly = T)
library(plotly, quietly = T)
library(openNLP, quietly = T)
library(NLP, quietly = T)


t0 <- proc.time()

###
#
###
dbcon <- dbConnect(PostgreSQL(), host="localhost", dbname="drugcentral", user = "jjyang", password = "assword")
results <- dbSendQuery(dbcon,"SELECT * FROM dbversion")
dbver <- dbFetch(results, colClasses="character")
dbClearResult(results)
print(sprintf("DrugCentral version: %s (%s)\n", dbver$version[1], dbver$dtime[1]))
#
###
#Get drug-label mappings.
#
sql <- "SELECT DISTINCT
  s.id AS \"struct_id\",
  st.type,
  s.name,
  p2l.label_id
FROM
  product AS p
JOIN
  prd2label p2l ON p.ndc_product_code = p2l.ndc_product_code
JOIN
  label l ON l.id = p2l.label_id
JOIN
  active_ingredient ai ON ai.ndc_product_code = p.ndc_product_code
JOIN
  structures s ON ai.struct_id = s.id
JOIN
  structure_type st ON st.struct_id = s.id
WHERE
  p.active_ingredient_count = 1"
#
#  AND p.marketing_status IN ('NDA','ANDA')
#  AND l.category LIKE '%HUMAN PRESCRIPTION%'
#  category 'HUMAN OTC%'
results <- dbSendQuery(dbcon,sql)
dlab <- dbFetch(results, colClasses="character")
dbClearResult(results)
print(sprintf("DEBUG: nrow(dlab) = %d",nrow(dlab)))
print(sprintf("DEBUG: n_drug = %d",length(unique(dlab$struct_id))))
#
###
#Full label indications texts (from DailyMed).
#(Now available from db.)
###
sql <- "SELECT label_id, text FROM section WHERE title = 'INDICATIONS & USAGE SECTION'"
results <- dbSendQuery(dbcon,sql)
dind <- dbFetch(results, colClasses="character")
dbClearResult(results)
print(sprintf("DEBUG: nrow(dind) = %d",nrow(dind)))
#
#Contraindications
###
sql <- "SELECT label_id, text FROM section WHERE title = 'CONTRAINDICATIONS SECTION'"
results <- dbSendQuery(dbcon,sql)
cind <- dbFetch(results, colClasses="character")
dbClearResult(results)
print(sprintf("DEBUG: nrow(cind) = %d",nrow(cind)))
cind$text <- sub("^\\d*\\.?\\s*CONTRAINDICATIONS?:?\\s*", "", cind$text, ignore.case = T, perl = T)
names(cind)[names(cind) == "text"] <- "text_cind"
#
dind <- merge(dlab, dind, all.x = F, all.y = F, by.x = "label_id", by.y = "label_id")
#
dind$text <- sub("^\\d*\\.?\\s*INDICATIONS? (AND|&) USAGE:?\\s*", "", dind$text, ignore.case = T, perl = T)
#
dind$cc <- nchar(dind$text)
dind <- dind[order(dind$struct_id, dind$cc, decreasing = c(F,T)), ]
###
# For this analysis consider only one product/label (longest) per drug.
#
for (i in 2:nrow(dind))
{
  if (dind$name[i] == dind$name[i-1]) {
    dind$label_id[i] <- NA
  }
}
dind <- dind[!is.na(dind$label_id),]
dind <- merge(dind, cind, by = "label_id", all.x = T, all.y = F)
print(sprintf("DEBUG: nrow(dind) = %d (FILTERED, ONE LABEL PER STRUCTURE)",nrow(dind)))
rm(cind)
dind$cc_ci <- nchar(dind$text_ci)
#
qtl <- quantile(dind$cc, probs = c(0, .25, .50, .75, seq(0.9, 1, 0.01)))
print(sprintf("nchar: N=%d ; range: [%d,%d] ; mean = %.1f\n",
	nrow(dind), qtl["0%"], qtl["100%"], mean(dind$cc)))
for (i in 1:length(qtl))
{
  print(sprintf("%5s-ile: %7d\n", names(qtl)[i], as.integer(qtl[i])))
}
#
###
#Word & sentence counts using openNLP.
msta <- Maxent_Sent_Token_Annotator()
mwta <- Maxent_Word_Token_Annotator()
dind$sc <- NA
dind$wc <- NA
for (i in 1:nrow(dind))
{
  s <- as.String(dind$text[i])
  a_s <- annotate(s, msta)
  dind$sc[i] <- length(a_s)
  a_w <- annotate(s, mwta, a_s)
  dind$wc[i] <- length(a_w)
}
#
qtl <- quantile(dind$wc, probs = c(0, .25, .50, .75, seq(0.9, 1, 0.01)))
print(sprintf("word count: N=%d ; range: [%d,%d] ; mean = %.1f\n",
	nrow(dind), qtl["0%"], qtl["100%"], mean(dind$wc)))
for (i in 1:length(qtl))
{
  print(sprintf("%5s-ile: %7d\n", names(qtl)[i], as.integer(qtl[i])))
}
#
qtl <- quantile(dind$sc, probs = c(0, .25, .50, .75, seq(0.9, 1, 0.01)))
print(sprintf("sentence count: N=%d ; range: [%d,%d] ; mean = %.1f\n",
	nrow(dind), qtl["0%"], qtl["100%"], mean(dind$sc)))
for (i in 1:length(qtl))
{
  print(sprintf("%5s-ile: %7d\n", names(qtl)[i], as.integer(qtl[i])))
}
#
for (i in head(order(dind$sc, decreasing=T), n=20))
{
  print(sprintf("%24s: cc: %6d ; cc_ci: %6d ; wc: %4d sc: %2d", dind$name[i],
	dind$cc[i], dind$cc_ci[i], dind$wc[i], dind$sc[i]))
}
#
p1 <- subplot(nrows = 1, shareX = F, shareY = T,
  plot_ly(name = "char count", x = dind$cc, type = "histogram", nbinsx = 20),
  plot_ly(name = "word count", x = dind$wc, type = "histogram", nbinsx = 20),
  plot_ly(name = "sentence count", x = dind$sc, type = "histogram", nbinsx = 20)
	) %>%
  layout(title = "Drug indication char, word & sentence count, from DrugCentral",
         yaxis = list(type = "log", title = "drug count"),
	font = list(family = "monospace"), showlegend = T)
#
p2 <- plot_ly(alpha = 0.6) %>%
  add_histogram(name = "char count", x = dind$cc) %>%
  add_histogram(name = "word count", x = dind$wc) %>%
  add_histogram(name = "sentence count", x = dind$sc) %>%
  layout(title = "Drug indication char, word & sentence count, from DrugCentral",
         xaxis = list(type = "log", title = "char/word/sentence counts"),
         yaxis = list(type = "log", title = "drug count"),
         font = list(family = "monospace"), showlegend = T,
         barmode = "overlay")
#
write.csv(dind[,c("label_id", "struct_id", "name", "type", "cc", "wc", "sc")],
	file = "data/label_indication_stats.csv", row.names = F)
#
ok <- dbDisconnect(dbcon)
#
print(sprintf("elapsed time (total): %.2fs",(proc.time()-t0)[3]))
#
