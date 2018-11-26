##########################################################################################
### DrugCentral drug-target subnetwork analysis 
### visNetwork is a R package for network visualization, using vis.js
### javascript library (http://visjs.org).  JS library DataTables also used.
### 
### Targets may be single protein or protein complexes.
### Protein complexes represented by multiple linked nodes.
### A single protein may belong to multiple complexes (e.g. "GABA A receptor
### alpha-3/beta-2/gamma-2" and "GABA-A receptor alpha-5/beta-3/gamma-2").
### 
### Activities are drug-target, whether single protein or protein complex targets.
### So protein complex targets must be represented as nodes (could be hierarchical nodes).
### 
### Jeremy Yang
##########################################################################################
require(visNetwork, quietly = T)
require(RPostgreSQL, quietly = T)
require(data.table, quietly = T)
require(png, quietly = T)
require(rcdk, quietly = T)

#qry <- "diazepam" #has protein complexes
#qry <- "statin"
#qry <- "testosterone"
qry <- "metformin"
tdata_file <- "tdata.csv"
network_file <- "visnetwork.html"

find_tgts <- function(qry) {
  
  host="localhost"
  con_dc <- dbConnect(PostgreSQL(), host=host, dbname="drugcentral", user="jjyang", password="assword")
  sql <- paste(
    "SELECT DISTINCT",
    "s.id AS \"struct_id\",",
    "s.name AS \"struct_name\",",
    "s.cd_smiles AS \"smiles\",",
    "s.cd_formula AS \"formula\",",
    "a.relation,",
    "a.act_type,",
    "a.act_value,",
    "a.act_unit,",
    "td.id AS \"target_id\",",
    "td.name AS \"target_name\",",
    "td.target_class,",
    "td.protein_type,",
    "tc.id AS \"tc_id\",",
    "tc.accession,",
    "tc.swissprot,",
    "tc.organism,",
    "tc.gene,",
    "tc.geneid,",
    "tc.name AS \"tc_name\"",
    "FROM",
    "structures s,",
    "activities a,",
    "target_dictionary td,",
    "target_component tc,",
    "td2tc",
    "WHERE",
    "s.id = a.struct_id",
    "AND a.target_id = td.id",
    "AND td.id = td2tc.target_id",
    "AND tc.id = td2tc.component_id",
    "AND tc.organism = 'Homo sapiens'",
    "AND UPPER(s.name) LIKE", paste0("'%",toupper(qry),"%'"),
    "ORDER BY s.id, td.id"
  )

  results <- dbSendQuery(con_dc,sql)
  tdata <- dbFetch(results, colClasses="character")
  dbClearResult(results)
  dbDisconnect(con_dc)
  write.csv(tdata, file = tdata_file)
  return(tdata)
}


#if (!file.exists(tdata_file)) {
if (TRUE) {
  message("DEBUG: qry = ", qry)
  tdata <- find_tgts(qry)
} else {
  message("DEBUG: reading ", tdata_file)
  tdata <- read.csv(tdata_file)
}

tdata <- data.table(tdata)
tdata$act_value <- round(as.numeric(tdata$act_value), digits = 2)

drugs <- unique(subset(tdata, select = c("struct_id", "struct_name", "formula", "smiles")))
n_drug <- nrow(drugs)

#SINGLE PROTEIN targets:
tgts_sp <- unique(subset(tdata[tdata$protein_type == "SINGLE PROTEIN", ], select = c("target_id", "target_name", "target_class", "accession","tc_name", "protein_type", "organism", "gene", "geneid")))
n_tgt_sp <- nrow(tgts_sp)

#PROTEIN COMPLEX targets:
tgts_pc <- unique(subset(tdata[tdata$protein_type == "PROTEIN COMPLEX", ], select = c("target_id", "target_name", "target_class", "protein_type")))
n_tgt_pc <- nrow(tgts_pc)
prots_pc <- unique(subset(tdata[tdata$protein_type == "PROTEIN COMPLEX", ], select = c("tc_id", "accession", "tc_name", "organism", "gene", "geneid")))
n_prot_pc <- nrow(prots_pc)
#PROTEIN COMPLEX target-protein links:
tgts_pc2p <- unique(subset(tdata[tdata$protein_type == "PROTEIN COMPLEX", ], select = c("target_id", "tc_id")))
n_pc2p <- nrow(tgts_pc2p)

#ACTIVITIES:
acts <- unique(subset(tdata, select = c("struct_id","target_id","act_type","act_value","relation")))
n_act <- nrow(acts)
acts$id <- 1:n_act

