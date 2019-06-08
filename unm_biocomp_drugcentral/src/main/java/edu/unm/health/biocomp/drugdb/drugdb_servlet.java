package edu.unm.health.biocomp.drugdb;

import java.io.*;
import java.net.*; //URLEncoder,InetAddress
import java.text.*;
import java.util.*;
import java.util.regex.*;
import java.sql.*; //DriverManager,Driver,SQLException,Connection,Statement,ResultSet
import javax.servlet.*;
import javax.servlet.http.*;

import com.oreilly.servlet.MultipartRequest;
import com.oreilly.servlet.multipart.DefaultFileRenamePolicy;
import com.oreilly.servlet.*; //Base64Encoder,Base64Decoder

import chemaxon.formats.*;
import chemaxon.struc.*;
import chemaxon.license.*; //LicenseException,LicenseManager
import chemaxon.util.*; //ConnectionHandler,MolHandler
import chemaxon.jchem.db.*; //JChemSearch,Updater,UpdateHandler
import chemaxon.jchem.version.*; //VersionInfo
import com.chemaxon.version.VersionInfo;
import chemaxon.sss.*; //SearchConstants
import chemaxon.sss.search.*; //JChemSearchOptions

import edu.unm.health.biocomp.http.*;
import edu.unm.health.biocomp.util.*;
import edu.unm.health.biocomp.text.*; //Name,NameList
import edu.unm.health.biocomp.db.*; //derby_utils
import edu.unm.health.biocomp.jchemdb.*; //jchemdb_utils

/**	Client for DrugDB queries: name searches, ID and structure lookups.
	Compound name search ranks COMPOUND name matches above synonyms.
	Sub-structure hits sorted by size.  Similarity-structure hits sorted by similiarity.

	@author Jeremy J Yang
*/
public class drugdb_servlet extends HttpServlet
{
  private static String SERVLETNAME=null;
  private static String CONTEXTPATH=null;
  private static String LOGDIR=null;	// configured in web.xml
  private static String APPNAME=null;	// configured in web.xml
  private static String UPLOADDIR=null;	// configured in web.xml
  private static String DBDIR=null;	// configured in web.xml
  private static String DBNAME=null;	// configured in web.xml
  private static String DBSCHEMA=null;	// configured in web.xml
  private static int N_MAX=100; // configured in web.xml
  private static String JC_PTABLE=null;	// configured in web.xml
  private static String JC_STABLE=null;	// configured in web.xml
  private static String JSMEURL=null; // configured in web.xml
  private static Boolean DEBUG=false; // configured in web.xml
  private static ServletContext CONTEXT=null;
  //private static ServletConfig CONFIG=null;
  private static ResourceBundle rb=null;
  private static PrintWriter out=null;
  private static ArrayList<String> outputs=null;
  private static ArrayList<String> errors=null;
  private static HttpParams params=null;
  private static int serverport=0;
  private static String SERVERNAME=null;
  private static String REMOTEHOST=null;
  private static String DATESTR=null;
  private static File LOGFILE=null;
  private static String color1="#EEEEEE";
  private static int DEPSZ=120;
  private static String mol2img_servleturl="";
  private static String JCHEMVER_DB=""; //set by init()
  private ConnectionHandler JC_CHAND=null; //non-static, one per object
  private JChemSearch JC_SEARCHER=null; //non-static, one per object
  private Connection DBCON=null; //non-static, one per object

