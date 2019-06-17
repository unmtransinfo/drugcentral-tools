package edu.unm.health.biocomp.drugdb;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import java.sql.*; //DriverManager,Driver,SQLException,Connection,Statement,ResultSet

import chemaxon.formats.*; //MolImporter,MolFormatException
import chemaxon.util.*; //ConnectionHandler
import chemaxon.jchem.db.*; //JChemSearch,Updater,UpdateHandler
import chemaxon.jchem.version.*; //VersionInfo
import com.chemaxon.version.VersionInfo;
import chemaxon.struc.*; //Molecule
import chemaxon.sss.search.*; //JChemSearchOptions
import chemaxon.enumeration.supergraph.SupergraphException;

import edu.unm.health.biocomp.util.*; //time_utils
import edu.unm.health.biocomp.util.db.*; //derby_utils
import edu.unm.health.biocomp.text.*; //Name,NameList
import edu.unm.health.biocomp.jchemdb.*; //jchemdb_utils

/**	Utilities for DrugDB queries and admin.
	Note: "SELECT DISTINCT" not allowed with BLOB column.

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

	IDEA: Improve speed by minimizing search SQL.  Then separate queries to get
	full records.

	@author Jeremy J Yang
*/
public class drugdb_utils
{
  private static ArrayList<String> JC_FIELDNAMES = new ArrayList<String>(Arrays.asList("ID","CD_ID","NAME","CAS_REG_NO"));

