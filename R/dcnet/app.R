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
### See dcnet_getdata.R to fetch datafile from db.
### 
### Jeremy Yang
##########################################################################################
library(readr)
library(shiny, quietly = T)
library(data.table, quietly = T)
library(visNetwork, quietly = T)
library(rcdk, quietly = T)
library(png, quietly = T)

deph <- 120
depw <- 120


#############################################################################
tdata_all <- read_delim("dtdata.tsv.gz", "\t")

#############################################################################
ui <- fluidPage(
  title = "DrugCentral drug-target networks",
  
  fluidRow(
    column(2, 
           style = "background-color:#dddddd; height:500px",
           HTML("<H3>DrugCentral drug-target networks</H3>"),
           hr(),
           textInput("qry", label = "Query:", value = "metformin"),
           HTML("Examples: "), 
           actionLink("ex1", "diazepam"), 
           actionLink("ex2", "metformin"),
           actionLink("ex3", "statin"),
           actionLink("ex4", "testosterone"),
           br(),
           submitButton(text = "Search"), p(),
           hr(),
           HTML("<b>Style:</b>"), p(),
           div(id = "controlpanel") #How to refer to this DOM element?
           ),
    column(8, visNetworkOutput("network", height = "500px")), #400px|auto
    column(2,
           style = "background-color:#dddddd; height:500px",
           HTML("<b>Depiction:</b>"),
           imageOutput("molimg1", height= deph))),
  hr(),

  fluidRow(
    column(1, HTML("<b>Results:</b>")),
    column(11, htmlOutput(outputId = "results", height = "60px"))
	),
  hr(),
  
  fluidRow(
    column(1, HTML("<b>Compounds:</b>")),
    column(11, dataTableOutput("cpds"))),
  hr(),

  fluidRow(
    column(1, HTML("<b>Targets (single protein):</b>")),
    column(11, dataTableOutput("tgts_sp"))),
  hr(),

  fluidRow(
    column(1, HTML("<b>Targets (protein complex):</b>")),
    column(11, dataTableOutput("tgts_pc"))),
  hr(),

  fluidRow(
    column(1, HTML("<b>Proteins (protein complex):</b>")),
    column(11, dataTableOutput("prots_pc"))),
  hr(),
  
  fluidRow(
    column(12, HTML("Web app build with R-shiny, visNetwork, vis.js, and rcdk."))
  )
)


#############################################################################
find_dtnet <- function(qry) {
  tdata_this <- tdata_all[grepl(qry, tdata_all$struct_name) , ]
  return(tdata_this)
}

#############################################################################
server <- function(input, output, session) {

  output$network <- renderVisNetwork({

    message("DEBUG: qry = ", input$qry)
    tdata <- find_dtnet(input$qry)
    if (is.na(tdata) || nrow(tdata)==0)
    {
      output$status <- renderText("No hits found.")
      message("DEBUG: No hits found.")
      return(NULL)
    }
    tdata <- data.table(tdata)
    tdata$act_value <- round(as.numeric(tdata$act_value), digits = 2)
    tdata$act_unit[is.na(tdata$act_unit)] <- ""
    drugs <- unique(subset(tdata, select = c("struct_id", "struct_name", "formula", "smiles")))
    n_drug <- nrow(drugs)

    drugs$smiles <- gsub("\\s.*$", "", drugs$smiles)
    mols <- parse.smiles(drugs$smiles)

      molimg <- renderImage({
        img <- view.image.2d(mols[[1]], depictor = get.depictor(width=depw, height=deph))
        fimg <- tempfile(fileext = ".png")
        writePNG(img, target=fimg, metadata=sessionInfo())  
        list(src=fimg, contentType="image/png", width=depw, height=deph)
      }, deleteFile = T)
      
    output$molimg1 <-  molimg
    #How to do several?
    
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
    acts <- unique(subset(tdata, select = c("struct_id","accession","target_id","act_type","act_value","act_unit","relation")))
    n_act <- nrow(acts)
    acts$id <- 1:n_act

    result_txt <- paste(sep = " ; ",
                        sprintf("drugs: %d", n_drug),
                        sprintf("activities: %d", nrow(tdata)),
                        sprintf("single protein targets: %d", n_tgt_sp),
                        sprintf("total targets: %d", n_tgt_sp+n_tgt_pc))
    if (n_tgt_pc>0)
    {
      result_txt <- paste(sep = " ; ",
                          result_txt,
                          sprintf("protein complex targets: %d", n_tgt_pc),
                          sprintf("proteins in complex targets: %d", n_prot_pc))
    }
    output$results <- renderText(result_txt)
    message(result_txt)

    output$cpds <- renderDataTable(drugs, options = list(pageLength=5, lengthChange=F, pagingType="simple", searching=F, info=T))
    output$tgts_sp <- renderDataTable(tgts_sp, options = list(pageLength=5, lengthChange=F, pagingType="simple", searching=F, info=T))
    output$tgts_pc <- renderDataTable(tgts_pc, options = list(pageLength=5, lengthChange=F, pagingType="simple", searching=F, info=T))
    output$prots_pc <- renderDataTable(prots_pc, options = list(pageLength=5, lengthChange=F, pagingType="simple", searching=F, info=T))
    
    ###Convert data to VisNetwork nodes and edges.
    node_ids <- c(paste0("D",drugs$struct_id),paste0("T",tgts_sp$target_id))
    node_labels <- c(drugs$struct_name, tgts_sp$gene)
    node_titles <- c(paste(drugs$struct_name, drugs$struct_id, drugs$formula, drugs$smiles, sep = "<br>"),
                paste(tgts_sp$gene, tgts_sp$target_name, tgts_sp$protein_type,
    tgts_sp$target_class, tgts_sp$target_id, tgts_sp$geneid, sep = "<br>" ))
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
    nodes$title <- node_titles
    nodes$shape <- node_shapes
    nodes$color <- node_colors
    nodes$group <- node_groups
    message(sprintf("DEBUG: nodes = %d", nrow(nodes)))

    edge_ids <- paste0("A",acts$id)
    edge_froms <- paste0("D",acts$struct_id)
    edge_tos <- paste0("T",acts$target_id)
    edge_titles <- paste(acts$act_type, acts$relation, paste0(acts$act_value, acts$act_unit))
    edge_lengths <- rep(300, n_act)
    if (n_tgt_pc>0)
    {
      edge_ids <- c(edge_ids, paste("T2P", tgts_pc2p$target_id, tgts_pc2p$tc_id, sep =
    "_"))
      edge_froms <- c(edge_froms, paste0("T",tgts_pc2p$target_id))
      edge_tos <- c(edge_tos, paste0("P", tgts_pc2p$tc_id))
      edge_titles <- c(edge_titles, rep(NA, n_pc2p))
      edge_lengths <- c(edge_lengths, rep(50, n_pc2p))
    }
    edges <- data.frame(id = edge_ids, from = edge_froms, to = edge_tos)
    edges$title <- edge_titles
    edges$length <- edge_lengths

    message(sprintf("DEBUG: edges = %d", nrow(edges)))

    net <- visNetwork(nodes, edges, width = "100%",
                      main = list(text = paste("DrugCentral drug-target network:", input$qry),
                                  style = "font-family:sans-serif;color:#333333;font-size:14px;text-align:center;")
                      ) %>%
    	visEdges(width = 3, color = "gray") %>%
    	visOptions(highlightNearest = list(enabled = TRUE, degree = 2))
      #visConfigure(net, enabled = TRUE, filter = c("layout"), showButton = FALSE) #How to add container param?
  })
}


###
shinyApp(ui, server)