  /////////////////////////////////////////////////////////////////////////////
  public void doPost(HttpServletRequest request,HttpServletResponse response)
      throws IOException,ServletException
  {
    serverport=request.getServerPort();
    SERVERNAME=request.getServerName();
    if (SERVERNAME.equals("localhost")) SERVERNAME=InetAddress.getLocalHost().getHostAddress();
    REMOTEHOST=request.getHeader("X-Forwarded-For"); // client (original)
    if (REMOTEHOST!=null)
    {
      String[] addrs=Pattern.compile(",").split(REMOTEHOST);
      if (addrs.length>0) REMOTEHOST=addrs[addrs.length-1];
    }
    else
    {
      REMOTEHOST=request.getRemoteAddr(); // client (may be proxy)
    }
    rb=ResourceBundle.getBundle("LocalStrings",request.getLocale());

    MultipartRequest mrequest=null;
    if (request.getMethod().equalsIgnoreCase("POST"))
    {
      try { mrequest=new MultipartRequest(request,UPLOADDIR,10*1024*1024,"ISO-8859-1", new DefaultFileRenamePolicy()); }
      catch (IOException lEx) { this.getServletContext().log("Not a valid MultipartRequest.",lEx); }
    }

    // main logic:
    ArrayList<String> cssincludes = new ArrayList<String>(Arrays.asList("biocomp.css"));
    ArrayList<String> jsincludes = new ArrayList<String>(Arrays.asList("biocomp.js","ddtip.js"));
    boolean ok=Initialize(request,mrequest);
    if (!ok)
    {
      response.setContentType("text/html");
      out=response.getWriter();
      out.println(HtmUtils.HeaderHtm(APPNAME,jsincludes,cssincludes,JavaScript(),color1,request));
      out.println(HtmUtils.FooterHtm(errors,true));
      return;
    }
    else if (request.getParameter("help")!=null)	// GET method, help=TRUE
    {
      response.setContentType("text/html");
      out=response.getWriter();
      out.println(HtmUtils.HeaderHtm(APPNAME,jsincludes,cssincludes,JavaScript(),color1,request));
      out.println(HelpHtm());
      out.println(HtmUtils.FooterHtm(errors,true));
    }
    else if (request.getParameter("dbupdate")!=null)	// GET method, dbupdate=TRUE
    {
      response.setContentType("text/html");
      out=response.getWriter();
      out.println(HtmUtils.HeaderHtm(APPNAME,jsincludes,cssincludes,JavaScript(),color1,request));
      try {
        StringBuilder sb = new StringBuilder();
        JChemDbUpdate(JC_CHAND,sb);
        out.println("<PRE>"+sb.toString()+"</PRE>");
      }
      catch (Exception e) { out.println("<PRE>"+e.toString()+"</PRE>"); }
      out.println(HtmUtils.FooterHtm(errors,true));
    }
    else	//POST or GET method
    {
      response.setContentType("text/html");
      out=response.getWriter();
      out.println(HtmUtils.HeaderHtm(APPNAME,jsincludes,cssincludes,JavaScript(),color1,request));
      java.util.Date t_0 = new java.util.Date();

      DrugDBQuery dbquery = new DrugDBQuery(params.getVal("query").trim()); //All queries begin here.

      if (!params.isChecked("noform")) //for popups:
      {
        out.println(FormHtm(mrequest,response));
        String qhtm=("<TABLE BORDER=\"0\" CELLPADDING=\"5\" CELLSPACING=\"5\">\n");
        qhtm+=("<TR><TD VALIGN=\"top\"><H2>Query:</H2></TD>\n");
        qhtm+=("<TD VALIGN=\"middle\" BGCOLOR=\"white\"><DIV STYLE=\"font-size:14px; font-family:monospace; font-weight:bold\">");
        if (dbquery.getType().matches("^.*struct$"))
          qhtm+=(HtmUtils.Smi2ImgHtm(dbquery.getText(),"",120,150,mol2img_servleturl,true,4,"go_zoom_smi2img"));
        else
          qhtm+=(dbquery.toString());
        qhtm+=("</DIV></TD>");
        qhtm+=("</TR>");
        qhtm+=("</TABLE>");
        outputs.add(qhtm);
      }

      if (request.getMethod().equalsIgnoreCase("POST") || !dbquery.getText().isEmpty())
      {
        if (dbquery.getText().isEmpty()) { outputs.add("ERROR: empty query string."); return ; }
        else if (dbquery.toString().length()<3) { outputs.add("ERROR: query must be 3+ characters."); return; }

        PrintWriter out_log=new PrintWriter(new BufferedWriter(new FileWriter(LOGFILE,true)));
        StringBuilder querylog = new StringBuilder();

        if (dbquery.getType().equalsIgnoreCase("cid")) //Query type: Get compound
        {
          DrugDBCompound cpd = null;
          try {
            cpd=drugdb_utils.GetCompound(DBCON,dbquery,querylog);
            if (params.isChecked("activityreport"))
            {
              ResultSet rset = drugdb_utils.GetCompoundActivities(DBCON,cpd.getID());
              drugdb_utils.ResultSet2CompoundActivities(rset,cpd);
            }
          }
          catch (SQLException e) { errors.add("ERROR: "+e.getMessage()); }
          if (params.isChecked("activityreport"))
            outputs.add(ResultCompoundActivityHtm(cpd));
          else
            outputs.add(ResultCompoundHtm(cpd,response));
        }
        else if (dbquery.getType().equalsIgnoreCase("pid")) //Query type: Get product
        {
          DrugDBProduct prd = null;
          try { prd=drugdb_utils.GetProduct(DBCON,dbquery,querylog); }
          catch (SQLException e) { errors.add("ERROR: "+e.getMessage()); }
          outputs.add(ResultProductHtm(prd,response));
        }
        else //Query type: Search compounds
        {
          CompoundList cpds = null;
          try { cpds=drugdb_utils.SearchCompounds(DBCON,JC_CHAND,JC_SEARCHER,dbquery,N_MAX,querylog); }
          catch (Exception e) { errors.add("ERROR: "+e.toString()); }
          outputs.add(ResultCompoundsHtm(cpds,response,N_MAX));
        }
        out.println(HtmUtils.OutputHtm(outputs));
        out_log.printf("%s\t%s\t\"%s\"\n",DATESTR,REMOTEHOST,dbquery.toString());
        out_log.close();
        if (params.isChecked("verbose"))
          if (querylog.length()>0)
            errors.add("<PRE>"+querylog.toString()+"</PRE>");
      }
      if (params.isChecked("verbose"))
        errors.add("Elapsed time: "+time_utils.TimeDeltaStr(t_0,new java.util.Date()));

      out.println(HtmUtils.FooterHtm(errors,true));
    }
  }
  /////////////////////////////////////////////////////////////////////////////
  /**	Called once per request.
  */
  private boolean Initialize(HttpServletRequest request,MultipartRequest mrequest)
      throws IOException,ServletException
  {
    SERVLETNAME=this.getServletName();
    outputs = new ArrayList<String>();
    errors = new ArrayList<String>();
    params = new HttpParams();

    String logo_htm="<TABLE CELLSPACING=5 CELLPADDING=5><TR><TD>";
    String imghtm=("<IMG BORDER=0 SRC=\"/tomcat"+CONTEXTPATH+"/images/biocomp_logo_only.gif\">");
    String tiphtm=(APPNAME+" web app from UNM Translational Informatics.");
    String href=("http://medicine.unm.edu/informatics/");
    logo_htm+=(HtmUtils.HtmTipper(imghtm,tiphtm,href,200,"white"));
    logo_htm+="</TD><TD>";
    imghtm=("<IMG BORDER=0 SRC=\"/tomcat"+CONTEXTPATH+"/images/chemaxon_powered_100px.png\">");
    tiphtm=("ChemAxon Ltd.");
    href=("http://www.chemaxon.com");
    logo_htm+=(HtmUtils.HtmTipper(imghtm,tiphtm,href,200,"white"));
    logo_htm+="</TD></TR></TABLE>";
    errors.add("<CENTER>"+logo_htm+"</CENTER>");

    //Create log dir if necessary:
    File dout=new File(LOGDIR);
    if (!dout.exists())
    {
      boolean ok=dout.mkdir();
      System.err.println("LOGDIR creation "+(ok?"succeeded":"failed")+": "+LOGDIR);
      if (!ok) { errors.add("ERROR: could not create LOGDIR: "+LOGDIR); return false; }
    }

    String logpath=LOGDIR+"/"+SERVLETNAME+".log";
    LOGFILE=new File(logpath);
    if (!LOGFILE.exists())
    {
      try { LOGFILE.createNewFile(); }
      catch (IOException e) { errors.add("ERROR: Cannot create log file:"+e.getMessage()); return false; }
      LOGFILE.setWritable(true,true);
      PrintWriter out_log=new PrintWriter(LOGFILE);
      out_log.println("date\tip\tquery"); 
      out_log.flush();
      out_log.close();
    }
    if (!LOGFILE.canWrite()) { errors.add("ERROR: Log file not writable."); return false; }
    BufferedReader buff=new BufferedReader(new FileReader(LOGFILE));
    if (buff==null) { errors.add("ERROR: Cannot open log file."); return false; }

    int n_lines=0;
    String line=null;
    Calendar calendar=Calendar.getInstance();
    while ((line=buff.readLine())!=null)
    {
      ++n_lines;
      String[] fields=Pattern.compile("\\t").split(line);
      if (n_lines==2) 
        calendar.set(Integer.parseInt(fields[0].substring(0,4)),
               Integer.parseInt(fields[0].substring(4,6))-1,
               Integer.parseInt(fields[0].substring(6,8)),
               Integer.parseInt(fields[0].substring(8,10)),
               Integer.parseInt(fields[0].substring(10,12)),0);
    }
    if (n_lines>1)
    {
      DateFormat df=DateFormat.getDateInstance(DateFormat.FULL,Locale.US);
      errors.add("Since "+df.format(calendar.getTime())+", times used: "+(n_lines-1));
    }

    calendar.setTime(new java.util.Date());
    DATESTR=String.format("%04d%02d%02d%02d%02d",
      calendar.get(Calendar.YEAR),
      calendar.get(Calendar.MONTH)+1,
      calendar.get(Calendar.DAY_OF_MONTH),
      calendar.get(Calendar.HOUR_OF_DAY),
      calendar.get(Calendar.MINUTE));

    mol2img_servleturl=("http://"+SERVERNAME+"/tomcat"+CONTEXTPATH+"/mol2img");

    try {
      errors.add("Total compounds: "+drugdb_utils.CompoundCount(DBCON)+
	" ; products: "+drugdb_utils.ProductCount(DBCON)+
	" ; targets: "+drugdb_utils.TargetCount(DBCON)+
	" ; activities: "+drugdb_utils.ActivityCount(DBCON));
    } catch (SQLException e) { errors.add(e.getMessage()); }


    for (Enumeration e=request.getParameterNames(); e.hasMoreElements(); ) //GET
    {
      String key=(String)e.nextElement();
      if (request.getParameter(key)!=null) params.setVal(key,request.getParameter(key));
    }
    if (DEBUG) params.setVal("verbose","TRUE");

    if (DBCON!=null)
    {
      if (params.isChecked("verbose"))
        errors.add("Derby connection ok: jdbc:derby:"+DBDIR+"/"+DBNAME);
      try { errors.add(derby_utils.ServerStatusTxt(DBCON)); } catch (SQLException e) { errors.add(e.getMessage()); }
    }

    if (params.isChecked("verbose"))
    {
      errors.add("app server: "+CONTEXT.getServerInfo()+" [API:"+CONTEXT.getMajorVersion()+"."+CONTEXT.getMinorVersion()+"]");
      errors.add("JChem DB version: "+JCHEMVER_DB+" ; JChem client version: "+VersionInfo.getVersion()+" ("+(JCHEMVER_DB.equals(VersionInfo.getVersion())?"EQUAL":"NOT EQUAL")+")");
    }
    if (!JCHEMVER_DB.equals(VersionInfo.getVersion())) errors.add("Warning: DB JChem version != client JChem version ("+JCHEMVER_DB+" != "+VersionInfo.getVersion()+")");

    if (mrequest==null) return true; //i.e. GET method

    for (Enumeration e=mrequest.getParameterNames(); e.hasMoreElements(); ) //POST
    {
      String key=(String)e.nextElement();
      if (mrequest.getParameter(key)!=null) params.setVal(key,mrequest.getParameter(key));
    }
    if (DEBUG) params.setVal("verbose","TRUE");

    return true;
  }
  /////////////////////////////////////////////////////////////////////////////
  private static String FormHtm(MultipartRequest mrequest,HttpServletResponse response)
      throws IOException
  {
    String htm=
    ("<FORM NAME=\"mainform\" METHOD=POST ACTION=\""+response.encodeURL(SERVLETNAME)+"\"")
    +(" ENCTYPE=\"multipart/form-data\">\n")
    +("<TABLE WIDTH=\"100%\"><TR><TD><H1>"+APPNAME+" <I>BETA</I></H1></TD><TD><DIV STYLE=\"margin-bottom:0\">drug knowledge integration database</DIV></TD><TD></TD>\n")
    +("<TD ALIGN=\"right\">\n")
    +("<BUTTON TYPE=BUTTON onClick=\"go_popup('"+response.encodeURL(SERVLETNAME)+"?help=TRUE&verbose=TRUE','helpwin','width=600,height=400,scrollbars=1,resizable=1')\"><B>Help</B></BUTTON>\n")
    +("<BUTTON TYPE=BUTTON onClick=\"go_reset('"+response.encodeURL(SERVLETNAME)+"')\"><B>Reset</B></BUTTON>\n")
    +("</TD></TR></TABLE>\n")
    +("<HR>\n")
    +("<CENTER>\n")
    +("<TABLE WIDTH=\"70%\" CELLPADDING=\"10\" CELLSPACING=\"10\">\n")
    +("<TR BGCOLOR=\"#CCCCFF\"><TD ALIGN=\"CENTER\" VALIGN=\"MIDDLE\">\n")
    +("<P>\n")
    +("<INPUT STYLE=\"padding:5px; font-size:14px;\" TYPE=\"text\" NAME=\"query\" SIZE=\"64\" VALUE=\""+params.getVal("query")+"\">\n")
    +("<BR>\n")
    +("<i>Enter query: name substring...or draw structure query:</i>\n")
    +("<BUTTON TYPE=BUTTON onClick=\"StartJSME()\"><DIV STYLE=\"font-size:9px\">JSME</DIV></BUTTON><BR>\n")
    +("<BUTTON TYPE=\"button\" onClick=\"go_drugdb(this.form)\"><DIV STYLE=\"font-size:14px; font-weight:bold\">Go "+APPNAME+"</DIV></BUTTON>\n")
    +("<P>\n")
    +("<SMALL>Examples: ");

    for (String drugname: new String[]{"aspirin","hydrocortisone","ketorolac","prozac","ranitidine","rosuvastatin","tamoxifen","verapamil"})
      htm+=("&nbsp;<A HREF=\""+response.encodeURL(SERVLETNAME)+"?query="+drugname+"[fulltxt]\">"+drugname+"</A>\n");

    htm+=(
     ("<BR>\n<i>For advanced query syntax, see help.</i>\n")
    +("</SMALL></TD></TR></TABLE>\n")
    +("</CENTER>\n")
    +("</FORM>\n"));
    return htm;
  }