  /////////////////////////////////////////////////////////////////////////////
  private static void DescribeDB(ConnectionHandler chand,String dbtable)
	throws SQLException
  {
    System.err.println(DBDescribeTxt(chand.getConnection()));
    System.err.println("PropertyTable: "+chand.getPropertyTable());
    JChemSearch searcher = jchemdb_utils.GetJChemSearch(chand,dbschema,stable);
    System.err.println("StructureTable: "+searcher.getStructureTable());
    if (dbtable!=null)
    {
      derby_utils.DescribeTable(chand.getConnection(),dbschema,dbtable,verbose);
    }
    else
    {
      ArrayList<String> tlist = derby_utils.GetTableList(chand.getConnection(),dbschema);
      for (String tname: tlist)
        System.err.println("\t"+tname);
    }
  }
  /////////////////////////////////////////////////////////////////////////////
  private static void DescribeQuery(DrugDBQuery dbquery)
	throws SQLException
  {
    System.err.println("DBQuery (raw): "+dbquery.toString());
    System.err.println("DBQuery type: "+dbquery.getType());
    System.err.println("DBQuery text: "+dbquery.getText());
    System.err.println("DBQuery isValid: "+dbquery.isValid());
  }
  /////////////////////////////////////////////////////////////////////////////
  public static String DBDescribeTxt(Connection dbcon)
	throws SQLException
  {
    String txt="";
    txt+=("Total compounds: "+CompoundCount(dbcon)+"\n");
    txt+=("Total synonyms: "+SynonymCount(dbcon)+"\n");
    txt+=("Total products: "+ProductCount(dbcon)+"\n");
    txt+=("Total ingredients: "+IngredientCount(dbcon)+"\n");
    txt+=("Total targets: "+TargetCount(dbcon)+"\n");
    txt+=("Total activities: "+ActivityCount(dbcon)+"\n");
    return txt;
  }
  /////////////////////////////////////////////////////////////////////////////
  public static int ProductCount(Connection dbcon) throws SQLException
  {
    ResultSet rset=derby_utils.ExecuteSql(dbcon,"SELECT COUNT(ID) FROM APP.PRODUCT");
    return (rset.next()?rset.getInt(1):0);
  }
  public static int IngredientCount(Connection dbcon) throws SQLException
  {
    ResultSet rset=derby_utils.ExecuteSql(dbcon,"SELECT COUNT(ID) FROM APP.ACTIVE_INGREDIENT");
    return (rset.next()?rset.getInt(1):0);
  }
  public static int CompoundCount(Connection dbcon) throws SQLException
  {
    ResultSet rset=derby_utils.ExecuteSql(dbcon,"SELECT COUNT(ID) FROM APP.STRUCTURES");
    return (rset.next()?rset.getInt(1):0);
  }
  public static int TargetCount(Connection dbcon) throws SQLException
  {
    ResultSet rset=derby_utils.ExecuteSql(dbcon,"SELECT COUNT(ID) FROM APP.TARGET_DICTIONARY");
    return (rset.next()?rset.getInt(1):0);
  }
  public static int ActivityCount(Connection dbcon) throws SQLException
  {
    ResultSet rset=derby_utils.ExecuteSql(dbcon,"SELECT COUNT(ACT_ID) FROM APP.ACTIVITIES");
    return (rset.next()?rset.getInt(1):0);
  }
  public static int SynonymCount(Connection dbcon) throws SQLException
  {
    ResultSet rset=derby_utils.ExecuteSql(dbcon,"SELECT COUNT(SYN_ID) FROM APP.SYNONYMS");
    return (rset.next()?rset.getInt(1):0);
  }
  public static int ReferenceCount(Connection dbcon) throws SQLException
  {
    ResultSet rset=derby_utils.ExecuteSql(dbcon,"SELECT COUNT(ID) FROM APP.REFERENCE");
    return (rset.next()?rset.getInt(1):0);
  }
  public static String JChemVersion(Connection dbcon) throws SQLException
  {
    ResultSet rset=derby_utils.ExecuteSql(dbcon,"SELECT PROP_VALUE FROM APP.JCHEMPROPERTIES WHERE PROP_NAME='table.APP.STRUCTURES.JChemVersion'");
    return (rset.next()?rset.getString(1):"");
  }
  /**	June 24, 2015:	6.2.3 to 6.3.1	*/
  public static boolean SetJChemVersion(Connection dbcon,String ver) throws SQLException
  {
    String sql="UPDATE APP.JCHEMPROPERTIES SET PROP_VALUE='"+ver+"' WHERE PROP_NAME='table.APP.STRUCTURES.JChemVersion'";
    boolean ok=derby_utils.Execute(dbcon,sql);
    return ok;
  }
  /////////////////////////////////////////////////////////////////////////////
  /// COMPOUND queries (all return same fields):
  /// Cannot assume all STRUCTURES link to ACTIVE_INGREDIENTs, PRODUCTs,
  /// thus must use LEFT OUTER JOIN.
  /////////////////////////////////////////////////////////////////////////////
  private static String sql_cpd=
"SELECT s.ID, s.CD_SMILES, s.CD_STRUCTURE, s.CD_MOLWEIGHT, s.CAS_REG_NO, s.NAME \"STRUCT_NAME\", "+
"p.ID \"PRODUCT_ID\", p.GENERIC_NAME, p.PRODUCT_NAME, p.ACTIVE_INGREDIENT_COUNT, "+
"ai.ACTIVE_MOIETY_NAME, ai.SUBSTANCE_NAME, syn.NAME \"SYNONYM\", "+
"atc.CODE \"ATC_CODE\", atc.L1_CODE, atc.L1_NAME, atc.L2_CODE, atc.L2_NAME, "+
"atc.L3_CODE, atc.L3_NAME, atc.L4_CODE, atc.L4_NAME "+
"FROM APP.STRUCTURES AS s "+
"LEFT OUTER JOIN APP.ACTIVE_INGREDIENT AS ai ON s.ID = ai.STRUCT_ID "+
"LEFT OUTER JOIN APP.PRODUCT AS p ON ai.NDC_PRODUCT_CODE = p.NDC_PRODUCT_CODE "+
"LEFT OUTER JOIN APP.IDENTIFIER AS id ON s.ID = id.STRUCT_ID "+
"LEFT OUTER JOIN APP.SYNONYMS AS syn ON s.ID = syn.ID "+
"LEFT OUTER JOIN APP.STRUCT2ATC AS s2atc ON s.ID = s2atc.STRUCT_ID "+
"LEFT OUTER JOIN APP.ATC AS atc ON atc.CODE = s2atc.ATC_CODE ";
  private static String sql_cpd_get=
"SELECT s.ID, s.CD_SMILES, s.CD_STRUCTURE, s.CD_MOLWEIGHT, s.CAS_REG_NO, s.NAME \"STRUCT_NAME\", "+
"p.ID \"PRODUCT_ID\", p.GENERIC_NAME, p.PRODUCT_NAME, p.ACTIVE_INGREDIENT_COUNT, "+
"ai.ACTIVE_MOIETY_NAME, ai.SUBSTANCE_NAME, syn.NAME \"SYNONYM\", "+
"atc.CODE \"ATC_CODE\", atc.L1_CODE, atc.L1_NAME, atc.L2_CODE, atc.L2_NAME, "+
"atc.L3_CODE, atc.L3_NAME, atc.L4_CODE, atc.L4_NAME, "+
"apv.APPROVAL \"APPROVAL_DATE\", apv.TYPE \"APPROVAL_TYPE\", apv.APPLICANT \"APPROVAL_APPLICANT\" "+
"FROM APP.STRUCTURES AS s "+
"LEFT OUTER JOIN APP.ACTIVE_INGREDIENT AS ai ON s.ID = ai.STRUCT_ID "+
"LEFT OUTER JOIN APP.PRODUCT AS p ON ai.NDC_PRODUCT_CODE = p.NDC_PRODUCT_CODE "+
"LEFT OUTER JOIN APP.SYNONYMS AS syn ON s.ID = syn.ID "+
"LEFT OUTER JOIN APP.STRUCT2ATC AS s2atc ON s.ID = s2atc.STRUCT_ID "+
"LEFT OUTER JOIN APP.ATC AS atc ON atc.CODE = s2atc.ATC_CODE "+
"LEFT OUTER JOIN APP.APPROVAL AS apv ON s.ID = apv.STRUCT_ID ";
  public static ResultSet SearchCompoundsByStructureName(Connection dbcon,String qstr,Boolean full)
	throws SQLException
  {
    String sql=sql_cpd;
    if (full) sql+=(" WHERE UPPER(s.NAME) = '"+qstr.toUpperCase()+"'");
    else       sql+=(" WHERE UPPER(s.NAME) LIKE '%"+qstr.toUpperCase()+"%'");
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  public static ResultSet SearchCompoundsByIngredientName(Connection dbcon,String qstr,Boolean full)
	throws SQLException
  {
    String sql=sql_cpd;
    if (full) sql+=(" WHERE UPPER(ai.SUBSTANCE_NAME) = '"+qstr.toUpperCase()+"'");
    else       sql+=(" WHERE UPPER(ai.SUBSTANCE_NAME) LIKE '%"+qstr.toUpperCase()+"%'");
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  public static ResultSet SearchCompoundsBySynonym(Connection dbcon,String qstr,Boolean full)
	throws SQLException
  {
    String sql=sql_cpd;
    if (full) sql+=(" WHERE UPPER(syn.NAME) = UPPER('"+qstr+"')");
    else       sql+=(" WHERE UPPER(syn.NAME) LIKE '%"+qstr.toUpperCase()+"%'");
    //System.err.println("DEBUG: SearchCompoundsBySynonym sql =\n"+sql);
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  public static ResultSet SearchCompoundsByProductName(Connection dbcon,String qstr,Boolean full)
	throws SQLException
  {
    String sql=sql_cpd;
    if (full) sql+=(" WHERE UPPER(p.PRODUCT_NAME) = '"+qstr.toUpperCase()+"'");
    else       sql+=(" WHERE UPPER(p.PRODUCT_NAME) LIKE '%"+qstr.toUpperCase()+"%'");
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  public static ResultSet SearchCompoundsByUNII(Connection dbcon,String unii)
	throws SQLException
  {
    String sql=sql_cpd;
    sql+=" WHERE ai.ACTIVE_MOIETY_UNII = '"+unii+"'";
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  public static ResultSet SearchCompoundsByATC(Connection dbcon,String code,Integer level)
	throws SQLException
  {
    String sql=sql_cpd;
    sql+=" WHERE atc.L"+level+"_CODE = '"+code+"'";
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  public static ResultSet SearchCompoundsByExtID(Connection dbcon,String id,String idtype)
	throws SQLException
  {
    String sql=sql_cpd;
    sql+=" WHERE id.IDENTIFIER = '"+id+"'";
    if (idtype!=null)
      sql+=" AND UPPER(id.ID_TYPE) = UPPER('"+idtype+"')";
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  public static ResultSet SearchCompoundsByID(Connection dbcon,List<Integer> ids)
	throws SQLException
  {
    String sql=sql_cpd;
    sql+=" WHERE s.ID IN ( ";
    for (int i=0;i<ids.size(); ++i)
      sql+=(((i==0)?"":",")+ids.get(i));
    sql+=(")");
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }

  /**	Get implies more data returned than Search. */
  public static ResultSet GetCompoundByID(Connection dbcon,Integer id)
	throws SQLException
  {
    String sql=sql_cpd_get;
    sql+=" WHERE s.ID = "+id;
    //System.err.println("DEBUG: GetCompoundByID sql =\n"+sql);
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }



  /////////////////////////////////////////////////////////////////////////////
  /// PRODUCT queries (all return same fields):
  /////////////////////////////////////////////////////////////////////////////
  private static String sql_product="SELECT p.ID, p.NDC_PRODUCT_CODE, p.FORM, p.GENERIC_NAME, p.PRODUCT_NAME, p.ROUTE, p.MARKETING_STATUS, p.ACTIVE_INGREDIENT_COUNT, ai.ACTIVE_MOIETY_NAME, ai.SUBSTANCE_NAME, s.CAS_REG_NO, s.CD_SMILES, s.CD_STRUCTURE, s.CD_MOLWEIGHT, n.NAME \"SYNONYM\" FROM APP.PRODUCT AS p, APP.ACTIVE_INGREDIENT AS ai, APP.STRUCTURES AS s, APP.SYNONYMS AS n WHERE p.NDC_PRODUCT_CODE = ai.NDC_PRODUCT_CODE AND ai.STRUCT_ID = s.ID AND n.ID = s.ID";
  public static ResultSet SearchProductsByProductName(Connection dbcon,String qstr,Boolean full)
	throws SQLException
  {
    String sql=sql_product;
    if (full) sql+=" AND UPPER(p.PRODUCT_NAME) = '"+qstr.toUpperCase()+"'";
    else       sql+=" AND UPPER(p.PRODUCT_NAME) LIKE '%"+qstr.toUpperCase()+"%'";
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  public static ResultSet SearchProductsByIngredientName(Connection dbcon,String qstr,Boolean full)
	throws SQLException
  {
    String sql=sql_product;
    if (full)  sql+=" AND UPPER(ai.SUBSTANCE_NAME) = '"+qstr.toUpperCase()+"'";
    else        sql+=" AND UPPER(ai.SUBSTANCE_NAME) LIKE '%"+qstr.toUpperCase()+"%'";
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  public static ResultSet SearchProductsByUNII(Connection dbcon,String unii)
	throws SQLException
  {
    String sql=sql_product;
    sql+=" AND ai.ACTIVE_MOIETY_UNII = '"+unii+"'";
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  public static ResultSet GetProductByID(Connection dbcon,Integer id)
	throws SQLException
  {
    String sql=sql_product;
    sql+=" AND p.ID = "+id;
    System.err.println("DEBUG: GetProductByID sql =\n"+sql);
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  public static ResultSet GetCompoundProducts(Connection dbcon,int id)
	throws SQLException
  {
    String sql=sql_product;
    sql+=" AND s.ID = "+id;
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  public static ResultSet SearchProductsByCompoundID(Connection dbcon,List<Integer> ids)
	throws SQLException
  {
    String sql=sql_product;
    sql+=" AND s.ID IN ( ";
    for (int i=0;i<ids.size(); ++i)
      sql+=(((i==0)?"":",")+ids.get(i));
    sql+=(")");
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }

  /////////////////////////////////////////////////////////////////////////////
  /// ACTIVITY - TARGET queries:
  /////////////////////////////////////////////////////////////////////////////
  private static String sql_activity=
"SELECT s.ID \"CID\", a.ACT_ID, a.ACT_TYPE, a.ACT_VALUE, a.ACT_UNIT, actsrc.SOURCE_NAME \"ACT_SOURCE\", a.ACT_COMMENT, "+
"a.RELATION, a.MOA, moasrc.SOURCE_NAME \"MOA_SOURCE\", a.REF_ID, a.MOA_REF_ID, a.ACTION_TYPE \"MOA_TYPE\", "+
"td.ID \"TID\", td.NAME \"TARGET_NAME\", td.CLASS, td.PROTEIN_TYPE, tc.ID \"TCID\", "+
"tc.ACCESSION \"PROTEIN_ACCESSION\", tc.SWISSPROT, tc.ORGANISM, tc.GENE \"GENE_SYMBOL\", "+
"tc.GENEID, tc.NAME \"PROTEIN_NAME\","+
"ref.ID \"REF_ID\", ref.PMID \"REF_PMID\", ref.TITLE \"REF_TITLE\", ref.DP_YEAR \"REF_YEAR\", "+
"ref.JOURNAL \"REF_JOURNAL\", ref.AUTHORS \"REF_AUTHORS\", ref.DOI \"REF_DOI\", ref.URL \"REF_URL\", "+
"moaref.ID \"MOA_REF_ID\", moaref.PMID \"MOA_REF_PMID\", moaref.TITLE \"MOA_REF_TITLE\", moaref.DP_YEAR \"MOA_REF_YEAR\", "+
"moaref.JOURNAL \"MOA_REF_JOURNAL\", moaref.AUTHORS \"MOA_REF_AUTHORS\", moaref.DOI \"MOA_REF_DOI\", moaref.URL \"MOA_REF_URL\" "+
"FROM APP.STRUCTURES AS s "+
"LEFT OUTER JOIN APP.ACTIVITIES AS a ON s.ID = a.STRUCT_ID "+
"LEFT OUTER JOIN APP.REFERENCE AS ref ON a.REF_ID = ref.ID "+
"LEFT OUTER JOIN APP.REFERENCE AS moaref ON a.REF_ID = moaref.ID "+
"JOIN APP.TARGET_DICTIONARY AS td ON a.TARGET_ID = td.ID "+
"JOIN APP.TD2TC AS td2tc ON td.ID = td2tc.TARGET_ID "+
"JOIN APP.TARGET_COMPONENT AS tc ON tc.ID = td2tc.COMPONENT_ID "+
"JOIN APP.DATA_SOURCE AS actsrc ON actsrc.SRC_ID = a.ACT_SOURCE "+
"JOIN APP.DATA_SOURCE AS moasrc ON moasrc.SRC_ID = a.ACT_SOURCE ";
  /////////////////////////////////////////////////////////////////////////////
  public static ResultSet GetCompoundActivities(Connection dbcon,int cid)
	throws SQLException
  {
    String sql=sql_activity+" WHERE s.ID = "+cid;
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  /////////////////////////////////////////////////////////////////////////////
  public static ResultSet GetCompoundTargetActivities(Connection dbcon,int cid,int tid)
	throws SQLException
  {
    String sql=sql_activity+" WHERE s.ID = "+cid+" AND td.ID = "+tid;
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }


  /////////////////////////////////////////////////////////////////////////////
  public static ResultSet GetCompoundSynonyms(Connection dbcon,int id)
	throws SQLException
  {
    String sql="SELECT DISTINCT syn.NAME \"SYNONYM\" FROM APP.SYNONYMS AS syn WHERE syn.ID = "+id;
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  /////////////////////////////////////////////////////////////////////////////
  public static ResultSet GetCompoundProductNames(Connection dbcon,int id)
	throws SQLException
  {
    String sql="SELECT DISTINCT p.PRODUCT_NAME FROM APP.PRODUCT AS p, APP.ACTIVE_INGREDIENT AS ai, APP.STRUCTURES AS s WHERE p.NDC_PRODUCT_CODE = ai.NDC_PRODUCT_CODE AND ai.STRUCT_ID = s.ID AND s.ID = "+id;
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }

  /////////////////////////////////////////////////////////////////////////////
  public static ResultSet GetProductIngredients(Connection dbcon,int id)
	throws SQLException
  {
    String sql="SELECT ai.ID, ai.ACTIVE_MOIETY_UNII, ai.ACTIVE_MOIETY_NAME, ai.UNIT, ai.QUANTITY, ai.SUBSTANCE_UNII, ai.SUBSTANCE_NAME, ai.NDC_PRODUCT_CODE, ai.STRUCT_ID, ai.QUANTITY_DENOM_UNIT, ai.QUANTITY_DENOM_VALUE, s.CD_SMILES, s.CD_STRUCTURE, s.CD_MOLWEIGHT, s.CAS_REG_NO, s.NAME \"STRUCT_NAME\" FROM APP.ACTIVE_INGREDIENT AS ai, APP.PRODUCT p, APP.STRUCTURES s WHERE ai.NDC_PRODUCT_CODE = p.NDC_PRODUCT_CODE AND ai.STRUCT_ID = s.ID AND p.ID = "+id;
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  /////////////////////////////////////////////////////////////////////////////
  public static ResultSet GetCompoundATCs(Connection dbcon,int id)
	throws SQLException
  {
    String sql="SELECT DISTINCT atc.CODE \"ATC_CODE\", atc.CHEMICAL_SUBSTANCE, atc.L1_CODE, atc.L1_NAME, atc.L2_CODE, atc.L2_NAME, atc.L3_CODE, atc.L3_NAME, atc.L4_CODE, atc.L4_NAME FROM APP.ATC AS atc, APP.STRUCT2ATC AS s2atc WHERE atc.CODE = s2atc.ATC_CODE AND s2atc.STRUCT_ID = "+id;
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  /////////////////////////////////////////////////////////////////////////////
  public static ResultSet GetCompoundUniis(Connection dbcon,int id)
	throws SQLException
  {
    String sql="SELECT DISTINCT ACTIVE_MOIETY_UNII FROM APP.ACTIVE_INGREDIENT AS ai WHERE ai.STRUCT_ID = "+id;
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }
  /////////////////////////////////////////////////////////////////////////////
  public static ResultSet GetCompoundIDs(Connection dbcon,int id)
	throws SQLException
  {
    String sql="SELECT DISTINCT IDENTIFIER \"ID_VAL\", ID_TYPE FROM APP.IDENTIFIER AS id WHERE id.STRUCT_ID = "+id;
    ResultSet rset=derby_utils.ExecuteSql(dbcon,sql);
    return rset;
  }

  /////////////////////////////////////////////////////////////////////////////
  /**   Search Db and return hitlist of products.
  */
  public static ProductList SearchProducts(Connection dbcon,ConnectionHandler chand,JChemSearch searcher,DrugDBQuery dbquery,StringBuilder log)
      throws SQLException,IOException
  {
    String qtxt=dbquery.getText();
    String qtype=dbquery.getType();

    ResultSet rset=null;
    ArrayList<Integer> ids = new ArrayList<Integer>();
    ProductList products = new ProductList();
    if (qtype.equalsIgnoreCase("subtxt") || qtype.equalsIgnoreCase("fulltxt"))
    {
      rset = SearchProductsByProductName(dbcon,qtxt,qtype.equalsIgnoreCase("fulltxt"));
      ResultSet2Products(dbcon,rset,products,100,log);
      if (rset!=null) rset.getStatement().close();
      rset = SearchProductsByIngredientName(dbcon,qtxt,qtype.equalsIgnoreCase("fulltxt"));
      ResultSet2Products(dbcon,rset,products,20,log);
      if (rset!=null) rset.getStatement().close();
    }
    else if (qtype.equalsIgnoreCase("unii"))
    {
      rset = SearchProductsByUNII(dbcon,qtxt);
      ResultSet2Products(dbcon,rset,products,100,log);
      if (rset!=null) rset.getStatement().close();
    }
    else
    {
      //outputs.add("ERROR: invalid query: \""+dbquery.toString()+"\"");
      System.err.println("ERROR: invalid query: \""+dbquery.toString()+"\"");
    }

    if (products.size()==0) return null;

    return products;
  }

  /////////////////////////////////////////////////////////////////////////////
  /**   Search Db and return hitlist of cpds.
	Assign matchpoints to hits so better matches rank higher.  E.g. structure
	name match better than synonym match.
  */
  public static CompoundList SearchCompounds(Connection dbcon,ConnectionHandler chand,JChemSearch searcher,DrugDBQuery dbquery,int nmax,StringBuilder log)
      throws Exception
  {
    String qtxt=dbquery.getText();
    String qtype=dbquery.getType();

    java.util.Date t_0 = new java.util.Date();

    ResultSet rset=null;
    ArrayList<Integer> ids = new ArrayList<Integer>();
    CompoundList cpds = new CompoundList();
    cpds.setQuery(dbquery);
    if (qtype.equalsIgnoreCase("subtxt") || qtype.equalsIgnoreCase("fulltxt"))
    {
      rset = SearchCompoundsByStructureName(dbcon,qtxt,qtype.equalsIgnoreCase("fulltxt"));
      ResultSet2Compounds(dbcon,rset,cpds,100,log);
      if (rset!=null) rset.getStatement().close();
      rset = SearchCompoundsBySynonym(dbcon,qtxt,qtype.equalsIgnoreCase("fulltxt"));
      ResultSet2Compounds(dbcon,rset,cpds,20,log);
      if (rset!=null) rset.getStatement().close();
      rset = SearchCompoundsByProductName(dbcon,qtxt,qtype.equalsIgnoreCase("fulltxt"));
      ResultSet2Compounds(dbcon,rset,cpds,10,log);
      if (rset!=null) rset.getStatement().close();
    }
    else if (qtype.equalsIgnoreCase("cidext"))
    {
      String extidtype=dbquery.getExtIdType();
      rset = SearchCompoundsByExtID(dbcon,qtxt,extidtype);
      ResultSet2Compounds(dbcon,rset,cpds,100.0F,log);
      if (rset!=null) rset.getStatement().close();
    }
    else if (qtype.equalsIgnoreCase("unii"))
    {
      rset = SearchCompoundsByUNII(dbcon,qtxt);
      ResultSet2Compounds(dbcon,rset,cpds,100.0F,log);
      if (rset!=null) rset.getStatement().close();
    }
    else if (qtype.toLowerCase().matches("atc[1-4]"))
    {
      rset = SearchCompoundsByATC(dbcon,qtxt,Integer.parseInt(qtype.replaceFirst("^(?i)atc([1-4])$","$1"))); //case-insensitive
      ResultSet2Compounds(dbcon,rset,cpds,100.0F,log);
      if (rset!=null) rset.getStatement().close();
    }
    else if (qtype.startsWith("substr")) //"substruct"
    {
      ArrayList<Object[]> JC_fieldVals = new ArrayList<Object[]>(JC_FIELDNAMES.size());
      Molecule[] hit_mols = jchemdb_utils.SubstructureSearch(chand,searcher,qtxt,nmax,JC_FIELDNAMES,JC_fieldVals);
      for (int i_hit=0;i_hit<hit_mols.length; ++i_hit)
      {
        ids.add((Integer)(JC_fieldVals.get(i_hit)[0]));
        //System.err.println(("DEBUG: "+i_hit+". "+JC_FIELDNAMES.get(0)+":"+JC_fieldVals.get(i_hit)[0])+("; "+JC_FIELDNAMES.get(1)+":"+JC_fieldVals.get(i_hit)[1])+("; "+JC_FIELDNAMES.get(2)+":"+JC_fieldVals.get(i_hit)[2]));
      }
      if (hit_mols.length>0)
      {
        rset = SearchCompoundsByID(dbcon,ids);
        ResultSet2Compounds(dbcon,rset,cpds,100.0F,log);
        if (rset!=null) rset.getStatement().close();
      }
      //cpds.setType("substruct"); //redundant?
    }
    else if (qtype.startsWith("fullstr")) //"fullstruct"
    {
      //System.err.println("DEBUG: NOT YET IMPLEMENTED: \""+dbquery.toString()+"\"");
      ArrayList<Object[]> JC_fieldVals = new ArrayList<Object[]>(JC_FIELDNAMES.size());
      Molecule[] hit_mols = jchemdb_utils.FullstructureSearch(chand,searcher,qtxt,nmax,JC_FIELDNAMES,JC_fieldVals);
      for (int i_hit=0;i_hit<hit_mols.length; ++i_hit)
      {
        ids.add((Integer)(JC_fieldVals.get(i_hit)[0]));
        //System.err.println(("DEBUG: "+i_hit+". "+JC_FIELDNAMES.get(0)+":"+JC_fieldVals.get(i_hit)[0])+("; "+JC_FIELDNAMES.get(1)+":"+JC_fieldVals.get(i_hit)[1])+("; "+JC_FIELDNAMES.get(2)+":"+JC_fieldVals.get(i_hit)[2]));
      }
      if (hit_mols.length>0)
      {
        rset = SearchCompoundsByID(dbcon,ids);
        ResultSet2Compounds(dbcon,rset,cpds,100.0F,log);
        if (rset!=null) rset.getStatement().close();
      }
    }
    else if (qtype.startsWith("simstr")) //"simstruct"
    {
      ArrayList<Object[]> JC_fieldVals = new ArrayList<Object[]>(JC_FIELDNAMES.size());
      Molecule[] hit_mols = jchemdb_utils.SimilaritySearch(chand,searcher,qtxt,1.0f-0.7f,nmax,JC_FIELDNAMES,JC_fieldVals);
      for (int i_hit=0;i_hit<hit_mols.length; ++i_hit)
      {
        ids.add((Integer)(JC_fieldVals.get(i_hit)[0]));
        //ID,CD_ID,NAME,Similarity
        //System.err.println(("DEBUG: "+i_hit+". "+JC_FIELDNAMES.get(0)+":"+JC_fieldVals.get(i_hit)[0])+("; "+JC_FIELDNAMES.get(1)+":"+JC_fieldVals.get(i_hit)[1])+("; "+JC_FIELDNAMES.get(2)+":"+JC_fieldVals.get(i_hit)[2])+" ("+(1.0F-searcher.getDissimilarity(i_hit))+")");
      }
      if (hit_mols.length>0)
      {
        rset = SearchCompoundsByID(dbcon,ids);
        ResultSet2Compounds(dbcon,rset,cpds,20.0F,log);
        if (rset!=null) rset.getStatement().close();
        for (int i_hit=0;i_hit<hit_mols.length; ++i_hit)
        {
          DrugDBCompound cpd = cpds.getHit(i_hit+1);
          if (cpd==null)
          {
            System.err.println("DEBUG: cpd==null");
            break;
          }
          cpd.setSimilarity(1.0F-searcher.getDissimilarity(i_hit));
        }
        cpds.setType("simstruct"); //redundant?
      }
    }
    else
    {
      System.err.println("ERROR: invalid query: \""+dbquery.toString()+"\"");
    }
    if (cpds.size()==0) return null;
    return cpds;
  }

  /////////////////////////////////////////////////////////////////////////////
  /**   Query Db and return one cpd.
  */
  public static DrugDBCompound GetCompound(Connection dbcon,DrugDBQuery dbquery,StringBuilder log)
      throws SQLException
  {
    String qtxt=dbquery.getText();
    String qtype=dbquery.getType();
    Integer cpd_id=Integer.parseInt(qtxt);
    if (!qtype.equalsIgnoreCase("cid"))
    {
      System.err.println("ERROR: invalid query: \""+dbquery.toString()+"\"");
      return null;
    }
    DrugDBCompound cpd = new DrugDBCompound(cpd_id);
    ResultSet rset = GetCompoundByID(dbcon,cpd_id);
    ResultSet2Compound(dbcon,rset,cpd,log);
    if (rset!=null) rset.getStatement().close();
    return cpd;
  }

  /////////////////////////////////////////////////////////////////////////////
  /**   Query Db and return one product.
  */
  public static DrugDBProduct GetProduct(Connection dbcon,DrugDBQuery dbquery,StringBuilder log)
      throws SQLException
  {
    String qtxt=dbquery.getText();
    String qtype=dbquery.getType();
    Integer product_id=Integer.parseInt(qtxt);
    if (!qtype.equalsIgnoreCase("pid"))
    {
      System.err.println("ERROR: invalid query: \""+dbquery.toString()+"\"");
      return null;
    }
    DrugDBProduct product = new DrugDBProduct(product_id);
    ResultSet rset = GetProductByID(dbcon,product_id);
    ResultSet2Product(dbcon,rset,product,log);
    if (rset!=null) rset.getStatement().close();
    return product;
  }

  /////////////////////////////////////////////////////////////////////////////
  /**	Append results to product list, for compound page.
  */
  public static void ResultSet2Products(Connection dbcon,ResultSet rset,ProductList products,int matchpoints,StringBuilder log)
      throws SQLException
  {
    if (rset==null) return;
    java.util.Date t_0 = new java.util.Date();
    while (rset.next())
    {
      Integer id=rset.getInt("ID");
      if (products.containsKey(id)) continue; //no duplicates
      DrugDBProduct product = new DrugDBProduct(id);

      product.setNdc(rset.getString("NDC_PRODUCT_CODE"));
      product.setForm(rset.getString("FORM"));
      product.setRoute(rset.getString("ROUTE"));
      product.setProductname(rset.getString("PRODUCT_NAME"));
      product.setGenericname(rset.getString("GENERIC_NAME"));
      product.setStatus(rset.getString("MARKETING_STATUS"));
      product.setIngredientCount(rset.getInt("ACTIVE_INGREDIENT_COUNT"));

      ResultSet rset2 = GetProductIngredients(dbcon,id);
      while (rset2.next())
      {
        Integer iid=rset2.getInt("ID");
        if (!product.hasIngredient(iid))
        {
          DrugDBIngredient ingr = new DrugDBIngredient(iid);
          ingr.setActivemoietyUnii(rset2.getString("ACTIVE_MOIETY_UNII"));
          ingr.setActivemoietyName(rset2.getString("ACTIVE_MOIETY_NAME"));
          ingr.setSubstanceUnii(rset2.getString("SUBSTANCE_UNII"));
          ingr.setSubstanceName(rset2.getString("SUBSTANCE_NAME"));
          ingr.setUnit(rset2.getString("UNIT"));
          ingr.setQuantity(rset2.getString("QUANTITY"));
          ingr.setQuantityUnit(rset2.getString("QUANTITY_DENOM_UNIT"));
          ingr.setQuantityValue(rset2.getString("QUANTITY_DENOM_VALUE"));
          product.addIngredient(ingr);
        }
        DrugDBIngredient ingr = product.getIngredient(iid);

        if (ingr.getCompound()==null)
        {
          Integer struct_id=rset2.getInt("STRUCT_ID");
          DrugDBCompound cpd = new DrugDBCompound(struct_id);
          ingr.setCompound(cpd);

          cpd.setMolbytes(rset2.getBytes("CD_STRUCTURE")); //Also sets smiles, MF, MWT, if possible.
          if (cpd.getSmiles()==null) cpd.setSmiles(rset.getString("CD_SMILES"));
          if (cpd.getMwt()==null) cpd.setMwt(rset.getDouble("CD_MOLWEIGHT"));

          cpd.setCAS(rset2.getString("CAS_REG_NO"));
        }
      }
      rset2.getStatement().close();
      products.put(id,product);
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  /**	Results to product, for product page.
  */
  public static void ResultSet2Product(Connection dbcon,ResultSet rset,DrugDBProduct product,StringBuilder log)
      throws SQLException
  {
    if (rset==null) return;
    if (!rset.next()) return;
    java.util.Date t_0 = new java.util.Date();

    Integer id=rset.getInt("ID");
    if (!product.getID().equals(id))
    {
      log.append("ERROR: product ID mismatch ("+product.getID()+"!="+id+")\n");
      return;
    }

    product.setNdc(rset.getString("NDC_PRODUCT_CODE"));
    product.setForm(rset.getString("FORM"));
    product.setRoute(rset.getString("ROUTE"));
    product.setProductname(rset.getString("PRODUCT_NAME"));
    product.setGenericname(rset.getString("GENERIC_NAME"));
    product.setStatus(rset.getString("MARKETING_STATUS"));
    product.setIngredientCount(rset.getInt("ACTIVE_INGREDIENT_COUNT"));

    ResultSet rset2 = GetProductIngredients(dbcon,id);
    while (rset2.next())
    {
      Integer iid=rset2.getInt("ID");
      if (!product.hasIngredient(iid))
      {
        DrugDBIngredient ingr = new DrugDBIngredient(iid);
        ingr.setActivemoietyUnii(rset2.getString("ACTIVE_MOIETY_UNII"));
        ingr.setActivemoietyName(rset2.getString("ACTIVE_MOIETY_NAME"));
        ingr.setSubstanceUnii(rset2.getString("SUBSTANCE_UNII"));
        ingr.setSubstanceName(rset2.getString("SUBSTANCE_NAME"));
        ingr.setUnit(rset2.getString("UNIT"));
        ingr.setQuantity(rset2.getString("QUANTITY"));
        ingr.setQuantityUnit(rset2.getString("QUANTITY_DENOM_UNIT"));
        ingr.setQuantityValue(rset2.getString("QUANTITY_DENOM_VALUE"));
        product.addIngredient(ingr);
      }
      DrugDBIngredient ingr = product.getIngredient(iid);

      if (ingr.getCompound()==null)
      {
        Integer struct_id=rset2.getInt("STRUCT_ID");
        DrugDBCompound cpd = GetCompound(dbcon,new DrugDBQuery(String.format("%d[cid]",struct_id)),log);
        ingr.setCompound(cpd);
      }
    }
    rset2.getStatement().close();
  }

  /////////////////////////////////////////////////////////////////////////////
  /**	Append results to cpd list.  Also get associated targets.
	Only retreive data needed for search results view.
  */
  public static void ResultSet2Compounds(Connection dbcon,ResultSet rset,CompoundList cpds,float matchpoints,
	StringBuilder log)
      throws SQLException
  {
    if (rset==null) return;
    java.util.Date t_0 = new java.util.Date();
    while (rset.next())
    {
      Integer id=rset.getInt("ID"); //structure ID
      if (!cpds.containsKey(id)) //If not present, add new cpd.
      {
        cpds.put(id,new DrugDBCompound(id));
        DrugDBCompound cpd = cpds.get(id);
        cpd.setMolbytes(rset.getBytes("CD_STRUCTURE")); //Also sets smiles, MF, MWT, if possible.
        if (cpd.getSmiles()==null) cpd.setSmiles(rset.getString("CD_SMILES"));
        if (cpd.getMwt()==null) cpd.setMwt(rset.getDouble("CD_MOLWEIGHT"));
        cpd.setMatchpoints(matchpoints);

        //Products:
        ResultSet rset2 = GetCompoundProducts(dbcon,id);
        ProductList products = new ProductList();
        ResultSet2Products(dbcon,rset2,products,0,log);
        rset2.getStatement().close();
        for (DrugDBProduct prd: products.getAllSortedByRelevance())
          cpd.addProduct(prd);

        //Activities+Targets:
        //rset2 = GetCompoundActivities(dbcon,id);
        //ResultSet2CompoundActivities(rset2,cpd);
        //rset2.getStatement().close();
      }
 
      DrugDBCompound cpd = cpds.get(id);

      //Additional data for each row:
      if (rset.getString("SYNONYM")!=null)
        cpd.addName(rset.getString("SYNONYM"));

      if (rset.getString("L1_CODE")!=null)
      {
        ATC atc = new ATC();
        for (int lev=1;lev<=4;++lev)
        {
          atc.setCode(lev,rset.getString("L"+lev+"_CODE"));
          atc.setName(lev,rset.getString("L"+lev+"_NAME"));
        }
        cpd.addATC(atc);
      }
    }
  }
  /////////////////////////////////////////////////////////////////////////////
  /**	Results to cpd.  Get all data needed for compound view.
  */
  public static void ResultSet2Compound(Connection dbcon,ResultSet rset,DrugDBCompound cpd,StringBuilder log)
      throws SQLException
  {
    if (rset==null) return;
    if (!rset.next()) return;
    java.util.Date t_0 = new java.util.Date();

    Integer id=rset.getInt("ID"); //structure ID
    if (!cpd.getID().equals(id))
    {
      log.append("ERROR: cpd ID mismatch ("+cpd.getID()+"!="+id+")\n");
      return;
    }

    cpd.setMolbytes(rset.getBytes("CD_STRUCTURE")); //Also sets smiles, MF, MWT, if possible.
    if (cpd.getSmiles()==null) cpd.setSmiles(rset.getString("CD_SMILES"));
    if (cpd.getMwt()==null) cpd.setMwt(rset.getDouble("CD_MOLWEIGHT"));

    cpd.setCAS(rset.getString("CAS_REG_NO"));
    cpd.setApprovalDate(rset.getDate("APPROVAL_DATE"));
    cpd.setApprovalType(rset.getString("APPROVAL_TYPE"));
    cpd.setApprovalApplicant(rset.getString("APPROVAL_APPLICANT"));

    //ATCs:
    if (rset.getString("L1_CODE")!=null)
    {
      ATC atc = new ATC();
      for (int lev=1;lev<=4;++lev)
      {
        atc.setCode(lev,rset.getString("L"+lev+"_CODE"));
        atc.setName(lev,rset.getString("L"+lev+"_NAME"));
      }
      cpd.addATC(atc);
    }

    //IDs:
    ResultSet rset2 = GetCompoundUniis(dbcon,id);
    if (rset2.next())
      cpd.setExtID("ACTIVE_MOIETY_UNII",rset2.getString("ACTIVE_MOIETY_UNII"));
    rset2.getStatement().close();
    rset2 = GetCompoundIDs(dbcon,id);
    while (rset2.next())
      cpd.setExtID(rset2.getString("ID_TYPE"),rset2.getString("ID_VAL"));
    rset2.getStatement().close();

    //Activities+Targets:
    rset2 = GetCompoundActivities(dbcon,id);
    ResultSet2CompoundActivities(rset2,cpd);
    rset2.getStatement().close();

    //Products:
    rset2 = GetCompoundProducts(dbcon,id);
    ProductList products = new ProductList();
    ResultSet2Products(dbcon,rset2,products,0,log);
    rset2.getStatement().close();
    for (DrugDBProduct prd: products.getAllSortedByRelevance())
      cpd.addProduct(prd);

    //Names:
    rset2 = GetCompoundSynonyms(dbcon,id);
    while (rset2.next())
      if (rset2.getString("SYNONYM")!=null)
        cpd.addName(rset2.getString("SYNONYM"));
    rset2.getStatement().close();
  }

  /////////////////////////////////////////////////////////////////////////////
  public static void ResultSet2CompoundActivities(ResultSet rset,DrugDBCompound cpd)
      throws SQLException
  {
    if (rset==null) return;
    while (rset.next())
    {
      Integer act_id = rset.getInt("ACT_ID");
      if (act_id==null) continue;
      if (!cpd.hasActivity(act_id))
        cpd.addActivity(new DrugDBActivity(act_id));

      DrugDBActivity act = cpd.getActivity(act_id);
      act.setCompound(cpd);
      act.setMoa(rset.getInt("MOA"));
      act.setMoaType(rset.getString("MOA_TYPE"));
      act.setMoaSource(rset.getString("MOA_SOURCE"));
      act.setMoaRefID(rset.getInt("MOA_REF_ID"));
      act.setRelation(rset.getString("RELATION"));
      act.setType(rset.getString("ACT_TYPE"));
      act.setUnit(rset.getString("ACT_UNIT"));
      act.setComment(rset.getString("ACT_COMMENT"));
      act.setSource(rset.getString("ACT_SOURCE"));
      act.setValue(rset.getDouble("ACT_VALUE"));

      act.setRefID(rset.getInt("REF_ID"));
      act.setRefPMID(rset.getString("REF_PMID"));
      act.setRefTitle(rset.getString("REF_TITLE"));
      act.setRefJournal(rset.getString("REF_JOURNAL"));
      act.setRefAuthors(rset.getString("REF_AUTHORS"));
      act.setRefDOI(rset.getString("REF_DOI"));
      act.setRefURL(rset.getString("REF_URL"));
      act.setRefYear(rset.getInt("REF_YEAR"));

      act.setMoaRefID(rset.getInt("MOA_REF_ID"));
      act.setMoaRefPMID(rset.getString("MOA_REF_PMID"));
      act.setMoaRefTitle(rset.getString("MOA_REF_TITLE"));
      act.setMoaRefJournal(rset.getString("MOA_REF_JOURNAL"));
      act.setMoaRefAuthors(rset.getString("MOA_REF_AUTHORS"));
      act.setMoaRefDOI(rset.getString("MOA_REF_DOI"));
      act.setMoaRefURL(rset.getString("MOA_REF_URL"));
      act.setMoaRefYear(rset.getInt("MOA_REF_YEAR"));

      Integer tid = rset.getInt("TID");
      if (tid==null) continue;
      if (!cpd.hasTarget(tid))
        cpd.addTarget(new DrugDBTarget(tid));
      DrugDBTarget tgt = cpd.getTarget(tid);
      act.setTarget(tgt);

      tgt.setName(rset.getString("TARGET_NAME"));
      Integer tcid = rset.getInt("TCID");
      if (tcid!=null)
      {
        if (!tgt.hasComponent(tcid))
          tgt.addComponent(new DrugDBTargetComponent(tcid));
        DrugDBTargetComponent tc = tgt.getComponent(tcid);
        if (rset.getString("PROTEIN_NAME")!=null) tc.setName(rset.getString("PROTEIN_NAME"));
        if (rset.getString("PROTEIN_ACCESSION")!=null) tc.setAccession(rset.getString("PROTEIN_ACCESSION"));
        if (rset.getString("ORGANISM")!=null) tc.setOrganism(rset.getString("ORGANISM"));
        if (rset.getString("SWISSPROT")!=null) tc.setSwissprot(rset.getString("SWISSPROT"));
        if (rset.getString("GENE_SYMBOL")!=null) tc.setGenesymbol(rset.getString("GENE_SYMBOL"));
        tc.setGeneID(rset.getInt("GENEID"));
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  private static String ResultProductText(DrugDBProduct product)
  {
    String txt=("Product [ID="+product.getID()+"]\n");
    txt+="Name:\t"+product.getProductname()+"\n";
    txt+="Generic name:\t"+product.getGenericname()+"\n";
    txt+="NDC:\t"+product.getNdc()+"\n";
    txt+="Route:\t"+product.getRoute()+"\n";
    txt+="Form:\t"+product.getForm()+"\n";
    txt+="Status:\t"+product.getStatus()+"\n";
    txt+="MixSmiles:\t"+product.getMixtureSmiles()+"\n";
    txt+="Ingredients ("+product.ingredientCount()+"):\t\n";
    int i=0;
    for (DrugDBIngredient ingr: product.getIngredients())
    {
      ++i;
      DrugDBCompound cpd = ingr.getCompound();
      txt+="\t"+i+".\n";
      txt+="\tID: "+ingr.getID()+"\n";
      txt+="\tActive moiety name: "+ingr.getActivemoietyName()+"\n";
      txt+="\tActive moiety Unii: "+ingr.getActivemoietyUnii()+"\n";
      txt+="\tSubstance Name: "+ingr.getSubstanceName()+"\n";
      txt+="\tSubstance Unii: "+ingr.getSubstanceUnii()+"\n";
      txt+="\tQuantity: "+ingr.getQuantity()+"\n";
      txt+="\tUnit: "+ingr.getUnit()+"\n";
      txt+="\tQuantityValue: "+ingr.getQuantityValue()+"\n";
      txt+="\tQuantityUnit: "+ingr.getQuantityUnit()+"\n";
      txt+="\tCompound Smiles: "+cpd.getSmiles()+"\n";
      txt+="\tCompound CAS: "+cpd.getCAS()+"\n";
    }
    return txt;
  }

  /////////////////////////////////////////////////////////////////////////////
  private static String ResultCompoundText(DrugDBCompound cpd,boolean full)
  {
    String txt=("Compound [ID="+cpd.getID()+"]\n");
    txt+=String.format("MF:\t%s\n",cpd.getMolformula());
    txt+=String.format("MWT:\t%.2f\n",cpd.getMwt());
    txt+="Smiles:\t"+cpd.getSmiles()+"\n";
    txt+="Source format:\t"+cpd.getMolformat()+" ("+((cpd.getMolformat()!=null)?MFileFormatUtil.getFormat(cpd.getMolformat()).getDescription():"")+")\n";

    if (full)
    {
      txt+="Approved: "+cpd.getApprovalDate()+" ("+cpd.getApprovalType()+((cpd.getApprovalApplicant()!=null)?(", "+cpd.getApprovalApplicant()):"")+")\n";
      txt+="IDs:\n";
      if (cpd.getCAS()!=null)
        txt+=("\tCAS: "+cpd.getCAS()+"\n");
      for (String idtype: cpd.getExtIDTypes())
        txt+=("\t"+idtype+": \""+cpd.getExtID(idtype)+"\"\n");
    }

    txt+="Names ("+cpd.nameCount()+"):\n";
    int i_name=0;
    for (Name name: cpd.getNames())
    {
      if (++i_name>10) break;
      txt+=("\t"+i_name+". \""+name.toString()+"\"\n");
    }
    txt+="ATCs:\n";
    int i_atc=0;
    for (ATC atc: cpd.getAtcs())
    {
      txt+=("\t"+(++i_atc)+".\n");
      for (int lev=1;lev<=4;++lev)
        txt+=("\t\t"+atc.getCode(lev)+"\t\""+atc.getName(lev)+"\"\n");
    }
    txt+="Targets ("+cpd.targetCount()+"):\n";
    int i_tgt=0;
    for (DrugDBActivity act: cpd.getActivityList().getAllSortedByRelevance())
    {
      DrugDBTarget tgt = act.getTarget();
      txt+=("\t"+(++i_tgt)+". ["+tgt.getID()+"]");
      txt+=((act.getMoa().equals(1) && act.getMoaType()!=null)?"(MOA:"+act.getMoaType()+") ":"");
      txt+=("\""+tgt.getName()+"\"");
      txt+=(" (components: "+tgt.componentCount()+"; ");
      int i_tgtc=0;
      for (DrugDBTargetComponent tgtc: tgt.getComponents())
        txt+=(((i_tgtc++>0)?",":"")+tgtc.getGenesymbol()+" - "+tgtc.getOrganism());
      txt+=(")\n");
    }
    txt+="Products ("+cpd.productCount()+") names:\n";
    int i_pname=0;
    for (Name pname: cpd.getProductnames())
    {
      if (++i_pname>10) break;
      txt+=("\t"+i_pname+". \""+pname.toString()+"\"\n");
    }
    return txt;
  }

  /////////////////////////////////////////////////////////////////////////////
  private static String ResultCompoundsText(CompoundList cpds)
  {
    List<DrugDBCompound> cpds_sorted;
    if (cpds.getType().equals("simstruct"))
      cpds_sorted = cpds.getAllSortedBySimilarity();
    else if (cpds.getType().equals("substruct"))
      cpds_sorted = cpds.getAllSortedByMwt();
    else
      cpds_sorted = cpds.getAllSortedByRelevance();
    String txt="";
    for (DrugDBCompound cpd: cpds_sorted)
    {
      Integer cpd_id = cpd.getID();
      txt+=("-----------------------------------------------------------------------------\n");
      if (cpds.getType().equals("simstruct"))
        txt+=("similarity: "+cpd.getSimilarity()+"\n");
      txt+=ResultCompoundText(cpd,false);
    }
    txt+=("-----------------------------------------------------------------------------\n");
    txt+=("Compound count: "+cpds.size()+"\n");
    return txt;
  }

  /////////////////////////////////////////////////////////////////////////////
  private static String ResultCompoundActivitiesText(DrugDBCompound cpd)
  {
    String txt="";
    txt+=("Compound ID: "+cpd.getID()+"\n");
    int i_tgt=0;
    for (DrugDBActivity act: cpd.getActivityList().getAllSortedByRelevance())
    {
      DrugDBTarget tgt = act.getTarget();
      txt+=(""+(++i_tgt)+".\tTID: "+tgt.getID()+"\n");
      txt+=("\tName: "+tgt.getName()+"\n");
      txt+=("\tACT_UNIT: "+act.getUnit()+"\n"); //15549 of 15551 are NULL
      txt+=("\tACT_TYPE: "+act.getType()+"\n");
      txt+=("\tACT_VALUE: "+(String.valueOf(act.getRelation()).matches("[><]")?act.getRelation():"")+String.format("%.3f",act.getValue())+"\n");
      txt+=("\tACT_SOURCE: "+act.getSource()+"\n");
      if (act.getComment()!=null)
        txt+=("\tACT_COMMENT: "+act.getComment()+"\n");
      txt+=("\tREF_ID: "+act.getRefID()+"\n");
      txt+=("\tREF_PMID: "+act.getRefPMID()+"\n");
      txt+=("\tREF_TITLE: "+act.getRefTitle()+"\n");
      txt+=("\tREF_JOURNAL: "+act.getRefJournal()+"\n");
      txt+=("\tREF_AUTHORS: "+act.getRefAuthors()+"\n");
      txt+=("\tREF_YEAR: "+act.getRefYear()+"\n");
      txt+=("\tREF_DOI: "+act.getRefDOI()+"\n");
      txt+=("\tREF_URL: "+act.getRefURL()+"\n");
      if (act.getMoa().equals(1) && act.getMoaType()!=null)
      {
        //txt+=("\tMOA: "+act.getMoa()+"\n");
        txt+=("\tMOA_TYPE: "+act.getMoaType()+"\n");
        txt+=("\tMOA_SOURCE: "+act.getMoaSource()+"\n");
        txt+=("\tMOA_REF_ID: "+act.getMoaRefID()+"\n");
        txt+=("\tMOA_REF_PMID: "+act.getMoaRefPMID()+"\n");
        txt+=("\tMOA_REF_TITLE: "+act.getMoaRefTitle()+"\n");
        txt+=("\tMOA_REF_JOURNAL: "+act.getMoaRefJournal()+"\n");
        txt+=("\tMOA_REF_AUTHORS: "+act.getMoaRefAuthors()+"\n");
        txt+=("\tMOA_REF_YEAR: "+act.getMoaRefYear()+"\n");
        txt+=("\tMOA_REF_DOI: "+act.getMoaRefDOI()+"\n");
        txt+=("\tMOA_REF_URL: "+act.getMoaRefURL()+"\n");
      }
      txt+=("\n");
    }
    return txt;
  }

  /////////////////////////////////////////////////////////////////////////////
  private static String ResultProductsText(ProductList products)
  {
    String txt="";
    int i=0;
    for (DrugDBProduct product: products.getAllSortedByRelevance())
    {
      ++i;
      txt+="--- "+i+".\n";
      Integer product_id = product.getID();
      txt+=ResultProductText(product);
    }
    txt+=("Product count: "+products.size()+"\n");
    return txt;
  }

  /////////////////////////////////////////////////////////////////////////////
  private static String dbname="db";
  private static String dbschema="APP";
  private static String dbdir="/home/data/drugdb/.config/localdb";
  private static String ofile="";
  private static String dbtable=null;
  private static String stable="STRUCTURES";
  private static String ptable="JCHEMPROPERTIES";
  private static int verbose=0;
  private static Boolean describe=false;
  private static Boolean get_cpd=false;
  private static Boolean get_cpd_activity=false;
  private static Boolean search_cpds=false;
  private static Boolean get_product=false;
  private static Boolean search_products=false;
  private static Boolean version=false;
  private static Boolean update_dbversion=false;
  private static Integer id=null;
  private static String query=null;
  private static String extidtype=null;

  /////////////////////////////////////////////////////////////////////////////
  private static void Help(String msg)
  {
    System.out.println(msg+"\n"
      +"drugdb_utils - drugdb utilities (JChemBase)\n"
      +"usage: drugdb_utils [options]\n"
      +"\n"
      +"operation:\n"
      +"    -describe .............. describe (schema or table)\n"
      +"    -version ............... show versions\n"
      +"  requires ID:\n"
      +"    -get_cpd ............... get cpd\n"
      +"    -get_cpd_activity ...... get cpd activity report\n"
      +"    -get_product ........... get product\n"
      +"  requires QUERY:\n"
      +"    -search_cpds ........... search compound names\n"
      +"    -search_products ....... search product names\n"
      +"\n"
      +"options:\n"
      +"    -query QUERY ........... see syntax\n"
      +"    -id ID ................. internal ID (int)\n"
      +"    -extidtype IDTYPE ...... external ID type\n"
      +"    -dbname DBNAME ......... db name ["+dbname+"]\n"
      +"    -dbdir DBDIR ........... directory of db ["+dbdir+"]\n"
      +"    -dbschema DBSCHEMA ..... db schema ["+dbschema+"]\n"
      +"    -dbtable TNAME ......... db table\n"
      +"    -ptable TNAME .......... JChem properties table ["+ptable+"]\n"
      +"    -stable TNAME .......... JChem structures table ["+stable+"]\n"
      +"    -o OFILE ............... output file\n"
      +"    -v[v] .................. verbose [very]\n"
      +"    -h ..................... this help\n"
      +"\n"
      +"Query syntax:\n"
      +"  STR[subtxt] ......................... search cpds, substring name match [default]\n"
      +"  STR[fulltxt] ........................ search cpds, full name match\n"
      +"  SMILES[substruct] ................... search cpds, Smiles as sub-structure\n"
      +"  SMILES[fullstruct] .................. search cpds, Smiles as full-structure\n"
      +"  3386[cidext] ........................ search cpds, external ID, any type\n"
      +"  CODE[atc1] .......................... search cpds, by ATC Level 1 code\n"
      +"  CODE[atc2] .......................... search cpds, by ATC Level 2 code\n"
      +"  CODE[atc3] .......................... search cpds, by ATC Level 3 code\n"
      +"  CODE[atc4] .......................... search cpds, by ATC Level 4 code\n"
      +"  CID[cid] ............................ get cpd, by cpd ID (DrugDB unique ID)\n"
      +"  TID[tid] ............................ get target, by target ID (DrugDB unique ID)\n"
      +"  PID[pid] ............................ get product, by product ID (DrugDB unique ID)\n"
      +"\n"
      +"External ID types:\n"
      +"  PUBCHEM_CID, ChEMBL_ID, MESH, ACTIVE_MOIETY_UNII, etc.\n"
      +"\n"
      +"(For generic utilities use derby_utils.sh.)\n"
      +"(For JChemDB utilities use jchemdb_utils.sh.)\n"
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
      +"RxCUI: 58827\n"
	);
    System.exit(1);
  }
  /////////////////////////////////////////////////////////////////////////////
  private static void ParseCommand(String args[])
  {
    if (args.length==0) Help("");
    for (int i=0;i<args.length;++i)
    {
      if (args[i].equals("-dbname")) dbname=args[++i];
      else if (args[i].equals("-dbdir")) dbdir=args[++i];
      else if (args[i].equals("-dbschema")) dbschema=args[++i];
      else if (args[i].equals("-dbtable")) dbtable=args[++i];
      else if (args[i].equals("-ptable")) ptable=args[++i];
      else if (args[i].equals("-stable")) stable=args[++i];
      else if (args[i].equals("-o")) ofile=args[++i];
      else if (args[i].equals("-describe")) describe=true;
      else if (args[i].equals("-get_cpd")) get_cpd=true;
      else if (args[i].equals("-get_cpd_activity")) get_cpd_activity=true;
      else if (args[i].equals("-get_product")) get_product=true;
      else if (args[i].equals("-search_cpds")) search_cpds=true;
      else if (args[i].equals("-search_products")) search_products=true;
      else if (args[i].equals("-version")) version=true;
      else if (args[i].equals("-update_dbversion")) update_dbversion=true;
      else if (args[i].equals("-id")) id=Integer.parseInt(args[++i]);
      else if (args[i].equals("-query")) query=args[++i];
      else if (args[i].equals("-extidtype")) extidtype=args[++i];
      else if (args[i].equals("-v")) verbose=1;
      else if (args[i].equals("-vv")) verbose=2;
      else if (args[i].equals("-h")) Help("");
      else Help("Unknown option: "+args[i]);
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  public static void main(String[] args)
	throws IOException,SQLException
  {
    ParseCommand(args);
    ConnectionHandler chand = null;
    Connection dbcon = null;

    java.util.Date t_0 = new java.util.Date();

    try {
      chand = jchemdb_utils.GetConnectionHandler(dbdir,dbname,ptable);
      dbcon = chand.getConnection();
    }
    catch (Exception e) { Help("Connection failed: "+e.getMessage()); }

    System.err.println("===");

    if (chand==null)
      Help("Connection failed: "+dbdir+"/"+dbname);
    else
      System.err.println("Connection ok: "+dbdir+"/"+dbname);

    JChemSearch jc_searcher = jchemdb_utils.GetJChemSearch(chand,dbschema,stable);

    DrugDBQuery dbquery = (query==null)? null : new DrugDBQuery(query.trim());
    if (verbose>0 && dbquery!=null) DescribeQuery(dbquery);

    StringBuilder log = new StringBuilder();

    if (describe)
    {
      DescribeDB(chand,dbtable);
    }
    else if (version)
    {
      
      String db_jcver=JChemVersion(dbcon);
      System.err.println("JChem version: "+VersionInfo.getVersion()+" ; DB JChem version: "+db_jcver+" ("+ (VersionInfo.getVersion().equals(db_jcver)?"EQUAL":"NOT EQUAL")+")");
    }
    else if (update_dbversion)
    {
      String db_jcver=JChemVersion(dbcon);
      System.err.println("JChem version: "+VersionInfo.getVersion()+" ; DB JChem version: "+db_jcver+" ("+ (VersionInfo.getVersion().equals(db_jcver)?"EQUAL":"NOT EQUAL")+")");
      if (VersionInfo.getVersion().equals(db_jcver))
      {
        System.err.println("Versions EQUAL; update not needed.");
      }
      else
      {
        boolean ok = SetJChemVersion(dbcon,VersionInfo.getVersion());
        db_jcver=JChemVersion(dbcon);
        System.err.println("New DB JChem version: "+db_jcver+" ("+ (VersionInfo.getVersion().equals(db_jcver)?"EQUAL":"NOT EQUAL")+") ; ok = "+ok);
      }
    }
    else if (get_cpd)
    {
      if (id==null) Help("ERROR: -get requires -id.");
      DrugDBCompound cpd = GetCompound(dbcon,new DrugDBQuery(String.format("%d[cid]",id)),log);
      if (cpd!=null)
        System.out.println(ResultCompoundText(cpd,true));
      else System.out.println("No compound found.");
    }
    else if (search_cpds)
    {
      if (dbquery==null) Help("ERROR: -search requires -query.");
      if (dbquery.getText().isEmpty()) { Help("ERROR: empty query string."); }
      else if (dbquery.toString().length()<3) { Help("ERROR: query must be 3+ characters."); }
      if (extidtype!=null) dbquery.setExtIdType(extidtype);
      try {
        CompoundList cpds = SearchCompounds(dbcon,chand,jc_searcher,dbquery,100,log);
        if (verbose>0) System.err.println(log.toString());
        if (cpds!=null) System.out.println(ResultCompoundsText(cpds));
        else System.out.println("No compounds found.");
      }
      catch (Exception e) { System.err.println(e.toString()); }
    }
    else if (get_cpd_activity)
    {
      if (id==null) Help("ERROR: -get requires -id.");
      DrugDBCompound cpd = GetCompound(dbcon,new DrugDBQuery(String.format("%d[cid]",id)),log);
      ResultSet rset = GetCompoundActivities(dbcon,cpd.getID());
      ResultSet2CompoundActivities(rset,cpd);
      System.out.println(ResultCompoundActivitiesText(cpd));
    }
    else if (get_product)
    {
      if (id==null) Help("ERROR: -get requires -id.");
      DrugDBProduct product = GetProduct(dbcon,new DrugDBQuery(String.format("%d[pid]",id)),log);
      if (product!=null)
        System.out.println(ResultProductText(product));
    }
    else if (search_products)
    {
      if (dbquery==null) Help("ERROR: -search requires -query.");
      if (dbquery.getText().isEmpty()) { Help("ERROR: empty query string."); }
      else if (dbquery.toString().length()<3) { Help("ERROR: query must be 3+ characters."); }
      ProductList products = SearchProducts(dbcon,chand,jc_searcher,dbquery,log);
      if (products!=null) System.out.println(ResultProductsText(products));
    }
    else
    {
      Help("ERROR: no operation specified.");
    }
    if (chand!=null) 
    {
      //chand.disconnect();
      chand.close();
    }
    if (verbose>0) System.err.println(log.toString());
    System.err.println("Elapsed time: "+time_utils.TimeDeltaStr(t_0,new java.util.Date()));
  }
}
