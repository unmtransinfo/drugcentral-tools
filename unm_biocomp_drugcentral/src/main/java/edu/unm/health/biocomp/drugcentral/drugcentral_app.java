package edu.unm.health.biocomp.drugcentral;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.sql.*; //DriverManager,Driver,SQLException,Connection,Statement,ResultSet

import org.apache.commons.cli.*; // CommandLine, CommandLineParser, HelpFormatter, OptionBuilder, Options, ParseException, PosixParser
import org.apache.commons.cli.Option.*; // Builder

import com.fasterxml.jackson.dataformat.yaml.*; //YAMLFactory, YAMLParser

import org.openscience.cdk.*; // CDK,DefaultChemObjectBuilder,AtomContainer,ChemFile
import org.openscience.cdk.io.*; //SDFWriter
import org.openscience.cdk.tools.manipulator.*; //ChemFileManipulator, AtomContainerManipulator
import org.openscience.cdk.interfaces.*; // IChemObjectBuilder
import org.openscience.cdk.smiles.*; //SmilesParser,SmilesGenerator
import org.openscience.cdk.smiles.smarts.*; //SMARTSQueryTool
import org.openscience.cdk.aromaticity.*; //Kekulization, Aromaticity
import org.openscience.cdk.graph.*; //Cycles
import org.openscience.cdk.exception.*; // CDKException, InvalidSmilesException
import org.openscience.cdk.depict.*; // Depiction, DepictionGenerator

import edu.unm.health.biocomp.util.*; //time_utils
import edu.unm.health.biocomp.util.db.*; //DBCon
import edu.unm.health.biocomp.util.jre.*;
import edu.unm.health.biocomp.text.*; //Name,NameList

/**	Utilities for DrugCentral (DC) queries and admin.

	Links:
	 - STRUCTURES to SYNONYMS (1-to-many)
	 - STRUCTURES to ACTIVITIES
	 - ACTIVITIES to TARGET_DICTIONARY
	 - TARGET_DICTIONARY to TD2TC
	 - TD2TC to TARGET_COMPONENT
	 - PRODUCT to ACTIVE_INGREDIENT (1-to-many)
	 - ACTIVE_INGREDIENT to STRUCTURES (1-to-1)

	Each product contains 1+ ingredients.  Ingredients belong to one and only
	one product.  Ingredients are substances which could be a mixture but have
	a single active moiety, defined in structures table.
*/
public class drugcentral_app
{
  private static String APPNAME="DRUGCENTRAL";
  private static String dbname="drugcentral";
  private static String dbschema="public";
  private static String dbhost="localhost";
  private static Integer dbport=5432;
  private static String dbusr="drugman";
  private static String dbpw="dosage";
  private static String ofile=null;
  private static Integer verbose=0;
  private static Boolean describe=false;
  private static Boolean get_cpd=false;
  private static Boolean get_cpd_activity=false;
  private static Boolean search_cpds=false;
  private static Boolean get_product=false;
  private static Boolean search_products=false;
  private static Integer id=null;
  private static String query=null;
  private static String extidtype=null;
  private static String param_file = System.getProperty("user.home")+"/.drugcentral.yaml";

