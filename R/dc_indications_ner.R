#############################################################################################
### DrugCentral indications NER
### Use tm package, MeSH disease terms as dictionary.
#############################################################################################
library(RPostgreSQL, quietly = T)
library(data.table, quietly = T)
library(dplyr, quietly = T)
library(plotly, quietly = T)
library(tm, quietly = T)
library(RWeka, quietly = T)
library(MASS, quietly = T)

t0 <- proc.time()

###
#
ind_txt <- read.csv("~/projects/drugcentral/data/label_indications.csv", stringsAsFactors=FALSE)
print(sprintf("DEBUG: nrow(ind_txt) = %d",nrow(ind_txt)))
#
#
dbcon <- dbConnect(PostgreSQL(), host="localhost", dbname="drugcentral", user = "jjyang", password = "assword")
#
sql <- "SELECT DISTINCT
  s.id AS \"struct_id\",
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
WHERE
  p.marketing_status IN ('NDA','ANDA')
  AND l.category LIKE '%HUMAN PRESCRIPTION%'
  AND p.active_ingredient_count = 1"
#
results <- dbSendQuery(dbcon,sql)
drug2label <- dbFetch(results, colClasses="character")
dbClearResult(results)
#
ok <- dbDisconnect(dbcon)
#
print(sprintf("DEBUG: nrow(drug2label) = %d",nrow(drug2label)))
print(sprintf("DEBUG: n_drug = %d",length(unique(drug2label$struct_id))))
#
ind_txt <- merge(drug2label, ind_txt, all.x = F, all.y = F, by.x = "label_id", by.y = "LABEL_ID")
#
### Consider only one product/label per drug (longest).
ind_txt$len <- nchar(ind_txt$TEXT)
ind_txt <- ind_txt[order(ind_txt$struct_id, ind_txt$len, decreasing = c(F,T)), ]
for (i in 2:nrow(ind_txt))
{
  if (ind_txt$name[i] == ind_txt$name[i-1]) {
    ind_txt$label_id[i] <- NA
  }
}
ind_txt <- ind_txt[!is.na(ind_txt$label_id),]
#
###
meshterms <- read.csv("~/projects/mesh/data/disease_terms.csv", header=T)
meshterms$term <- tolower(meshterms$term)
#
# Add "A B" for all "B, A" (e.g. "Anemia, Sickle Cell" -> "Sickle Cell Anemia")
# Also "C, A, B" -> "A B C" (e.g. Lymphoma, T-Cell, Peripheral" -> "Peripheral T-Cell Lymphoma")
mt2 <- meshterms[grep(",", meshterms$term, value=F), ]
mt2$term <- sub("^(.*),\\s*(.*),\\s*(.*)$", "\\3 \\2 \\1", mt2$term, perl = T)
mt2$term <- sub("^(.*),\\s*(.*)$", "\\2 \\1", mt2$term, perl = T)
mt2 <- mt2[grep(",", mt2$term, invert=T),]
meshterms <- rbind(meshterms, mt2)
meshterms <- meshterms[order(meshterms$id), ]
###
#Define corpus:
vs <- VectorSource(ind_txt$TEXT)
vc <-VCorpus(vs)
meta(vc,"name","local") <- ind_txt$name
#
#ctrl <- list(tokenize = strsplit_space_tokenizer,
#             removePunctuation = list(preserve_intra_word_dashes = TRUE),
#             stopwords = c("reuter", "that"),
#             stemming = TRUE,
#             wordLengths = c(4, Inf))
#
ctrl <- list(dictionary = meshterms$term, tolower = T)
#
tdm <- TermDocumentMatrix(vc, control = ctrl)
#
print(paste("tdm nDocs x nTerms: ",nDocs(tdm), " x ",nTerms(tdm)))
#
n <- min(100,floor(nTerms(tdm)/4))
i_sample <- floor((nTerms(tdm)/n)*(1:n))
inspect(tdm[i_sample,1:4])
#
#What are the top terms? Doc count per term?
fterms <- findFreqTerms(tdm, lowfreq = 1)
for (i in seq_along(fterms))
{
  dvec <- as.vector(tdm[fterms[i], ])
  w <- which(dvec > 0)
  if (length(w) > 0)
  {
    print(sprintf("%d. %18s (%d): %s\n", i, fterms[i], length(w), paste(ind_txt$name[w], collapse = "; ")))
  }
}
#Top docs? Term count per doc?
docstats <- data.frame(name = ind_txt$name, nterms = rep(0, length(vc)))
for (i in seq_along(vc))
{
  doc <- PlainTextDocument(vc[i])
  drugname <- meta(vc[i], "name", "local")
  tfv <- termFreq(doc, control = ctrl)
  w <- which(tfv > 0)
  if (length(w)>0)
  {
    print(sprintf("%d. %18s (%d): %s\n", i, drugname, length(w), paste(names(w), collapse="; ")))
  }
  docstats$nterms[i] <- length(w)
}
docstats <- docstats[order(-docstats$nterms), ]
#
write(meshterms$term, file = "data/mesh_disease_lower.txt")
#
#
#
print(sprintf("elapsed time (total): %.2fs",(proc.time()-t0)[3]))