  /////////////////////////////////////////////////////////////////////////////
  private static String ResultCompoundHtm(DrugDBCompound cpd,HttpServletResponse response)
  {
    if (cpd==null) return ("<H2>Compound not found.</H2>");
    String htm=("<H2>Compound ["+cpd.getID()+"]</H2>");
    String thtm=("<TABLE WIDTH=\"100%\" CELLSPACING=2 CELLPADDING=2>\n");
    String imghtm;
    String depictopts="kekule=TRUE";
    if (cpd.isLarge())
      imghtm=("<I>(LARGE MOLECULE)</I>\n");
    else if (cpd.getAtomCount()==0)
      imghtm=("<I>(MOLECULE UNAVAILABLE)</I>\n");
    else
      imghtm=((cpd.getSmiles()!=null)?HtmUtils.Smi2ImgHtm(cpd.getSmiles(),depictopts,120,150,mol2img_servleturl,true,4,"go_zoom_smi2img"):"");

    thtm+=("<TR><TD ALIGN=\"right\"></TD><TD BGCOLOR=\"white\" ALIGN=\"center\">"+imghtm+"</TD></TR>\n");
    thtm+=("<TR><TD ALIGN=\"right\">MF</TD><TD BGCOLOR=\"white\">"+cpd.getMolformula()+"</TD></TR>\n");
    thtm+=("<TR><TD ALIGN=\"right\">MWT</TD><TD BGCOLOR=\"white\">"+String.format("%.2f",cpd.getMwt())+"</TD></TR>\n");
    if (!cpd.isLarge())
      thtm+=("<TR><TD ALIGN=\"right\">Smiles</TD><TD BGCOLOR=\"white\">"+cpd.getSmiles()+"</TD></TR>\n");
    errors.add("Mol source format: "+cpd.getMolformat()+" ("+((cpd.getMolformat()!=null)?MFileFormatUtil.getFormat(cpd.getMolformat()).getDescription():"")+")");

    //Names:
    NameList names =  cpd.getNames();
    thtm+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">Names ("+names.size()+")</TD><TD BGCOLOR=\"white\"><UL>\n";
    for (Name name: names)
      thtm+=("<LI>"+name.getValue()+"\n");
    thtm+="</UL></TD></TR>\n";

    thtm+=("<TR><TD ALIGN=\"right\">Approval</TD><TD BGCOLOR=\"white\">"+(cpd.getApprovalDate()+" ("+cpd.getApprovalType()+((cpd.getApprovalApplicant()!=null)?(", "+cpd.getApprovalApplicant()):"")+")")+"</TD></TR>\n");

    thtm+=("<TR><TD ALIGN=\"right\">CAS</TD><TD BGCOLOR=\"white\">"+((cpd.getCAS()!=null)?cpd.getCAS():"~")+"</TD></TR>\n");

    //IDs:
    String thtm2="<TABLE WIDTH=\"100%\" CELLSPACING=\"1\" CELLPADDING=\"1\">\n";
    for (String idtype: cpd.getExtIDTypes())
    {
      String val=cpd.getExtID(idtype);
      String url=null;
      if (idtype.equalsIgnoreCase("PUBCHEM_CID"))
        url=("http://pubchem.ncbi.nlm.nih.gov/compound/"+val);
      else if (idtype.equalsIgnoreCase("CHEBI"))
        url=("https://www.ebi.ac.uk/chebi/searchId.do?chebiId="+val);
      else if (idtype.equalsIgnoreCase("ChEMBL_ID"))
        url=("https://www.ebi.ac.uk/chembl/compound/inspect/"+val);
      else if (idtype.equalsIgnoreCase("KEGG DRUG"))
        url=("http://www.kegg.jp/entry/"+val);
      else if (idtype.equalsIgnoreCase("MESH"))
        url=("http://id.nlm.nih.gov/mesh/"+val);
      else if (idtype.equalsIgnoreCase("ACTIVE_MOIETY_UNII") || idtype.equalsIgnoreCase("UNII_CODE"))
        url=("http://chem.sis.nlm.nih.gov/chemidplus/unii/"+val);
      else if (idtype.equalsIgnoreCase("IUPHAR_LIGAND_ID"))
        url=("http://www.guidetopharmacology.org/GRAC/LigandDisplayForward?ligandId="+val);
      //else if (idtype.equalsIgnoreCase("MMSL_CODE"))
      //else if (idtype.equalsIgnoreCase("PDB_CHEM_ID"))
      //else if (idtype.equalsIgnoreCase("RxCUI"))
      //else if (idtype.equalsIgnoreCase("UMLSCUI"))
      //else if (idtype.equalsIgnoreCase("NUI"))
      //else if (idtype.equalsIgnoreCase("VUID"))
      thtm2+=("<TR><TD WIDTH=\"25%\" ALIGN=\"right\">"+idtype+":</TD><TD>"+((url!=null)?"<A TARGET=\"_\" HREF=\""+url+"\">"+val+"</A>":val)+"</TD></TR>\n");
    }
    thtm2+="</TABLE>\n";
    thtm+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">IDs</TD><TD BGCOLOR=\"white\">"+thtm2+"</TD></TR>\n";

    //ATCs:
    int i_atc=0;
    thtm2="";
    for (ATC atc: cpd.getAtcs())
    {
      thtm2+="<TABLE WIDTH=\"100%\" CELLSPACING=\"0\" CELLPADDING=\"0\">\n";
      for (int lev=1;lev<=4;++lev)
      {
        thtm2+=("<TR><TD WIDTH=\"15%\"><A HREF=\"javascript:void(0)\" onClick=\"go_parentwin('"+response.encodeURL(SERVLETNAME)+"?query="+atc.getCode(lev)+"[atc"+lev+"]')\">"+atc.getCode(lev)+"</A></TD><TD>"+atc.getName(lev)+"</TD></TR>\n");
      }
      thtm2+="</TABLE><P>\n";
      ++i_atc;
    }
    thtm+=("<TR><TD ALIGN=\"right\" VALIGN=\"top\">ATCs ("+i_atc+")</TD><TD BGCOLOR=\"white\">"+((i_atc>0)?thtm2:"None")+"</TD></TR>\n");

    //Activities-Targets:
    thtm2="<UL>\n";
    for (DrugDBActivity act: cpd.getActivityList().getAllSortedByRelevance())
    {
      DrugDBTarget tgt = act.getTarget();
      thtm2+=("<!-- TID: "+tgt.getID()+" -->\n<LI>");
      if (act.getMoaType()!=null && act.getMoa().equals(1))
        thtm2+=(tgt.getName()+" <B>(MOA:"+act.getMoaType()+")</B>\n");
      else if (tgt.getOrganism().equalsIgnoreCase("Homo sapiens"))
        thtm2+=(tgt.getName()+"\n");
      else
        thtm2+=(tgt.getName()+" ("+tgt.getOrganism()+")\n");
      thtm2+=("<OL>\n");
      for (DrugDBTargetComponent tgtc: tgt.getComponents())
      {
        thtm2+=("<LI>Component: "+("<A TARGET=\"_\" HREF=\"http://www.ncbi.nlm.nih.gov/gene/"+tgtc.getGeneID()+"\">"+tgtc.getGenesymbol()+"</A>")+": "+tgtc.getName()+" ("+tgtc.getOrganism()+")\n");
      }
      thtm2+=("</OL>\n");
    }
    thtm2+="</UL>\n";
    String bhtm=("<BUTTON TYPE=\"button\" onClick=\"javascript:go_popup('"+response.encodeURL(SERVLETNAME)+"?query="+cpd.getID()+"[cid]&activityreport=TRUE&noform=TRUE','actwin','width=900,height=700,scrollbars=1,resizable=1')\"><B>See activity</B></BUTTON>");
    thtm+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">Targets ("+cpd.targetCount()+")<BR>"+bhtm+"</TD><TD BGCOLOR=\"white\">"+((cpd.targetCount()>0)?thtm2:"None")+"</TD></TR>\n";

    //Products:
    int i_prd=0;
    thtm2="<TABLE WIDTH=\"100%\" CELLSPACING=2 CELLPADDING=2>\n";
    thtm2+=("<TR><TH>ID</TH><TH>Name</TH><TH>Generic Name</TH><TH>NDC</TH><TH>Form</TH><TH>Route</TH><TH>Status</TH><TH>#Ingr</TH></TR>\n");
    for (DrugDBProduct prd: cpd.getProductList().getAllSortedByRelevance())
    {
      ++i_prd;
      thtm2+=("<TR>\n");
      thtm2+=("<TD><A HREF=\"javascript:void(0)\" onClick=\"javascript:go_popup('"+response.encodeURL(SERVLETNAME)+"?query="+prd.getID()+"[pid]&noform=TRUE','productwin','width=700,height=800,scrollbars=1,resizable=1')\">"+prd.getID()+"</A></TD>\n");
      thtm2+=("<TD>"+prd.getProductname()+"</TD>\n");
      thtm2+=("<TD>"+prd.getGenericname()+"</TD>\n");
      thtm2+=("<TD>"+prd.getNdc()+"</TD>\n");
      thtm2+=("<TD>"+prd.getForm()+"</TD>\n");
      thtm2+=("<TD>"+prd.getRoute()+"</TD>\n");
      thtm2+=("<TD>"+prd.getStatus()+"</TD>\n");
      thtm2+=("<TD>"+prd.getIngredientCount()+"</TD>\n");
      thtm2+=("</TR>\n");
    }
    thtm2+="</TABLE>\n";
    thtm+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">Products ("+i_prd+")</TD><TD BGCOLOR=\"white\">"+((i_prd>0)?thtm2:"None")+"</TD></TR>\n";

    thtm+="</TABLE>\n";
    htm+=thtm;
    return htm;
  }
  /////////////////////////////////////////////////////////////////////////////
  private static String ResultCompoundActivityHtm(DrugDBCompound cpd)
  {
    if (cpd==null) return ("<H2>ERROR: cpd==null</H2>");
    String htm=("<H2>Compound Activity ["+cpd.getID()+"]</H2>");
    String thtm=("<TABLE WIDTH=\"100%\" CELLSPACING=2 CELLPADDING=2>\n");
    thtm+=("<TR><TH></TH><TH>Target</TH><TH>Result</TH><TH>Value</TH><TH>MOA</TH>\n");
    thtm+=("<TH>Source</TH><TH>RefID</TH><TH>RefPMID</TH><TH>RefTitle</TH><TH>RefJournal</TH><TH>RefYear</TH><TH>Comment</TH></TR>\n");

    int i_act=0;
    for (DrugDBActivity act: cpd.getActivityList().getAllSortedByRelevance())
    {
      boolean got_moa = (act.getMoaType()!=null && act.getMoa().equals(1));
      String bgcolor = got_moa ? "yellow":"white";
      ++i_act;
      DrugDBTarget tgt = act.getTarget();
      String rhtm="";

      rhtm=("<TR>");
      rhtm+=("<TD "+(got_moa?"ROWSPAN=\"2\" ":"")+"ALIGN=\"right\">"+(i_act)+"</TD>\n");
      rhtm+=("<TD "+(got_moa?"ROWSPAN=\"2\" ":"")+"BGCOLOR=\""+bgcolor+"\">"+tgt.getName()+" ["+tgt.getID()+"]</TD>\n");
      rhtm+=("<TD "+(got_moa?"ROWSPAN=\"2\" ":"")+"BGCOLOR=\""+bgcolor+"\">"+act.getType()+"</TD>\n");
      rhtm+=("<TD "+(got_moa?"ROWSPAN=\"2\" ":"")+"BGCOLOR=\""+bgcolor+"\"> "+(String.valueOf(act.getRelation()).matches("[><]")?act.getRelation():"")+String.format("%.3f%s",act.getValue(),((act.getUnit()!=null)?act.getUnit():""))+"</TD>\n");
      if (got_moa)
        rhtm+=("<TD ROWSPAN=\"2\" BGCOLOR=\""+bgcolor+"\"><B>MOA: "+act.getMoaType()+"</B></TD>\n");
      else
        rhtm+=("<TD BGCOLOR=\""+bgcolor+"\">~</TD>\n");


      boolean same_source = got_moa && act.getSource().equals(act.getMoaSource());
      boolean same_ref = got_moa && act.getRefID().equals(act.getMoaRefID());

      rhtm+=("<TD "+(same_source?"ROWSPAN=\"2\" ":"")+"BGCOLOR=\""+bgcolor+"\">"+act.getSource()+"</TD>\n");

      rhtm+=("<TD "+(same_ref?"ROWSPAN=\"2\" ":"")+"BGCOLOR=\""+bgcolor+"\">"+((act.getRefID()!=null && act.getRefID()!=0)?act.getRefID():"~")+"</TD>\n");
      rhtm+=("<TD "+(same_ref?"ROWSPAN=\"2\" ":"")+"BGCOLOR=\""+bgcolor+"\">"+((act.getRefPMID()!=null)?("<A TARGET=\"_\" HREF=\"http://www.ncbi.nlm.nih.gov/pubmed?term="+act.getRefPMID()+"[uid]\">"+act.getRefPMID()+"</A>"):"~")+"</TD>\n");
      rhtm+=("<TD "+(same_ref?"ROWSPAN=\"2\" ":"")+"BGCOLOR=\""+bgcolor+"\">"+((act.getRefTitle()!=null)?act.getRefTitle():"~")+"</TD>\n");
      rhtm+=("<TD "+(same_ref?"ROWSPAN=\"2\" ":"")+"BGCOLOR=\""+bgcolor+"\">"+((act.getRefJournal()!=null)?act.getRefJournal():"~")+"</TD>\n");
      rhtm+=("<TD "+(same_ref?"ROWSPAN=\"2\" ":"")+"BGCOLOR=\""+bgcolor+"\">"+((act.getRefYear()!=null && act.getRefYear()!=0)?act.getRefYear():"~")+"</TD>\n");

      rhtm+=("<TD ROWSPAN=\""+(got_moa?"2":"1")+"\" BGCOLOR=\""+bgcolor+"\">"+((act.getComment()!=null)?act.getComment():"~")+"</TD>\n");
      rhtm+=("</TR>\n");

      if (got_moa)
      {
        rhtm+=("<TR>\n");
        if (!same_source)
          rhtm+=("<TD BGCOLOR=\""+bgcolor+"\">"+act.getMoaSource()+"</TD>\n");
        if (!same_ref)
        {
          rhtm+=("<TD BGCOLOR=\""+bgcolor+"\">"+((act.getMoaRefID()!=null && act.getMoaRefID()!=0)?act.getMoaRefID():"~")+"</TD>\n");
          rhtm+=("<TD BGCOLOR=\""+bgcolor+"\">"+((act.getMoaRefPMID()!=null)?("<A TARGET=\"_\" HREF=\"http://www.ncbi.nlm.nih.gov/pubmed?term="+act.getMoaRefPMID()+"[uid]\">"+act.getMoaRefPMID()+"</A>"):"~")+"</TD>\n");
          rhtm+=("<TD BGCOLOR=\""+bgcolor+"\">"+((act.getMoaRefTitle()!=null)?act.getMoaRefTitle():"~")+"</TD>\n");
          rhtm+=("<TD BGCOLOR=\""+bgcolor+"\">"+((act.getMoaRefJournal()!=null)?act.getMoaRefJournal():"~")+"</TD>\n");
          rhtm+=("<TD BGCOLOR=\""+bgcolor+"\">"+((act.getMoaRefYear()!=null && act.getMoaRefYear()!=0)?act.getMoaRefYear():"~")+"</TD>\n");
        }
        rhtm+=("</TR>\n");
      }

      thtm+=rhtm;
    }
    thtm+="</TABLE>\n";
    htm+=thtm;
    htm+=("Target count: "+cpd.targetCount()+"<BR>\n");
    htm+=("Activity count: "+cpd.activityCount()+"<BR>\n");
    return htm;
  }