  public static void main(String[] args) throws Exception
  {
    String HELPFOOTER = 
      "Query syntax:\n"
      +"  STR[subtxt] .......... search cpds, substring name match [default]\n"
      +"  STR[fulltxt] ......... search cpds, full name match\n"
      +"  SMILES[substruct] .... search cpds, Smiles as sub-structure\n"
      +"  SMILES[fullstruct] ... search cpds, Smiles as full-structure\n"
      +"  3386[cidext] ......... search cpds, external ID, any type\n"
      +"  CODE[atc1] ........... search cpds, by ATC Level 1 code\n"
      +"  CODE[atc2] ........... search cpds, by ATC Level 2 code\n"
      +"  CODE[atc3] ........... search cpds, by ATC Level 3 code\n"
      +"  CODE[atc4] ........... search cpds, by ATC Level 4 code\n"
      +"  CID[cid] ............. get cpd, by cpd ID (DC unique ID)\n"
      +"  TID[tid] ............. get target, by target ID (DC unique ID)\n"
      +"  PID[pid] ............. get product, by product ID (DC unique ID)\n"
      +"External ID types:\n"
      +"  PUBCHEM_CID, ChEMBL_ID, MESH, ACTIVE_MOIETY_UNII, etc.\n"
      +"\n"
      +"Examples:\n"
      +"compound ID: 74 (acetylsalicylic acid)\n"
      +"compound ID: 1529 (ketorolac)\n"
      +"compound ID: 1209 (prozac)\n"
      +"product ID: 685873\n"
      +"product ID: 646461\n"
      +"\n"
      +"Example external IDs:\n"
      +"ACTIVE_MOIETY_UNII: 01K63SUP8D\n"
      +"ChEMBL_ID: CHEMBL41\n"
      +"MESH: D005473\n"
      +"PUBCHEM_CID: 3386\n"
      +"RxCUI: 58827\n";
    String HELPHEADER =  "DrugCentral utilities";
    Options opts = new Options();
    OptionGroup operations = new OptionGroup();
    operations.addOption(new Option("describe", false, "Describe (schema or table)."))
      .addOption(new Option("get_cpd", false, "Get cpd"))
      .addOption(new Option("get_cpd_activity", false, "Get cpd activity report"))
      .addOption(new Option("get_product", false, "Get product"))
      .addOption(new Option("search_cpds", false, "Search compounds by name or structure"))
      .addOption(new Option("search_products", false, "Search product names"))
      .addOption(new Option("h", "help", false, "Show verbose help."));
    operations.setRequired(true);
    opts.addOptionGroup(operations);

    opts.addOption(Option.builder("query").hasArg().desc("See syntax").build());
    opts.addOption(Option.builder("extidtype").hasArg().desc("External ID type").build());
    opts.addOption(Option.builder("dbhost").hasArg().desc("Db host ["+dbhost+"]").build());
    opts.addOption(Option.builder("dbport").type(Number.class).hasArg().desc("Db port ["+dbport+"]").build()); //Note: Integer.class not ok with CLI.
    opts.addOption(Option.builder("dbname").hasArg().desc("Db name ["+dbname+"]").build());
    opts.addOption(Option.builder("dbschema").hasArg().desc("Db schema ["+dbschema+"]").build());
    opts.addOption(Option.builder("dbusr").hasArg().desc("Db user").build());
    opts.addOption(Option.builder("dbpw").hasArg().desc("Db pw").build());
    opts.addOption(Option.builder("param_file").hasArg().desc("Db parameter file ["+param_file+"]").build());
    opts.addOption(Option.builder("id").longOpt("identifier").type(Number.class).hasArg().desc("Internal ID (int)").build()); //Note: Integer.class not ok with CLI.
    opts.addOption(Option.builder("o").hasArg().argName("OFILE").desc("Output file").build());
    opts.addOption("v", "verbose", false, "Verbose.");
    HelpFormatter helper = new HelpFormatter();
    CommandLineParser clip = new PosixParser();
    CommandLine clic = null;
    try {
      clic = clip.parse(opts, args);
    } catch (ParseException e) {
      helper.printHelp(APPNAME, HELPHEADER, opts, e.getMessage(), true);
      System.exit(0);
    }
    if (clic.hasOption("help")) {
      helper.printHelp(APPNAME, HELPHEADER, opts, HELPFOOTER, true);
      System.exit(0);
    }
    if (clic.hasOption("id")) id = ((Number)clic.getParsedOptionValue("id")).intValue();
    if (clic.hasOption("extidtype")) extidtype = clic.getOptionValue("extidtype");
    if (clic.hasOption("query")) query = clic.getOptionValue("query");
    if (clic.hasOption("o")) ofile = clic.getOptionValue("o");
    if (clic.hasOption("param_file")) param_file = clic.getOptionValue("param_file");
    if (clic.hasOption("v")) { verbose = 1; }

    if (verbose>0) System.err.println("JRE_VERSION: "+JREUtils.JREVersion());

    if (new File(param_file).exists()) {
      InputStream iStream = new FileInputStream(new File(param_file));
      YAMLParser parser = (new YAMLFactory()).createParser(iStream);
      for (parser.nextToken(); parser.nextToken()!=null; ) {
        String key = parser.getCurrentName();
        if (key==null) break;
        parser.nextToken();
        switch (key) {
          case "DBHOST": dbhost = parser.getText(); break;
          case "DBPORT": dbport = parser.getIntValue(); break;
          case "DBNAME": dbname = parser.getText(); break;
          case "DBSCHEMA": dbschema = parser.getText(); break;
          case "DBUSR": dbusr = parser.getText(); break;
          case "DBPW": dbpw = parser.getText(); break;
        }
      }
    }
    if (clic.hasOption("dbhost")) dbhost = clic.getOptionValue("dbhost");
    if (clic.hasOption("dbport")) dbport = ((Number)clic.getParsedOptionValue("dbport")).intValue();
    if (clic.hasOption("dbname")) dbname = clic.getOptionValue("dbname");
    if (clic.hasOption("dbschema")) dbschema = clic.getOptionValue("dbschema");
    if (clic.hasOption("dbusr")) dbusr = clic.getOptionValue("dbusr");
    if (clic.hasOption("dbpw")) dbpw = clic.getOptionValue("dbpw");

    PrintWriter fout_writer = (ofile!=null)?(new PrintWriter(new BufferedWriter(new FileWriter(new File(ofile), false)))):(new PrintWriter((OutputStream)System.out));

    java.util.Date t_0 = new java.util.Date();

    DBCon dbcon = null;
    try {
      if (verbose>0) System.err.println("postgres:"+dbhost+":"+dbport+"/"+dbname+":"+dbusr+":"+dbpw);
      dbcon = new DBCon("postgres", dbhost, dbport, dbname, dbusr, dbpw);
    }
    catch (Exception e) { helper.printHelp(APPNAME, HELPHEADER, opts, e.getMessage()); System.exit(1); }

    if (verbose>0)
      System.err.println("Connection ok: "+dbname+"@"+dbhost);

    DCQuery dbquery = (query==null) ? null : new DCQuery(query.trim());
    if (verbose>0 && dbquery!=null) drugcentral_utils.DescribeQuery(dbquery);

    StringBuilder log = new StringBuilder();

    if (clic.hasOption("describe"))
    {
      drugcentral_utils.DBDescribe(dbcon, fout_writer);
    }
    else if (clic.hasOption("get_cpd"))
    {
      if (id==null) { helper.printHelp(APPNAME, HELPHEADER, opts, "ERROR: -get requires -id."); System.exit(1); }
      DCCompound cpd = drugcentral_utils.GetCompound(dbcon, new DCQuery(String.format("%d[cid]", id)), log);
      if (cpd!=null)
        drugcentral_utils.ResultCompound(cpd, true, fout_writer);
      else System.out.println("No compound found.");
    }
    else if (clic.hasOption("search_cpds"))
    {
      if (dbquery==null) { helper.printHelp(APPNAME, HELPHEADER, opts, "ERROR: -search requires -query."); System.exit(1); }
      if (dbquery.getText().isEmpty()) { helper.printHelp(APPNAME, HELPHEADER,
opts, "ERROR: empty query string."); System.exit(1); }
      else if (dbquery.toString().length()<3) { helper.printHelp(APPNAME,
HELPHEADER, opts, "ERROR: query must be 3+ characters."); System.exit(1); }
      if (extidtype!=null) dbquery.setExtIdType(extidtype);
      CompoundList cpds = null;
      try { cpds = drugcentral_utils.SearchCompounds(dbcon,dbquery,log); }
      catch (Exception e) { System.err.println(e.toString()); }
      if (cpds!=null && cpds.size()>0) drugcentral_utils.ResultCompounds(cpds, fout_writer);
      else System.out.println("No compounds found.");
    }
    else if (clic.hasOption("get_cpd_activity"))
    {
      if (id==null) { helper.printHelp(APPNAME, HELPHEADER, opts, "ERROR: -get requires -id."); System.exit(1); }
      DCCompound cpd = drugcentral_utils.GetCompound(dbcon, new DCQuery(String.format("%d[cid]", id)), log);
      ResultSet rset = drugcentral_utils.GetCompoundActivities(dbcon, cpd.getDCID());
      drugcentral_utils.ResultSet2CompoundActivities(rset, cpd);
      drugcentral_utils.ResultCompoundActivities(cpd, fout_writer);
    }
    else if (clic.hasOption("get_product"))
    {
      if (id==null) { helper.printHelp(APPNAME, HELPHEADER, opts, "ERROR: -get requires -id."); System.exit(1); }
      DCProduct product = drugcentral_utils.GetProduct(dbcon, new DCQuery(String.format("%d[pid]", id)), log);
      if (product!=null)
        drugcentral_utils.ResultProduct(product, fout_writer);
    }
    else if (clic.hasOption("search_products"))
    {
      if (dbquery==null) { helper.printHelp(APPNAME, HELPHEADER, opts, "ERROR: -search requires -query."); System.exit(1); }
      if (dbquery.getText().isEmpty()) { helper.printHelp(APPNAME, HELPHEADER, opts, "ERROR: empty query string."); System.exit(1); }
      else if (dbquery.toString().length()<3) { helper.printHelp(APPNAME, HELPHEADER, opts, "ERROR: query must be 3+ characters."); System.exit(1); }
      ProductList products = drugcentral_utils.SearchProducts(dbcon, dbquery, log);
      if (products!=null) drugcentral_utils.ResultProducts(products, fout_writer);
    }
    else
    {
      helper.printHelp(APPNAME, HELPHEADER, opts, "ERROR: no operation specified.");
      System.exit(1);
    }
    if (verbose>0) System.err.println(log.toString());
    System.err.println("Elapsed time: "+time_utils.TimeDeltaStr(t_0,new java.util.Date()));
    if (fout_writer!=null) fout_writer.close();
  }
}
