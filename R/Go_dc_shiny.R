#!/usr/bin/env Rscript
###
# See .Rprofile for port (3838).
###
require(shiny, quietly = T)
#
# /srv/shiny-server/dcnet/
#
#	port = getOption("shiny.port"),
runApp(appDir = paste0(Sys.getenv()["HOME"],'/projects/drugcentral/R/dcnet'),
	port = 9999,
	display.mode = "auto", launch.browser = T)