  /////////////////////////////////////////////////////////////////////////////
  private static String ResultProductHtm(DrugDBProduct product,HttpServletResponse response)
  {
    if (product==null) return ("<H2>Product not found.</H2>");
    String htm=("<H2>Product ["+product.getID()+"]</H2>");
    String imghtm;
    String depictopts="kekule=TRUE";
    String thtm=("<TABLE WIDTH=\"100%\" CELLSPACING=2 CELLPADDING=2>\n");
    if (!product.hasLargeCompound())
    {
      imghtm=((product.getMixtureSmiles()!=null)?HtmUtils.Smi2ImgHtm(product.getMixtureSmiles(),depictopts,180,320,mol2img_servleturl,true,4,"go_zoom_smi2img"):"");
      thtm+=("<TR><TD></TD><TD ALIGN=\"center\" BGCOLOR=\"white\">"+imghtm+"</TD></TR>\n");
    }
    thtm+=("<TR><TD ALIGN=\"right\">Name:</TD><TD BGCOLOR=\"white\">"+product.getProductname()+"</TD></TR>\n");
    thtm+=("<TR><TD ALIGN=\"right\">Generic name:</TD><TD BGCOLOR=\"white\">"+product.getGenericname()+"</TD></TR>\n");
    thtm+=("<TR><TD ALIGN=\"right\">NDC:</TD><TD BGCOLOR=\"white\">"+product.getNdc()+"</TD></TR>\n");
    thtm+=("<TR><TD ALIGN=\"right\">Route:</TD><TD BGCOLOR=\"white\">"+product.getRoute()+"</TD></TR>\n");
    thtm+=("<TR><TD ALIGN=\"right\">Form:</TD><TD BGCOLOR=\"white\">"+product.getForm()+"</TD></TR>\n");
    thtm+=("<TR><TD ALIGN=\"right\">Status:</TD><TD BGCOLOR=\"white\">"+product.getStatus()+"</TD></TR>\n");

    int i=0;
    for (DrugDBIngredient ingr: product.getIngredients())
    {
      ++i;
      DrugDBCompound cpd = ingr.getCompound();
      String thtm2="<TABLE WIDTH=\"100%\" CELLSPACING=2 CELLPADDING=2>\n"; //ingredient table
      if (cpd.isLarge())
        imghtm=("<I>(LARGE MOLECULE)</I>");
      else if (cpd.getAtomCount()==0)
        imghtm=("<I>(MOLECULE UNAVAILABLE)</I>\n");
      else
        imghtm=((cpd.getSmiles()!=null)?HtmUtils.Smi2ImgHtm(cpd.getSmiles(),depictopts,120,150,mol2img_servleturl,true,4,"go_zoom_smi2img"):"");
      thtm2+="<TR><TD COLSPAN=\"2\" ALIGN=\"center\" BGCOLOR=\"white\">"+imghtm+"</TD></TR>\n";
      thtm2+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">MF</TD><TD BGCOLOR=\"white\">"+cpd.getMolformula()+"</TD></TR>\n";
      thtm2+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">MWT</TD><TD BGCOLOR=\"white\">"+String.format("%.1f",cpd.getMwt())+"</TD></TR>\n";
      thtm2+="<!-- IID = "+ingr.getID()+" -->\n";
      thtm2+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">CID</TD><TD BGCOLOR=\"white\">"+cpd.getID()+"</TD></TR>\n";
      thtm2+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">Active moiety name</TD><TD BGCOLOR=\"white\">"+ingr.getActivemoietyName()+"</TD></TR>\n";
      thtm2+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">Active moiety UNII</TD><TD BGCOLOR=\"white\">"+ingr.getActivemoietyUnii()+"</TD></TR>\n";
      thtm2+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">Substance name</TD><TD BGCOLOR=\"white\">"+ingr.getSubstanceName()+"</TD></TR>\n";
      thtm2+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">Substance UNII</TD><TD BGCOLOR=\"white\">"+ingr.getSubstanceUnii()+"</TD></TR>\n";
      thtm2+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">CAS</TD><TD BGCOLOR=\"white\">"+((cpd.getCAS()!=null)?cpd.getCAS():"~")+"</TD></TR>\n";
      thtm2+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">Quantity</TD><TD BGCOLOR=\"white\">"+ingr.getQuantity()+" ("+ingr.getUnit()+")</TD></TR>\n";

      //ATCs:
      int i_atc=0;
      String thtm3="";
      for (ATC atc: cpd.getAtcs())
      {
        thtm3+="<TABLE WIDTH=\"100%\" CELLSPACING=\"0\" CELLPADDING=\"0\">\n";
        for (int lev=1;lev<=4;++lev)
          thtm3+=("<TR><TD WIDTH=\"15%\">"+atc.getCode(lev)+"</TD><TD>"+atc.getName(lev)+"</TD></TR>\n");
        thtm3+="</TABLE><P>\n";
        ++i_atc;
      }
      thtm2+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">ATCs ("+i_atc+")</TD><TD BGCOLOR=\"white\">"+thtm3+"</TD></TR>\n";

      //Targets:
      thtm2+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">Targets ("+cpd.targetCount()+")</TD><TD BGCOLOR=\"white\"><I>See compound report.</I></TD></TR>\n";

      //Names:
      NameList nlist =  cpd.getNames();
      thtm2+="<TR><TD ALIGN=\"right\" VALIGN=\"top\">Names ("+cpd.nameCount()+")</TD><TD BGCOLOR=\"white\"><UL>\n";
      for (Name name: nlist)
        thtm2+=("<LI>"+name.toString()+"\n");
      thtm2+="</UL></TD></TR>\n";

      thtm2+="</TABLE>\n";
      thtm+=("<TR><TD VALIGN=\"top\" ALIGN=\"right\">Ingredient "+i+"/"+product.ingredientCount()+"<BR><BUTTON TYPE=\"button\" onClick=\"go_topwin('"+response.encodeURL(SERVLETNAME)+"?query="+cpd.getID()+"[cid]')\"><B>See cpd</B></BUTTON></TD><TD BGCOLOR=\"white\">"+thtm2+"</TD></TR>\n");
    }
    thtm+="</TABLE>\n";
    htm+=thtm;
    return htm;
  }

