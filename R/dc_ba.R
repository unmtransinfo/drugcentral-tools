#############################################################################################
### DrugCentral target selectivity analysis
#############################################################################################
library(RPostgreSQL)
library(rcdk)

pretitle <- "DrugCentral"

t0 <- proc.time()

orig.par <- par(no.readonly=TRUE)

###
#Compounds:
###
#
con_dc <- dbConnect(PostgreSQL(), host="lengua.health.unm.edu", dbname="drugcentral")
sql <- paste(
  "SELECT DISTINCT",
  "m.id,",
  "mol_to_smiles(m.mol)::varchar(500) AS \"smiles\",",
  "s.name",
  "FROM public.structures s",
  "JOIN public.mols m ON (s.id = m.id)"
)

results <- dbSendQuery(con_dc,sql)
mols <- dbFetch(results, colClasses="character")
dbClearResult(results)
print(sprintf("compounds = %d\n",nrow(mols)))

###
#Scaffolds and Badapple scores:
###
ba <- read.delim("data/drugcentral_ba.smiles", header=FALSE, stringsAsFactors=FALSE)
colnames(ba) <- c("smiles", "fields", "scores", "scafids", "scafsmis")
ba$scafsmis <- NULL

ba$mid <- rep(NA,nrow(ba))
ba$maxscore <- rep(NA,nrow(ba))
ba$maxscafid <- rep(NA,nrow(ba))

fields <- strsplit(ba$fields," ")
scafids <- strsplit(ba$scafids,",")
scores <- strsplit(ba$scores,",")

n_scaf <- 0

for (i in 1:nrow(ba))
{
  mid <- fields[[i]][1]
  ba$mid[i] <- mid
  scafids_this <- scafids[[i]]
  n_scaf_this <- length(scafids_this)
  if (n_scaf_this==0) {
    next
  }
  if (n_scaf==0) {
    m2s <- data.frame(mid=mid,scafid=scafids_this)
  } else {
    m2s <- rbind(m2s, data.frame(mid=mid,scafid=scafids_this))
  }
  n_scaf = n_scaf + n_scaf_this
  
  scores_this <- as.numeric(scores[[i]])
  if (is.na(scores_this)) {
    next
  }
  j_max <- which.max(scores_this)
  ba$maxscore[i] <- scores_this[j_max]
  ba$maxscafid[i] <- scafids_this[j_max]
}
ba$fields <- NULL

scafs <- read.csv("data/drugcentral_ba_scaf.csv", stringsAsFactors=FALSE)
scafs$baScore <- as.numeric(scafs$pScore)
scafs$pScore <- NULL
print(sprintf("scaffolds = %d\n",nrow(scafs)))

###
#Targets, Activity:
#act_table_full is pre-joined with tgt/protein data.
###
sql <- "SELECT * FROM act_table_full ORDER BY struct_id"
results <- dbSendQuery(con_dc,sql)
activity <- dbFetch(results, colClasses="character")
dbClearResult(results)
print(sprintf("activity: Nactivity = %d\n",nrow(activity)))
print(sprintf("activity: Nstructures = %d\n",length(unique(activity$struct_id))))
print(sprintf("activity: Ntargets = %d\n",length(unique(activity$target_id))))

par(mar=c(18,4,4,2), cex=0.6)
barplot(table(activity$action_type), las=3, col="cyan", main=sprintf("%s: Activity count by Action type", pretitle))
barplot(table(activity$act_type), las=3, col="orange", main=sprintf("%s: Activity count by result type", pretitle))
barplot(table(activity$moa_source), las=3, col="maroon", main=sprintf("%s: Activity count by MOA source", pretitle))

par(orig.par)

#Targets per compound:
mols$ntgt <- 0 #new column
for (i in 1:nrow(mols))
{
  mols$ntgt[i] <- length(unique(activity$target_id[activity$struct_id == mols$id[i]]))
}
#hist(mols$ntgt, ylab="Compound count", xlab="Ntargets", col="pink", main=sprintf("%s: Targets per compound", pretitle))
t <- table(pmin(mols$ntgt,10))
names(t) <- c(0:9,"10+")
barplot(t, ylab="Compound count", xlab="Ntargets", col="pink", main=sprintf("%s: Targets per compound", pretitle))

#Compunds, targets per scaffold:
scafs$ntgt <- 0 #new column
scafs$ncpd <- 0 #new column
for (i in 1:nrow(scafs))
{
  mids_this <- m2s$mid[m2s$scafid == scafs$scafId[i]]
  n <- 0
  for (mid in mids_this)
  {
    n_this <- mols$ntgt[mols$id == mid]
    if (length(n_this) == 0)
    {
      next
    }
    scafs$ncpd[i] <- scafs$ncpd[i] + 1
    n <- n + n_this
  }
  scafs$ntgt[i] <- n
}
#hist(scafs$ntgt, main=sprintf("%s: Targets per scaffold", pretitle), xlab="Ntargets", ylab="Scaffold count", col="limegreen")
t <- table(pmin(scafs$ntgt,30))
names(t) <- c(0:29,"30+")
barplot(t, ylab="Scaffold count", xlab="Ntargets", col="limegreen", main=sprintf("%s: Targets per scaffold", pretitle))