message(sprintf("drugs: %d", n_drug))
message(sprintf("activities: %d", nrow(tdata)))
message(sprintf("single protein targets: %d", n_tgt_sp))
message(sprintf("protein complex targets: %d", n_tgt_pc))
message(sprintf("total targets: %d", n_tgt_sp+n_tgt_pc))
message(sprintf("proteins in complex targets: %d", n_prot_pc))

    
###Convert data to VisNetwork nodes and edges.
node_ids <- c(paste0("D",drugs$struct_id),paste0("T",tgts_sp$target_id))
node_labels <- c(drugs$struct_name, tgts_sp$gene)
node_titles <- c(paste(drugs$struct_name, drugs$struct_id, drugs$formula, drugs$smiles, sep = "<br>"),
		paste(tgts_sp$gene, tgts_sp$target_name, tgts_sp$protein_type, tgts_sp$target_class, tgts_sp$target_id, tgts_sp$geneid, sep = "<br>" ))
node_shapes <- c(rep("square", n_drug), rep("circle", n_tgt_sp))
node_colors <- c(rep("orange", n_drug), rep("cyan", n_tgt_sp))
node_groups <- c(rep("drug", n_drug), rep("target", n_tgt_sp))
if (n_tgt_pc>0)
{
  node_ids <- c(node_ids, paste0("T",tgts_pc$target_id),paste0("P",prots_pc$tc_id))
  node_labels <- c(node_labels, tgts_pc$target_id, prots_pc$gene)
  node_titles <- c(node_titles,
		paste(tgts_pc$target_name, tgts_pc$protein_type, tgts_pc$target_class, tgts_pc$target_id, sep = "<br>" ),
		paste(prots_pc$gene, prots_pc$tc_name, prots_pc$accession, prots_pc$tc_id, prots_pc$organism, prots_pc$geneid, sep = "<br>" ))
  node_shapes <- c(node_shapes, rep("star", n_tgt_pc), rep("circle", n_prot_pc))
  node_colors <- c(node_colors, rep("gray", n_tgt_pc), rep("cyan", n_prot_pc))
  node_groups <- c(node_groups, rep("target", n_tgt_pc), rep("protein", n_prot_pc))
}
nodes <- data.frame(id = node_ids)
nodes$label <- node_labels
nodes$shape <- node_shapes
nodes$color <- node_colors
nodes$group <- node_groups
nodes$title <- node_titles
message(sprintf("DEBUG: nodes = %d", nrow(nodes)))

edge_ids <- paste0("A",acts$id)
edge_froms <- paste0("D",acts$struct_id)
edge_tos <- paste0("T",acts$target_id)
edge_titles <- paste(acts$act_type, acts$relation, acts$act_value)
edge_lengths <- rep(300, n_act)
if (n_tgt_pc>0)
{
  edge_ids <- c(edge_ids, paste("T2P", tgts_pc2p$target_id, tgts_pc2p$tc_id, sep = "_"))
  edge_froms <- c(edge_froms, paste0("T",tgts_pc2p$target_id))
  edge_tos <- c(edge_tos, paste0("P", tgts_pc2p$tc_id))
  edge_titles <- c(edge_titles, rep(NA, n_pc2p))
  edge_lengths <- c(edge_lengths, rep(50, n_pc2p))
}
edges <- data.frame(id = edge_ids, from = edge_froms, to = edge_tos)
edges$title <- edge_titles
edges$length <- edge_lengths

message(sprintf("DEBUG: edges = %d", nrow(edges)))

visNetwork(nodes, edges, width = "100%") %>%
	visEdges(width = 3, color = "gray") %>%
	visOptions(highlightNearest = list(enabled = TRUE, degree = 2)) %>%
	visConfigure(enabled = TRUE, filter = c("layout", "physics")) %>%
  visSave(file = network_file)

viewer <- getOption("viewer") #RStudio HTML viewer
if (!is.null(viewer)) {
  viewer(network_file)
}

message("DEBUG: network output file: ", network_file)

drugs$smiles <- gsub("\\s.*$", "", drugs$smiles)
mols <- parse.smiles(drugs$smiles)
#view.table is fragile.  Needs non-NA strings.
#view.table(mols, drugs, cellx=100, celly=60)
view.molecule.2d(mols)
molimg <- view.image.2d(mols[[1]], width = 300, height = 300)
writePNG(molimg, target = "molimg.png",
  text=c(source=R.version.string), metadata=sessionInfo())
plot(c(0, 300), c(0, 300), asp = 1.0, type = "n", xlab = "", ylab = "")
rasterImage(molimg, 0, 0, 300, 300)
#copy.image.to.clipboard(mols[[1]], width = 300, height = 300) #PNG
###