  /////////////////////////////////////////////////////////////////////////////
  private static String ResultCompoundsHtm(CompoundList cpds,HttpServletResponse response,int nmax)
  {
    if (cpds==null) return ("<H2>Results: 0 hits</H2>");
    DrugDBQuery dbquery = cpds.getQuery();
    String htm="";
    String thtm="<TABLE WIDTH=\"100%\">\n";
    thtm+=("<TR><TH WIDTH=\"8%\"></TH>");
    for (String tag: new String[]{"Structure","Names","ATCs","Products"})
      thtm+=("<TH WIDTH=\"23%\" BGCOLOR=\"#DDDDDD\">"+tag+"</TH>");
    thtm+=("</TR>\n");
    List<DrugDBCompound> cpds_sorted = null;
    if (dbquery.getType().equals("simstruct"))
      cpds_sorted = cpds.getAllSortedBySimilarity();
    else if (dbquery.getType().equals("substruct"))
      cpds_sorted = cpds.getAllSortedByMwt();
    else
      cpds_sorted = cpds.getAllSortedByRelevance();

    String depictopts="kekule=TRUE";
    if (dbquery.getType().matches("^.*struct$"))
      try { depictopts+=("&smartscode="+URLEncoder.encode(dbquery.getText(),"UTF-8")); } catch (Exception e) { }

    int i_hit=0;
    for (DrugDBCompound cpd: cpds_sorted)
    {
      ++i_hit;
      Integer cpd_id = cpd.getID();
      String rhtm="<TR>\n";
      //ID, popup link:
      rhtm+=("<TD VALIGN=\"top\" ALIGN=\"right\">"+i_hit+".<BR/>\n");
      rhtm+=("<BUTTON TYPE=\"button\" onClick=\"javascript:go_popup('"+response.encodeURL(SERVLETNAME)+"?query="+cpd_id+"[cid]&noform=TRUE','cpdwin','width=700,height=800,scrollbars=1,resizable=1')\"><B>See cpd</B></BUTTON>\n");
      if (dbquery.getType().equals("simstruct"))
        rhtm+=("<BR>"+String.format("sim=%.2f",cpd.getSimilarity()));
      rhtm+=("</TD>\n");

      //Depiction:
      String imghtm;
      if (cpd.isLarge())
        imghtm=("<I>(LARGE MOLECULE)</I><BR>MF = "+cpd.getMolformula()+"<BR>MWT = "+String.format("%.1f",cpd.getMwt())+"\n");
      else if (cpd.getAtomCount()==0)
        imghtm=("<I>(MOLECULE UNAVAILABLE)</I>\n");
      else
        imghtm=((cpd.getSmiles()!=null)?HtmUtils.Smi2ImgHtm(cpd.getSmiles(),depictopts,120,150,mol2img_servleturl,true,4,"go_zoom_smi2img"):"");
      rhtm+=("<TD BGCOLOR=\"white\" ALIGN=\"center\" VALIGN=\"top\">"+imghtm+"<BR>\n"+cpd_id+"</TD>");

      //Names:
      rhtm+=("<TD BGCOLOR=\"white\" VALIGN=\"top\"><UL>");
      int i_name=0;
      NameList cnames = dbquery.getType().matches("^.*txt$")?cpd.getNames().getNamesSortedByNiceness(dbquery.getText()):cpd.getNames().getNamesSortedByNiceness();
      for (Name name: cnames)
      {
        String n = (dbquery.getType().matches("^.*txt$"))?(name.toString().replaceAll("(?i)("+dbquery.getText()+")","<strong>$1</strong>")):name.toString();
        rhtm+=("<LI>"+n);
        if (++i_name>=10) { break; }
      }
      rhtm+=("</UL>");
      rhtm+=("<CENTER><SMALL><I>[Names: "+cpd.nameCount()+"]</I></SMALL></CENTER>\n");
      rhtm+=("</TD>");

      //ATCs:
      rhtm+=("<TD BGCOLOR=\"white\" VALIGN=\"top\">");
      for (ATC atc: cpd.getAtcs())
      {
        if (atc.getName(1)==null) break;
        //for (int lev=1;lev<=4;++lev) rhtm+=(((lev>1)?" : ":"")+atc.getName(lev));
        rhtm+=(atc.getName(3)+": "+atc.getName(4));
        rhtm+=("<P>\n");
      }
      rhtm+=("<CENTER><SMALL><I>[ATCs: "+cpd.atcCount()+"]</I></SMALL></CENTER>\n");
      rhtm+=("</TD>");

      //Products:
      rhtm+=("<TD BGCOLOR=\"white\" ALIGN=\"left\" VALIGN=\"top\"><UL>\n");
      int i_pname=0;
      NameList pnames = dbquery.getType().matches("^.*txt$")?cpd.getProductnames().getNamesSortedByNiceness(dbquery.getText()):cpd.getProductnames().getNamesSortedByNiceness();
      for (Name name: pnames)
      {
        String n = (dbquery.getType().matches("^.*txt$"))?(name.toString().replaceAll("(?i)("+dbquery.getText()+")","<strong>$1</strong>")):name.toString();
        rhtm+=("<LI>"+n+"\n");
        if (++i_pname>=10) break;
      }
      rhtm+=("</UL>");
      rhtm+=("<CENTER><SMALL><I>[Products: "+cpd.productCount()+"]</I></SMALL></CENTER>\n");
      rhtm+=("</TD>");

      thtm+=(rhtm+"\n");
      if (i_hit>=100) { break; }
    }
    thtm+="</TABLE>\n";
    htm+=("<H2>Results: "+cpds.size()+" hits</H2>");
    if (cpds.size()==nmax)
      htm+=("(N_MAX = "+cpds.size()+")\n<BLOCKQUOTE>\n"+thtm+"</BLOCKQUOTE>");
    else if (cpds.size()>0)
      htm+=("<BLOCKQUOTE>\n"+thtm+"</BLOCKQUOTE>");
    return htm;
  }