#Sort by promiscuity:
scafs <- scafs[order(-scafs$ntgt),]

y_halfact <- sum(scafs$ncpd)/2
x_halfact <- which(abs(cumsum(scafs$ncpd) - y_halfact) == min(abs(cumsum(scafs$ncpd) - y_halfact)))

#Top promiscuous scaffolds:
Ntop <- x_halfact
for (i in 1:Ntop)
{
  print(sprintf("%2d. [nTgt = %4d ; nCpd = %4d] %6s: %s\n", i, scafs$ntgt[i], scafs$ncpd[i], scafs$scafId[i], scafs$scafSmi[i]))
}

plot(1:nrow(scafs),
     cumsum(scafs$ncpd),
     col="red",
     main=sprintf("%s: Scaffold activity ROC\n(activity = # MOC drug-target pairs)", pretitle),
     sub=sprintf("Nscaf = %d ; Nactivity = %d", nrow(scafs), sum(scafs$ncpd)),
     xlab="Scafs in order of decreasing activity",
     ylab="Cumulative activity count")

abline(h = y_halfact, col="cyan")
abline(v = x_halfact, col="cyan")
text(x_halfact, y_halfact*0.9, sprintf("50%% of activity by %d top scaffolds", x_halfact), pos=4, offset=1)

#view.molecule.2d(mols)

#view.table is fragile.  Needs non-NA strings.
moldata <- subset(scafs[1:Ntop,], select = c("scafId", "baScore", "ntgt", "ncpd"))
moldata <- cbind(data.frame(rank = 1:Ntop), moldata)
for (colname in names(moldata))
{
  moldata[colname][[1]] <- as.character(moldata[colname][[1]])
  moldata[colname][is.na(moldata[colname])] <- ""
}
view.table(parse.smiles(scafs$scafSmi[1:Ntop]), moldata, cellx=100, celly=60)


###
#Drug classes:
sql <- "SELECT * FROM struct2atc"
results <- dbSendQuery(con_dc,sql)
struct2atc <- dbFetch(results, colClasses="character")
dbClearResult(results)

mols <- merge(mols, struct2atc, by.x="id", by.y="struct_id")
print(sprintf("molecules: %d ; unique ATC codes: %d", nrow(mols), length(unique(mols$atc_code))))
print(sprintf("molecules: %d ; unique ATC_L4 codes: %d", nrow(mols), length(unique(substr(mols$atc_code, 1, 5)))))
print(sprintf("molecules: %d ; unique ATC_L3 codes: %d", nrow(mols), length(unique(substr(mols$atc_code, 1, 4)))))
print(sprintf("molecules: %d ; unique ATC_L2 codes: %d", nrow(mols), length(unique(substr(mols$atc_code, 1, 3)))))
print(sprintf("molecules: %d ; unique ATC_L1 codes: %d", nrow(mols), length(unique(substr(mols$atc_code, 1, 1)))))

sql <- "SELECT * FROM atc ORDER BY code"
results <- dbSendQuery(con_dc,sql)
atc <- dbFetch(results, colClasses="character")
dbClearResult(results)

atc3 <- unique(subset(atc, select=c("l3_code","l3_name")))

for (i in 1:nrow(atc3))
{
  code <- atc3$l3_code[i]
  name <- atc3$l3_name[i]
  struct_ids <- mols$id[substr(mols$atc_code, 1, 4) == code]
  target_ids <- activity$target_id[activity$struct_id %in% struct_ids]
  print(sprintf("ATC [Ncpd = %3d ; Ntgt = %3d] %s: %s", length(struct_ids), length(unique(target_ids)), code, name))
}

sql <- "SELECT * FROM pharma_class"
results <- dbSendQuery(con_dc,sql)
pharma_class <- dbFetch(results, colClasses="character")
dbClearResult(results)
print(table(pharma_class$source, pharma_class$type))

fda_class <- subset(pharma_class, source=="FDA", select=c("struct_id", "type", "class_code", "name"))
fda_class <- fda_class[order(fda_class$class_code),]

for (type in unique(fda_class$type))
{
  print(sprintf("FDA %s classes: %4d ; compounds: %4d", type,
                length(unique(fda_class$class_code[fda_class$type == type])),
                length(unique(fda_class$struct_id[fda_class$type == type]))))
}

for (code in unique(fda_class$class_code[fda_class$type == "MoA"]))
{
  name <- fda_class$name[fda_class$class_code == code][1]
  struct_ids <- fda_class$struct_id[fda_class$class_code == code]
  target_ids <- activity$target_id[activity$struct_id %in% struct_ids]
  print(sprintf("FDA [Ncpd = %3d ; Ntgt = %3d] %s: %s", length(struct_ids), length(unique(target_ids)), code, name))
}

###
#Target promiscuity:





#options("java.parameters"=c("-Xmx4000m"))

#jcall("java/lang/System","V","gc")
#gc()
  

###
#dbDisconnect(con_dc)

print(sprintf("elapsed time (total): %.2fs",(proc.time()-t0)[3]))
