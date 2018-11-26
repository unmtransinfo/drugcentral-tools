##########################################################################################
### DrugCentral drug-target subnetwork analysis 
### visNetwork is a R package for network visualization, using vis.js
### javascript library (http://visjs.org).  JS library DataTables also used.
### 
### 
### Jeremy Yang
##########################################################################################
require(shiny, quietly = T)
require(visNetwork, quietly = T)
library(rcdk, quietly = T)

depw <- 120
deph <- 120

ui <- fluidPage(
  #includeScript("dcnet.js"),
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