  /////////////////////////////////////////////////////////////////////////////
  private static String JavaScript()
  {
    return(
"var childwins = new Array();\n"+
"function go_drugdb(form)\n"+
"{\n"+
"  if (checkform(form))\n"+
"    form.submit();\n"+
"}\n"+
"function checkform(form)\n"+
"{\n"+
"  if (!form.query.value) {\n"+
"    alert('ERROR: No query specified');\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"function go_topwin(url)\n"+
"{\n"+
"  //var topwin = window.top; //Should work but does not.\n"+
"  var topwin = window;\n"+
"  while (topwin.opener != null)\n"+
"  {\n"+
"    topwin = topwin.opener;\n"+
"  }\n"+
"  topwin.focus();\n"+
"  topwin.location.replace(url);\n"+
"}\n"+
"function go_parentwin(url)\n"+
"{\n"+
"  var parentwin = window.opener;\n"+
"  //var parentwin = window.parent; //Should work but does not.\n"+
"  parentwin.focus();\n"+
"  parentwin.location.assign(url);\n"+
"}\n"+
"function go_popup(url,name,opts)\n"+
"{\n"+
"  var cwin = window.open(url,name,opts);\n"+
"  childwins.push(cwin);\n"+
"}\n"+
"function close_childwins(win)\n"+
"{\n"+
"  if (win==null || (typeof win)=='undefined') return;\n"+
"  if ((typeof win.childwins)=='undefined') return;\n"+
"  while (win.childwins.length>0)\n"+
"  {\n"+
"    var cwin=win.childwins.shift();\n"+
"    if ((typeof cwin.childwins)!='undefined' && cwin.childwins.length>0) close_childwins(cwin); //recurse\n"+
"    cwin.focus();\n"+
"    cwin.close();\n"+
"  }\n"+
"}\n"+
"function go_reset(url)\n"+
"{\n"+
"  close_childwins(window);\n"+
"  window.location.replace(url)\n"+
"}\n"+
"\n"+
"/// JSME stuff:\n"+
"function StartJSME()\n"+
"{\n"+
"  var cwin = window.open('"+JSMEURL+"','JSME','width=500,height=450,scrollbars=0,location=0,resizable=1');\n"+
"  childwins.push(cwin);\n"+
"}\n"+
"function fromJSME(smiles)\n"+
"{\n"+
"  // this function is called from JSME window\n"+
"  if (smiles=='')\n"+
"  {\n"+
"    alert('ERROR: no molecule submitted');\n"+
"    return;\n"+
"  }\n"+
"  var form=document.mainform;\n"+
"  form.query.value=smiles+'[substruct]';\n"+
"}\n"
    );
  }
  /////////////////////////////////////////////////////////////////////////////
  private static String HelpHtm()
  {
    return (
    "<H1>"+APPNAME+" help</H1>\n"+
    "<P>\n"+
    "<I><B>BETA RELEASE:</B> Usability under review; feedback invited.</I>\n"+
    "<P>\n"+
    "<H2>Introduction</H2>\n"+
    APPNAME+" is a research database developed at the University of New Mexico Translational Informatics Division.\n"+
    "The database is designed to contain all compounds approved for use as human therapeutics.\n"+
    APPNAME+" links and integrates knowledge across multiple domains, from multiple sources, including: FDA, USP/USAN, WHO,\n"+
    "PubChem, ChEMBL, and others.  Thorough and onging manual curation has ensured high accuracy and completeness.\n"+
    "This web client does not provide full access to "+APPNAME+", and is intended for convenient, interactive access to\n"+
    "a subset of that information.\n"+
    "<P>\n"+
    "<H2>Compounds, Ingredients, Products</H2>\n"+
    "Drugs are considered in two important ways, (1) Distinct chemical entities, compounds, often patented as such\n"+
    "and manufactured to high purity standards, and (2) Pharmaceutical products, which combine multiple inactive\n"+
    "and active ingredients, with varying dosage and formulations.  Each view is relevant to research,\n"+
    "and to clinical effects and outcomes.  "+APPNAME+" focuses on drug compounds by default, but provides\n"+
    "views of products also.  Each product contains one or more active ingredients.  Each ingredient specifies an\n"+
    "active moiety (compound) and quantity, responsible for the theraputic activity.\n"+
    "<P>\n"+
    "<TABLE WIDTH=\"100%\"><TR><TD ALIGN=\"center\">"+
    "<IMG BORDER=0 SRC=\"/tomcat"+CONTEXTPATH+"/images/drugdb_schema.png\">"+
    "</TD></TR></TABLE>\n"+
    "<P>\n"+
    "<H2>Accurate Chemical Structures</H2>\n"+
    "Accuracy of chemical structures is a key feature of "+APPNAME+" and focus of curation efforts.  Although \n"+
    "pharmaceutical compounds may be well known in general, determining and representing fully specified,\n"+
    "accurate structures can be challenging.  In addition to correct molecular formulae, salt forms, and\n"+
    "molecular graphs, accurate stereochemistry is also prioritized.  In cases of racemic mixtures or\n"+
    "\"relative stereo\", stereo depiction has been suppressed to avoid confusion.\n"+
    "<P>\n"+
    "<H2>Targets, Classes, ATC, MOA</H2>\n"+
    "ATC drug classes, protein targets, and mechanism of action (MOA) annotations comprise data associating\n"+
    "drugs with physiological effects.  These data are also key features of "+APPNAME+".\n"+
    "Targets are comprised of one or more target components, each normally associated with a gene and protein,\n"+
    "from human or other specified organisms.\n"+
    "The Anatomical Therapeutic Chemical (ATC) classification system is from the WHO.\n"+
    "The five hierarchical levels are (1) anatomical, (2) therapeutic, (3) pharmacological,\n"+
    "(4) chemical subgroup, and (5) chemical substance.  Level 5 is ignored in this application,\n"+
    "as substance identification is better handled by other means.  Levels 2 and 3 are typically of greatest\n"+
    "interest as drug classifiers, hence displayed in the search results.\n"+
    "<P>\n"+
    "<H2>Large Molecules</H2>\n"+
    "Drug molecules exceeding 1000 AMU are not depicted.  Typically these are biologics.\n"+
    "<P>\n"+
    "<H2>Why another resource? Relation to other databases.</H2>\n"+
    "There are many sources for data about approved drugs.  What is the motivation and special\n"+
    "value of "+APPNAME+"?  Almost all the data in "+APPNAME+" is available elsewhere, but it can be difficult or\n"+
    "laborious to find. Moreover, drug informatics spans multiple domains, including: chemical,\n"+
    "biological, medical, regulatory, and business.  Integrating these domains with high accuracy in a research\n"+
    "database is the unmet need "+APPNAME+" seeks to fill.\n"+
    "\n"+
    "<P>\n"+
    "<H2>Usage Guide</H2>\n"+
    "A <b>search</b> returns a hitlist of drug compounds.  A subset of names is shown, ranked for relevance\n"+
    "and usability.  Fields searched include compound names, synonyms, and product names.\n"+
    "A subset of products is shown.  A subset of targets are shown.  To see full lists of names,\n"+
    "targets, and products, see full drug compound record (via ID link, or [cid] query).\n"+
    "<P>\n"+
    "<TABLE WIDTH=\"100%\" CELLSPACING=\"2\" CELLPADDING=\"2\">\n"+
    "<TR><TH WIDTH=\"50%\">syntax</TH><TH>query</TH></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>STR</b></code></TD><TD>substring name search [default]</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>STR[subtxt]</b></code></TD><TD>substring name search</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>STR[fulltxt]</b></code></TD><TD>full name search</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>UNII[unii]</b></code></TD><TD>get compound by UNII ID</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>SMILES[substruct]</b></code></TD><TD>search compounds by sub-structure</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>SMILES[fullstruct]</b></code></TD><TD>search compounds by full-structure</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>CODE[atc1]</b></code></TD><TD>search by ATC Level 1 code</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>CODE[atc2]</b></code></TD><TD>search by ATC Level 2 code</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>CODE[atc3]</b></code></TD><TD>search by ATC Level 3 code</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>CODE[atc4]</b></code></TD><TD>search by ATC Level 4 code</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>CID[cid]</b></code></TD><TD>get compound by ID</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>PID[pid]</b></code></TD><TD>get product by ID</TD></TR>\n"+
    "<TR><TH COLSPAN=\"2\">Example Queries:</TH></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>penicillin[fulltxt]</b></code></TD><TD>matches name \"penicillin\"</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>amphetamine[subtxt]</b></code></TD><TD>matches name substring \"amphetamine\"</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>N1C2=CC=CC=C2C(=NCC1)C1=CC=CC=C1[substruct]</b></code></TD><TD>benzodiazepine derivatives</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>C12CCCC1CCC1C2CCC2=CCCCC12[substruct]</b></code></TD><TD>steroids</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>J01CE[atc4]</b></code></TD><TD>beta-lactamase sensitive penicillins</TD></TR>\n"+
    "<TR><TD ALIGN=\"right\" BGCOLOR=\"white\"><code><b>G04BE[atc4]</b></code></TD><TD>drugs used in erectile dysfunction</TD></TR>\n"+
    "</TABLE>\n"+
    "<HR>\n"+
    "<H2>Source and related resources</H2>\n"+
    "<UL>\n"+
    "<LI><A HREF=\"http://www.whocc.no/atc/structure_and_principles/\">ATC reference</A>\n"+
    "<LI><A HREF=\"http://carlsbad.health.unm.edu/\">Carlsbad</A>\n"+
    "<LI><A HREF=\"http://www.ebi.ac.uk/chembldb/\">ChEMBL</A>\n"+
    "<LI><A HREF=\"http://dailymed.nlm.nih.gov/dailymed/\">DailyMed (NLM)</A>\n"+
    "<LI><A HREF=\"http://www.drugbank.ca/\">DrugBank</A>\n"+
    "<LI><A HREF=\"http://www.iuphar-db.org/\">IUPHAR</A>\n"+
    "<LI><A HREF=\"http://open.fda.gov/\">OpenFDA</A>\n"+
    "<LI><A HREF=\"http://pubchem.ncbi.nlm.nih.gov/\">PubChem</A>\n"+
    "<LI><A HREF=\"http://www.nlm.nih.gov/research/umls/rxnorm/\">RxNorm (NLM)</A>\n"+
    "<LI><A HREF=\"http://www.sunsetmolecular.com/\">WOMBAT</A>\n"+
    "</UL>\n"+
    "<HR>\n"+
    "N_MAX (hits): "+N_MAX+"\n"+
    "<HR>\n"+
    "Thanks to ChemAxon for the use of JChem in this application.\n"+
    "<P>\n"+
    "Database design and curation: Oleg Ursu, Tudor Oprea<br/>\n"+
    "Webapp: Jeremy Yang\n"
    );
  }
  /////////////////////////////////////////////////////////////////////////////
  /**	Called once per servlet instantiation; read servlet parameters (from web.xml).
  */
  public void init(ServletConfig conf) throws ServletException
  {
    super.init(conf);
    CONTEXT=this.getServletContext();	// inherited method
    CONTEXTPATH=CONTEXT.getContextPath();
    //CONFIG=conf;
    APPNAME=conf.getInitParameter("APPNAME");
    if (APPNAME==null) APPNAME=this.getServletName();
    UPLOADDIR=conf.getInitParameter("UPLOADDIR");
    if (UPLOADDIR==null)
      throw new ServletException("Please supply UPLOADDIR parameter");
    DBDIR=conf.getInitParameter("DBDIR");
    if (DBDIR==null) DBDIR="/home/data/derby/drugdb/.config/localdb";
    DBNAME=conf.getInitParameter("DBNAME");
    if (DBNAME==null) DBNAME="db";
    DBSCHEMA=conf.getInitParameter("DBSCHEMA");
    if (DBSCHEMA==null) DBSCHEMA="APP";
    JC_PTABLE=conf.getInitParameter("JC_PTABLE");
    if (JC_PTABLE==null) JC_PTABLE="JCHEMPROPERTIES";
    JC_STABLE=conf.getInitParameter("JC_STABLE");
    if (JC_STABLE==null) JC_STABLE="STRUCTURES";
    JSMEURL=conf.getInitParameter("JSMEURL");
    if (JSMEURL==null) { JSMEURL="/tomcat/"+APPNAME+"/jsme_win.html"; }

    LOGDIR=conf.getInitParameter("LOGDIR")+CONTEXTPATH;
    if (LOGDIR==null) LOGDIR="/usr/local/tomcat/logs"+CONTEXTPATH;
    try { N_MAX=Integer.parseInt(conf.getInitParameter("N_MAX")); }
    catch (Exception e) { N_MAX=100; }

    try { String s=conf.getInitParameter("DEBUG"); if (s.equalsIgnoreCase("TRUE")) DEBUG=true; }
    catch (Exception e) { DEBUG=false; }

    try {
      JC_CHAND = jchemdb_utils.GetConnectionHandler(DBDIR,DBNAME,JC_PTABLE);
      JC_SEARCHER = jchemdb_utils.GetJChemSearch(JC_CHAND,DBSCHEMA,JC_STABLE);
      DBCON = JC_CHAND.getConnection();
    }
    catch (Exception e) {
      CONTEXT.log("Connection failed: "+e.getMessage());
      throw new ServletException(e.getMessage());
    }
    if (JC_CHAND!=null && JC_SEARCHER!=null && DBCON!=null)
      CONTEXT.log("Connection ok: "+DBDIR+"/"+DBNAME);

    try { JCHEMVER_DB = drugdb_utils.JChemVersion(DBCON); } catch (SQLException e) { JCHEMVER_DB="unknown"; }

    try {
      StringBuilder sb = new StringBuilder();
      JChemDbUpdate(JC_CHAND,sb);
      CONTEXT.log(sb.toString());
    }
    catch (Exception e) { CONTEXT.log(e.toString()); }
  }
  /////////////////////////////////////////////////////////////////////////////
  /**	Update JChem DB.  Client/db version mismatch not always requires updates. */
  private void JChemDbUpdate(ConnectionHandler jc_chand,StringBuilder sb)
	throws Exception
  {
    sb.append("UPDATE JChem DB v"+JCHEMVER_DB+" by JChem v"+VersionInfo.getVersion()+":\n");
    Updater upd = new Updater(jc_chand);
    int i_update=0;
    while (true)
    {
      Updater.UpdateInfo updi = upd.getNextUpdateInfo();
      if (updi==null) break;
      if (updi.isOperationRequired) 
      {
        ++i_update;
        sb.append(updi.message+"\n");
        sb.append(updi.processingMessage+"\n");
        String msg = upd.performCurrentUpdate();
        sb.append(msg+"\n");
      }
    }
    sb.append("REQUIRED UPDATES COMPLETED: "+i_update+"\n");
  }
  /////////////////////////////////////////////////////////////////////////////
  public void destroy()
  {
    try { JC_CHAND.close(); }
    catch (Exception e) { CONTEXT.log("Connection close failed: "+e.getMessage()); }
  }
  /////////////////////////////////////////////////////////////////////////////
  public void doGet(HttpServletRequest request,HttpServletResponse response)
      throws IOException, ServletException
  {
    doPost(request,response);
  }
}
