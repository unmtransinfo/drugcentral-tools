package edu.unm.health.biocomp.drugcentral;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.sql.*; //DriverManager,Driver,SQLException,Connection,Statement,ResultSet

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
import edu.unm.health.biocomp.text.*; //Name,NameList

/**	Utilities for DrugCentral queries and admin.

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
public class drugcentral_utils
{
  /////////////////////////////////////////////////////////////////////////////
  public static void DescribeQuery(DCQuery dbquery)
	throws SQLException
  {
    System.err.println("DBQuery (raw): "+dbquery.toString());
    System.err.println("DBQuery type: "+dbquery.getType());
    System.err.println("DBQuery text: "+dbquery.getText());
    System.err.println("DBQuery isValid: "+dbquery.isValid());
  }
  /////////////////////////////////////////////////////////////////////////////
  public static String DBDescribe(DBCon dbcon, PrintWriter fout_writer)
	throws SQLException
  {
    Version v = GetVersion(dbcon);
    String txt="";
    txt+=("Version: "+v.version+" ("+v.dtime+")\n");
    txt+=("Total compounds: "+CompoundCount(dbcon)+"\n");
    txt+=("Total synonyms: "+SynonymCount(dbcon)+"\n");
    txt+=("Total products: "+ProductCount(dbcon)+"\n");
    txt+=("Total ingredients: "+IngredientCount(dbcon)+"\n");
    txt+=("Total targets: "+TargetCount(dbcon)+"\n");
    txt+=("Total activities: "+ActivityCount(dbcon)+"\n");
    fout_writer.write(txt);
    return txt;
  }
  /////////////////////////////////////////////////////////////////////////////
  public static Version GetVersion(DBCon dbcon) throws SQLException
  {
    ResultSet rset = dbcon.executeSql("SELECT version,dtime FROM dbversion");
    if (!rset.next()) return new Version("?", "?");
    else return new Version(rset.getString(1), rset.getString(2));
  }
  public static String RDKitVersion(DBCon dbcon)
  {
    try {
      ResultSet rset = dbcon.executeSql("SELECT rdkit_version()");
      if (!rset.next()) return ("?");
      else return (rset.getString(1));
    } catch (SQLException e) { return ("~"); }
  }
  /////////////////////////////////////////////////////////////////////////////
  public static int ProductCount(DBCon dbcon) throws SQLException
  {
    ResultSet rset = dbcon.executeSql("SELECT COUNT(id) FROM public.product");
    return (rset.next()?rset.getInt(1):0);
  }
  public static int IngredientCount(DBCon dbcon) throws SQLException
  {
    ResultSet rset = dbcon.executeSql("SELECT COUNT(id) FROM public.active_ingredient");
    return (rset.next()?rset.getInt(1):0);
  }
  public static int CompoundCount(DBCon dbcon) throws SQLException
  {
    ResultSet rset = dbcon.executeSql("SELECT COUNT(id) FROM public.structures");
    return (rset.next()?rset.getInt(1):0);
  }
  public static int TargetCount(DBCon dbcon) throws SQLException
  {
    ResultSet rset = dbcon.executeSql("SELECT COUNT(id) FROM public.target_dictionary");
    return (rset.next()?rset.getInt(1):0);
  }
  public static int ActivityCount(DBCon dbcon) throws SQLException
  {
    ResultSet rset = dbcon.executeSql("SELECT COUNT(act_id) FROM public.act_table_full");
    return (rset.next()?rset.getInt(1):0);
  }
  public static int SynonymCount(DBCon dbcon) throws SQLException
  {
    ResultSet rset = dbcon.executeSql("SELECT COUNT(syn_id) FROM public.synonyms");
    return (rset.next()?rset.getInt(1):0);
  }
  public static int ReferenceCount(DBCon dbcon) throws SQLException
  {
    ResultSet rset = dbcon.executeSql("SELECT COUNT(id) FROM public.reference");
    return (rset.next()?rset.getInt(1):0);
  }
  /////////////////////////////////////////////////////////////////////////////
  /// COMPOUND queries (all return same fields):
  /////////////////////////////////////////////////////////////////////////////
  private static String sql_cpd=
"SELECT DISTINCT s.id, s.molfile, s.cd_molweight, s.cas_reg_no, s.name \"struct_name\" FROM public.structures AS s ";
  public static ResultSet SearchCompoundsByStructureName(DBCon dbcon,String qstr,Boolean full)
	throws SQLException
  {
    String sql=sql_cpd;
    if (full) sql+=(" WHERE UPPER(s.name) = '"+qstr.toUpperCase()+"'");
    else      sql+=(" WHERE UPPER(s.name) LIKE '%"+qstr.toUpperCase()+"%'");
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet SearchCompoundsByIngredientName(DBCon dbcon,String qstr,Boolean full)
	throws SQLException
  {
    String sql=sql_cpd;
    sql+=("JOIN public.active_ingredient AS ai ON s.id = ai.struct_id ");
    if (full) sql+=(" WHERE UPPER(ai.substance_name) = '"+qstr.toUpperCase()+"'");
    else       sql+=(" WHERE UPPER(ai.substance_name) LIKE '%"+qstr.toUpperCase()+"%'");
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet SearchCompoundsBySynonym(DBCon dbcon,String qstr,Boolean full)
	throws SQLException
  {
    String sql=sql_cpd;
    sql+=("JOIN public.synonyms AS syn ON s.id = syn.id ");
    if (full) sql+=(" WHERE UPPER(syn.name) = UPPER('"+qstr+"')");
    else      sql+=(" WHERE UPPER(syn.name) LIKE '%"+qstr.toUpperCase()+"%'");
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet SearchCompoundsByProductName(DBCon dbcon,String qstr,Boolean full)
	throws SQLException
  {
    String sql=sql_cpd;
    sql+=("JOIN public.active_ingredient AS ai ON s.id = ai.struct_id JOIN public.product AS p ON ai.ndc_product_code = p.ndc_product_code ");
    if (full) sql+=(" WHERE UPPER(p.product_name) = '"+qstr.toUpperCase()+"'");
    else      sql+=(" WHERE UPPER(p.product_name) LIKE '%"+qstr.toUpperCase()+"%'");
    sql+=(" AND p.active_ingredient_count = 1");
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet SearchCompoundsByUNII(DBCon dbcon,String unii)
	throws SQLException
  {
    String sql=sql_cpd;
    sql+=("JOIN public.active_ingredient AS ai ON s.id = ai.struct_id ");
    sql+=" WHERE ai.active_moiety_unii = '"+unii+"'";
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet SearchCompoundsByATC(DBCon dbcon,String code,Integer level)
	throws SQLException
  {
    String sql=sql_cpd;
    sql+=("JOIN public.struct2atc AS s2atc ON s.id = s2atc.struct_id JOIN public.atc AS atc ON atc.code = s2atc.atc_code ");
    sql+=" WHERE atc.l"+level+"_code = '"+code+"'";
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet SearchCompoundsByExtID(DBCon dbcon,String id,String idtype)
	throws SQLException
  {
    String sql=sql_cpd;
    sql+=("JOIN public.identifier AS id ON s.id = id.struct_id ");
    sql+=" WHERE id.identifier = '"+id+"'";
    if (idtype!=null)
      sql+=" AND UPPER(id.id_type) = UPPER('"+idtype+"')";
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet SearchCompoundsByID(DBCon dbcon,List<Integer> ids)
	throws SQLException
  {
    String sql=sql_cpd;
    sql+=" WHERE s.id IN ( ";
    for (int i=0;i<ids.size(); ++i)
      sql+=(((i==0)?"":",")+ids.get(i));
    sql+=(")");
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet GetCompoundByID(DBCon dbcon,Integer id)
	throws SQLException
  {
    String sql=sql_cpd;
    sql+=" WHERE s.id = "+id;
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }

  /**	Requires RDKit cartridge. */
  public static ResultSet SearchCompoundsByStructure(DBCon dbcon,String qtype,String qtxt)
	throws SQLException
  {
    String sql=sql_cpd;
    sql+="JOIN public.mols m ON s.id = m.id ";
    if (qtype.startsWith("substr")) //"substruct"
      sql+="WHERE m.mol @> '"+qtxt+"' ";
    else if (qtype.startsWith("fullstr")) //"fullstruct"
      sql+="WHERE m.mol @= '"+qtxt+"' ";
    else if (qtype.startsWith("simstr")) //"simstruct"
    {
      sql=sql.replaceFirst("SELECT DISTINCT ", "SELECT DISTINCT tanimoto_sml(rdkit_fp(mol_from_smiles('"+qtxt+"'::cstring)),m.fp) AS \"sim\", ");
      sql+="WHERE rdkit_fp(mol_from_smiles('"+qtxt+"'::cstring))%m.fp ";
    }
    else
      System.err.println("DEBUG: ERROR: bad qtype: "+qtype);
    //System.err.println("DEBUG: sql: "+sql);
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }

  public static ResultSet GetCompoundProductNames(DBCon dbcon,int id)
	throws SQLException
  {
    String sql="SELECT DISTINCT ai.active_moiety_name, ai.substance_name, p.id \"product_id\", p.generic_name, p.product_name, p.active_ingredient_count FROM public.active_ingredient AS ai JOIN public.product AS p ON ai.ndc_product_code = p.ndc_product_code WHERE ai.struct_id = "+id;
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet GetCompoundSynonyms(DBCon dbcon,int id)
	throws SQLException
  {
    String sql="SELECT DISTINCT syn.name \"synonym\" FROM public.synonyms AS syn WHERE syn.id = "+id;
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet GetCompoundATCs(DBCon dbcon,int id)
	throws SQLException
  {
    String sql="SELECT DISTINCT atc.code \"atc_code\", atc.chemical_substance, atc.l1_code, atc.l1_name, atc.l2_code, atc.l2_name, atc.l3_code, atc.l3_name, atc.l4_code, atc.l4_name FROM public.atc AS atc JOIN public.struct2atc AS s2atc ON atc.code = s2atc.atc_code WHERE s2atc.struct_id = "+id;
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet GetCompoundIDs(DBCon dbcon,int id)
	throws SQLException
  {
    String sql="SELECT DISTINCT identifier \"id_val\", id_type FROM public.identifier AS id WHERE id.struct_id = "+id;
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet GetCompoundUniis(DBCon dbcon,int id)
	throws SQLException
  {
    String sql="SELECT DISTINCT active_moiety_unii FROM public.active_ingredient AS ai WHERE ai.struct_id = "+id;
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet GetCompoundApprovals(DBCon dbcon,int id)
	throws SQLException
  {
    String sql="SELECT DISTINCT apv.approval \"approval_date\", apv.type \"approval_type\", apv.applicant \"approval_applicant\" FROM public.approval AS apv WHERE apv.struct_id = "+id;
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }

  /////////////////////////////////////////////////////////////////////////////
  /// PRODUCT queries (all return same fields):
  /////////////////////////////////////////////////////////////////////////////
  private static String sql_product="SELECT p.id, p.ndc_product_code, p.form, p.generic_name, p.product_name, p.route, p.marketing_status, p.active_ingredient_count, ai.active_moiety_name, ai.substance_name, s.cas_reg_no, s.molfile, s.cd_molweight, n.name \"synonym\" FROM public.product AS p JOIN public.active_ingredient AS ai ON p.ndc_product_code = ai.ndc_product_code JOIN public.structures AS s ON ai.struct_id = s.id JOIN public.synonyms AS n ON n.id = s.id";
  public static ResultSet SearchProductsByProductName(DBCon dbcon,String qstr,Boolean full)
	throws SQLException
  {
    String sql=sql_product;
    if (full) sql+=" WHERE UPPER(p.product_name) = '"+qstr.toUpperCase()+"'";
    else       sql+=" WHERE UPPER(p.product_name) LIKE '%"+qstr.toUpperCase()+"%'";
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet SearchProductsByIngredientName(DBCon dbcon,String qstr,Boolean full)
	throws SQLException
  {
    String sql=sql_product;
    if (full)  sql+=" WHERE UPPER(ai.substance_name) = '"+qstr.toUpperCase()+"'";
    else        sql+=" WHERE UPPER(ai.substance_name) LIKE '%"+qstr.toUpperCase()+"%'";
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet SearchProductsByUNII(DBCon dbcon,String unii)
	throws SQLException
  {
    String sql=sql_product;
    sql+=" WHERE ai.active_moiety_unii = '"+unii+"'";
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet GetProductByID(DBCon dbcon,Integer id)
	throws SQLException
  {
    String sql=sql_product;
    sql+=" WHERE p.id = "+id;
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet GetCompoundProducts(DBCon dbcon,int id)
	throws SQLException
  {
    String sql=sql_product;
    sql+=" WHERE s.id = "+id;
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  public static ResultSet SearchProductsByCompoundID(DBCon dbcon,List<Integer> ids)
	throws SQLException
  {
    String sql=sql_product;
    sql+=" WHERE s.id IN ( ";
    for (int i=0;i<ids.size(); ++i)
      sql+=(((i==0)?"":",")+ids.get(i));
    sql+=(")");
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }

  /////////////////////////////////////////////////////////////////////////////
  /// ACTIVITY - TARGET queries:
  /// Late 2016: activities table replaced by act_table_full, not linked to
  /// reference.
  /////////////////////////////////////////////////////////////////////////////
  private static String sql_activity=
"SELECT s.id \"cid\", a.act_id, a.act_type, a.act_value, a.act_unit, a.act_source \"act_source\", a.act_comment, a.act_source_url, a.moa_source_url, a.relation, a.moa, a.moa_source, "+
"td.id \"tid\", td.name \"target_name\", td.target_class, td.protein_type, tc.id \"tcid\", "+
"tc.accession \"protein_accession\", tc.swissprot, tc.organism, tc.gene \"gene_symbol\", tc.geneid, tc.name \"protein_name\""+
"FROM public.structures AS s "+
"LEFT OUTER JOIN public.act_table_full AS a ON s.id = a.struct_id "+
"JOIN public.target_dictionary AS td ON a.target_id = td.id "+
"JOIN public.td2tc AS td2tc ON td.id = td2tc.target_id "+
"JOIN public.target_component AS tc ON tc.id = td2tc.component_id ";
  /////////////////////////////////////////////////////////////////////////////
  public static ResultSet GetCompoundActivities(DBCon dbcon,int cid)
	throws SQLException
  {
    String sql=sql_activity+" WHERE s.id = "+cid;
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  /////////////////////////////////////////////////////////////////////////////
  public static ResultSet GetCompoundTargetActivities(DBCon dbcon,int cid,int tid)
	throws SQLException
  {
    String sql=sql_activity+" WHERE s.id = "+cid+" AND td.id = "+tid;
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  /////////////////////////////////////////////////////////////////////////////
  public static ResultSet GetProductIngredients(DBCon dbcon,int id)
	throws SQLException
  {
    String sql="SELECT ai.id, ai.active_moiety_unii, ai.active_moiety_name, ai.unit, ai.quantity, ai.substance_unii, ai.substance_name, ai.ndc_product_code, ai.struct_id, ai.quantity_denom_unit, ai.quantity_denom_value, s.molfile, s.cd_molweight, s.cas_reg_no, s.name \"struct_name\" FROM public.active_ingredient AS ai, public.product p, public.structures s WHERE ai.ndc_product_code = p.ndc_product_code AND ai.struct_id = s.id AND p.id = "+id;
    ResultSet rset=dbcon.executeSql(sql);
    return rset;
  }
  /////////////////////////////////////////////////////////////////////////////
  /**   Search Db and return hitlist of products.
  */
  public static ProductList SearchProducts(DBCon dbcon,DCQuery dbquery,StringBuilder log)
      throws SQLException,IOException
  {
    String qtxt=dbquery.getText();
    String qtype=dbquery.getType();
    ResultSet rset=null;
    ArrayList<Integer> ids = new ArrayList<Integer>();
    ProductList products = new ProductList();
    if (qtype.equalsIgnoreCase("fulltxt"))
    {
      rset = SearchProductsByProductName(dbcon,qtxt,true);
      ResultSet2Products(dbcon,rset,products,100,log);
      if (rset!=null) rset.getStatement().close();
      rset = SearchProductsByIngredientName(dbcon,qtxt,true);
      ResultSet2Products(dbcon,rset,products,20,log);
      if (rset!=null) rset.getStatement().close();
    }
    else if (qtype.equalsIgnoreCase("subtxt"))
    {
      rset = SearchProductsByProductName(dbcon,qtxt,true);
      ResultSet2Products(dbcon,rset,products,100,log);
      if (rset!=null) rset.getStatement().close();
      rset = SearchProductsByIngredientName(dbcon,qtxt,true);
      ResultSet2Products(dbcon,rset,products,20,log);
      if (rset!=null) rset.getStatement().close();

      rset = SearchProductsByProductName(dbcon,qtxt,false);
      ResultSet2Products(dbcon,rset,products,10,log);
      if (rset!=null) rset.getStatement().close();
      rset = SearchProductsByIngredientName(dbcon,qtxt,false);
      ResultSet2Products(dbcon,rset,products,2,log);
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
	Assign name search matchpoints to hits so better matches rank higher.  E.g. structure
	name match better than synonym match.  On subtxt, match fulltxt matches higher.

	Problem: hits can include mixtures where compound is minority ingredient.
	But without this, "Viagra" not found.
	Maybe filter for one-ingredient products?  Optionally?
  */
  public static CompoundList SearchCompounds(DBCon dbcon,DCQuery dbquery,StringBuilder log)
      throws Exception
  {
    String qtxt=dbquery.getText();
    String qtype=dbquery.getType();
    java.util.Date t_0 = new java.util.Date();
    ResultSet rset=null;
    ArrayList<Integer> ids = new ArrayList<Integer>();
    CompoundList cpds = new CompoundList();
    cpds.setQuery(dbquery);
    if (qtype.equalsIgnoreCase("fulltxt"))
    {
      rset = SearchCompoundsByStructureName(dbcon,qtxt,true);
      ResultSet2Compounds(dbcon,rset,cpds,100,log);
      if (rset!=null) rset.getStatement().close();
      rset = SearchCompoundsBySynonym(dbcon,qtxt,true);
      ResultSet2Compounds(dbcon,rset,cpds,50,log);
      if (rset!=null) rset.getStatement().close();
      rset = SearchCompoundsByProductName(dbcon,qtxt,true);
      ResultSet2Compounds(dbcon,rset,cpds,20,log);
      if (rset!=null) rset.getStatement().close();
    }
    else if (qtype.equalsIgnoreCase("subtxt"))
    {
      rset = SearchCompoundsByStructureName(dbcon,qtxt,true);
      ResultSet2Compounds(dbcon,rset,cpds,100,log);
      if (rset!=null) rset.getStatement().close();
      rset = SearchCompoundsBySynonym(dbcon,qtxt,true);
      ResultSet2Compounds(dbcon,rset,cpds,50,log);
      if (rset!=null) rset.getStatement().close();
      rset = SearchCompoundsByProductName(dbcon,qtxt,true);
      ResultSet2Compounds(dbcon,rset,cpds,20,log);
      if (rset!=null) rset.getStatement().close();

      rset = SearchCompoundsByStructureName(dbcon,qtxt,false);
      ResultSet2Compounds(dbcon,rset,cpds,10,log);
      if (rset!=null) rset.getStatement().close();
      rset = SearchCompoundsBySynonym(dbcon,qtxt,false);
      ResultSet2Compounds(dbcon,rset,cpds,5,log);
      if (rset!=null) rset.getStatement().close();
      rset = SearchCompoundsByProductName(dbcon,qtxt,false);
      ResultSet2Compounds(dbcon,rset,cpds,2,log);
      if (rset!=null) rset.getStatement().close();
    }
    else if (qtype.equalsIgnoreCase("cidext"))
    {
      String extidtype=dbquery.getExtIdType();
      rset = SearchCompoundsByExtID(dbcon,qtxt,extidtype);
      ResultSet2Compounds(dbcon,rset,cpds,100,log);
      if (rset!=null) rset.getStatement().close();
    }
    else if (qtype.equalsIgnoreCase("unii"))
    {
      rset = SearchCompoundsByUNII(dbcon,qtxt);
      ResultSet2Compounds(dbcon,rset,cpds,100,log);
      if (rset!=null) rset.getStatement().close();
    }
    else if (qtype.toLowerCase().matches("atc[1-4]"))
    {
      rset = SearchCompoundsByATC(dbcon,qtxt,Integer.parseInt(qtype.replaceFirst("^(?i)atc([1-4])$","$1"))); //case-insensitive
      ResultSet2Compounds(dbcon,rset,cpds,100,log);
      if (rset!=null) rset.getStatement().close();
    }
    //"simstruct", "substruct", "fullstruct"
    else if (qtype.startsWith("substr") || qtype.startsWith("fullstr") || qtype.startsWith("simstr"))
    {
      rset = SearchCompoundsByStructure(dbcon,qtype,qtxt);
      ResultSet2Compounds(dbcon,rset,cpds,100,log);
      if (rset!=null) rset.getStatement().close();
    }
    else
    {
      System.err.println("ERROR: invalid query: \""+dbquery.toString()+"\"");
    }
    log.append("SearchCompounds elapsed time: "+time_utils.TimeDeltaStr(t_0,new java.util.Date()));
    return cpds;
  }

  /////////////////////////////////////////////////////////////////////////////
  /**   Query Db and return one cpd.
  */
  public static DCCompound GetCompound(DBCon dbcon,DCQuery dbquery,StringBuilder log)
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
    DCCompound cpd = new DCCompound(cpd_id);
    ResultSet rset = GetCompoundByID(dbcon,cpd_id);
    if (!rset.next()) return cpd;
    try { if (cpd.getMolfile()==null) cpd.setMolfile(rset.getString("molfile")); }
    catch (CDKException e) { System.err.println("DEBUG: error: "+e.toString()); }
    catch (Exception e) { } //molfile error, probably missing for large mols
    if (cpd.getMwt()==null) cpd.setMwt(rset.getDouble("cd_molweight"));
    cpd.setCAS(rset.getString("cas_reg_no"));
    rset.getStatement().close();
    LoadCompoundData(dbcon,cpd,log);
    return cpd;
  }

  /////////////////////////////////////////////////////////////////////////////
  /**   Query Db and return one product.
  */
  public static DCProduct GetProduct(DBCon dbcon,DCQuery dbquery,StringBuilder log)
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
    DCProduct product = new DCProduct(product_id);
    ResultSet rset = GetProductByID(dbcon,product_id);
    ResultSet2Product(dbcon,rset,product,log);
    if (rset!=null) rset.getStatement().close();
    return product;
  }

  /////////////////////////////////////////////////////////////////////////////
  /**	Append results to product list, for compound page.
  */
  public static void ResultSet2Products(DBCon dbcon,ResultSet rset,ProductList products,int matchpoints,StringBuilder log)
      throws SQLException
  {
    if (rset==null) return;
    java.util.Date t_0 = new java.util.Date();
    while (rset.next())
    {
      Integer id=rset.getInt("ID");
      if (products.containsKey(id)) continue; //no duplicates
      DCProduct product = new DCProduct(id);

      product.setNdc(rset.getString("ndc_product_code"));
      product.setForm(rset.getString("form"));
      product.setRoute(rset.getString("route"));
      product.setProductname(rset.getString("product_name"));
      product.setGenericname(rset.getString("generic_name"));
      product.setStatus(rset.getString("marketing_status"));
      product.setIngredientCount(rset.getInt("active_ingredient_count"));

      ResultSet rset2 = GetProductIngredients(dbcon,id);
      while (rset2.next())
      {
        Integer iid=rset2.getInt("id");
        if (!product.hasIngredient(iid))
        {
          DCIngredient ingr = new DCIngredient(iid);
          ingr.setActivemoietyUnii(rset2.getString("active_moiety_unii"));
          ingr.setActivemoietyName(rset2.getString("active_moiety_name"));
          ingr.setSubstanceUnii(rset2.getString("substance_unii"));
          ingr.setSubstanceName(rset2.getString("substance_name"));
          ingr.setUnit(rset2.getString("unit"));
          ingr.setQuantity(rset2.getString("quantity"));
          ingr.setQuantityUnit(rset2.getString("quantity_denom_unit"));
          ingr.setQuantityValue(rset2.getString("quantity_denom_value"));
          product.addIngredient(ingr);
        }
        DCIngredient ingr = product.getIngredient(iid);

        if (ingr.getCompound()==null)
        {
          Integer struct_id=rset2.getInt("struct_id");
          DCCompound cpd = new DCCompound(struct_id);
          ingr.setCompound(cpd);

          try { if (cpd.getMolfile()==null) cpd.setMolfile(rset.getString("molfile")); }
          catch (CDKException e) { System.err.println("DEBUG: error: "+e.toString()); }
          catch (Exception e) { } //molfile error, probably missing for large mols
          if (cpd.getMwt()==null) cpd.setMwt(rset.getDouble("cd_molweight"));
          cpd.setCAS(rset2.getString("cas_reg_no"));
        }
      }
      rset2.getStatement().close();
      products.put(id,product);
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  /**	Results to product, for product page.
  */
  public static void ResultSet2Product(DBCon dbcon,ResultSet rset,DCProduct product,StringBuilder log)
      throws SQLException
  {
    if (rset==null) return;
    if (!rset.next()) return;
    java.util.Date t_0 = new java.util.Date();

    Integer id=rset.getInt("id");
    if (!product.getID().equals(id))
    {
      log.append("ERROR: product ID mismatch ("+product.getID()+"!="+id+")\n");
      return;
    }

    product.setNdc(rset.getString("ndc_product_code"));
    product.setForm(rset.getString("form"));
    product.setRoute(rset.getString("route"));
    product.setProductname(rset.getString("product_name"));
    product.setGenericname(rset.getString("generic_name"));
    product.setStatus(rset.getString("marketing_status"));
    product.setIngredientCount(rset.getInt("active_ingredient_count"));

    ResultSet rset2 = GetProductIngredients(dbcon,id);
    while (rset2.next())
    {
      Integer iid=rset2.getInt("id");
      if (!product.hasIngredient(iid))
      {
        DCIngredient ingr = new DCIngredient(iid);
        ingr.setActivemoietyUnii(rset2.getString("active_moiety_unii"));
        ingr.setActivemoietyName(rset2.getString("active_moiety_name"));
        ingr.setSubstanceUnii(rset2.getString("substance_unii"));
        ingr.setSubstanceName(rset2.getString("substance_name"));
        ingr.setUnit(rset2.getString("unit"));
        ingr.setQuantity(rset2.getString("quantity"));
        ingr.setQuantityUnit(rset2.getString("quantity_denom_unit"));
        ingr.setQuantityValue(rset2.getString("quantity_denom_value"));
        product.addIngredient(ingr);
      }
      DCIngredient ingr = product.getIngredient(iid);

      if (ingr.getCompound()==null)
      {
        Integer struct_id=rset2.getInt("struct_id");
        DCCompound cpd = GetCompound(dbcon,new DCQuery(String.format("%d[cid]",struct_id)),log);
        ingr.setCompound(cpd);
      }
    }
    rset2.getStatement().close();
  }

  /////////////////////////////////////////////////////////////////////////////
  /**	Append results to cpd list.  Also get associated targets.
	Only retreive data needed for search results view.
	NEW: Initial rset only structures+mols cols.  Separate queries
	for related data.
  */
  public static void ResultSet2Compounds(DBCon dbcon,ResultSet rset,CompoundList cpds,int matchpoints,
	StringBuilder log)
      throws SQLException
  {
    if (rset==null) return;
    ResultSetMetaData rsmd=rset.getMetaData();
    HashSet<String> colnames = new HashSet<String>();
    for (int i=1;i<=rsmd.getColumnCount();++i)
      colnames.add(rsmd.getColumnName(i));
    //System.err.println("DEBUG: colnames = "+colnames.toString());
    if (!rset.next()) return;
    do
    {
      Integer id=rset.getInt("id"); //structure ID
      if (cpds.containsKey(id)) continue;

      DCCompound cpd = new DCCompound(id);
      cpds.put(id,cpd);
      cpd.setMatchpoints(matchpoints);

      try { if (cpd.getMolfile()==null) cpd.setMolfile(rset.getString("molfile")); }
      catch (CDKException e) { System.err.println("DEBUG: error: "+e.toString()); }
      catch (Exception e) { } //molfile error, probably missing for large mols
      if (cpd.getMwt()==null) cpd.setMwt(rset.getDouble("cd_molweight"));
      cpd.setCAS(rset.getString("cas_reg_no"));

      LoadCompoundData(dbcon,cpd,log);

      //IF similarity search:
      if (colnames.contains("sim"))
        cpd.setSimilarity(rset.getFloat("sim"));
    } while (rset.next());
  }
  /////////////////////////////////////////////////////////////////////////////
  /**	Result (one row) to cpd.  Get additional related data via separate queries.
  */
  public static void LoadCompoundData(DBCon dbcon,DCCompound cpd,StringBuilder log)
      throws SQLException
  {
    Integer id=cpd.getDCID();

    //Products:
    ResultSet rset2 = GetCompoundProducts(dbcon,id);
    ProductList products = new ProductList();
    ResultSet2Products(dbcon,rset2,products,0,log);
    rset2.getStatement().close();
    for (DCProduct prd: products.getAllSortedByRelevance())
      cpd.addProduct(prd);

    //Names:
    rset2 = GetCompoundSynonyms(dbcon,id);
    while (rset2.next())
      if (rset2.getString("synonym")!=null)
        cpd.addName(rset2.getString("synonym"));
    rset2.getStatement().close();

    //ATCs:
    rset2=GetCompoundATCs(dbcon,id);
    while (rset2.next())
    {
      if (rset2.getString("l1_code")!=null)
      {
        ATC atc = new ATC();
        for (int lev=1;lev<=4;++lev)
        {
          atc.setCode(lev,rset2.getString("l"+lev+"_code"));
          atc.setName(lev,rset2.getString("l"+lev+"_name"));
        }
        cpd.addATC(atc);
      }
    }
    rset2.getStatement().close();

    //Approvals:
    rset2 = GetCompoundApprovals(dbcon,id);
    if (rset2.next())
    {
      cpd.setApprovalDate(rset2.getDate("approval_date"));
      cpd.setApprovalType(rset2.getString("approval_type"));
      cpd.setApprovalApplicant(rset2.getString("approval_applicant"));
    }

    //IDs:
    //rset2 = GetCompoundUniis(dbcon,id);
    //if (rset2.next())
    //  cpd.setExtID("active_moiety_unii",rset2.getString("active_moiety_unii"));
    //rset2.getStatement().close();
    rset2 = GetCompoundIDs(dbcon,id);
    while (rset2.next())
      cpd.setExtID(rset2.getString("id_type"),rset2.getString("id_val"));
    rset2.getStatement().close();

    //Activities+Targets:
    rset2 = GetCompoundActivities(dbcon,id);
    ResultSet2CompoundActivities(rset2,cpd);
    rset2.getStatement().close();
  }

  /////////////////////////////////////////////////////////////////////////////
  public static void ResultSet2CompoundActivities(ResultSet rset,DCCompound cpd)
      throws SQLException
  {
    if (rset==null) return;
    while (rset.next())
    {
      Integer act_id = rset.getInt("act_id");
      if (act_id==null) continue;
      if (!cpd.hasActivity(act_id))
        cpd.addActivity(new DCActivity(act_id));

      DCActivity act = cpd.getActivity(act_id);
      act.setCompound(cpd);
      act.setMoa(rset.getInt("moa"));
      //act.setMoaType(rset.getString("moa_type"));
      act.setMoaSource(rset.getString("moa_source"));
      act.setMoaSourceUrl(rset.getString("moa_source_url"));
      //act.setMoaRefID(rset.getInt("moa_ref_id"));
      act.setRelation(rset.getString("relation"));
      act.setType(rset.getString("act_type"));
      act.setUnit(rset.getString("act_unit"));
      act.setComment(rset.getString("act_comment"));
      act.setSource(rset.getString("act_source"));
      act.setSourceUrl(rset.getString("act_source_url"));
      act.setValue(rset.getDouble("act_value"));

      //act.setRefID(rset.getInt("ref_id"));
      //act.setRefPMID(rset.getString("ref_pmid"));
      //act.setRefTitle(rset.getString("ref_title"));
      //act.setRefJournal(rset.getString("ref_journal"));
      //act.setRefAuthors(rset.getString("ref_authors"));
      //act.setRefDOI(rset.getString("ref_doi"));
      //act.setRefURL(rset.getString("ref_url"));
      //act.setRefYear(rset.getInt("ref_year"));

      //act.setMoaRefID(rset.getInt("moa_ref_id"));
      //act.setMoaRefPMID(rset.getString("moa_ref_pmid"));
      //act.setMoaRefTitle(rset.getString("moa_ref_title"));
      //act.setMoaRefJournal(rset.getString("moa_ref_journal"));
      //act.setMoaRefAuthors(rset.getString("moa_ref_authors"));
      //act.setMoaRefDOI(rset.getString("moa_ref_doi"));
      //act.setMoaRefURL(rset.getString("moa_ref_url"));
      //act.setMoaRefYear(rset.getInt("moa_ref_year"));

      Integer tid = rset.getInt("tid");
      if (tid==null) continue;
      if (!cpd.hasTarget(tid))
        cpd.addTarget(new DCTarget(tid));
      DCTarget tgt = cpd.getTarget(tid);
      act.setTarget(tgt);

      tgt.setName(rset.getString("target_name"));
      Integer tcid = rset.getInt("tcid");
      if (tcid!=null)
      {
        if (!tgt.hasComponent(tcid))
          tgt.addComponent(new DCTargetComponent(tcid));
        DCTargetComponent tc = tgt.getComponent(tcid);
        if (rset.getString("protein_name")!=null) tc.setName(rset.getString("protein_name"));
        if (rset.getString("protein_accession")!=null) tc.setAccession(rset.getString("protein_accession"));
        if (rset.getString("organism")!=null) tc.setOrganism(rset.getString("organism"));
        if (rset.getString("swissprot")!=null) tc.setSwissprot(rset.getString("swissprot"));
        if (rset.getString("gene_symbol")!=null) tc.setGenesymbol(rset.getString("gene_symbol"));
        tc.setGeneID(rset.getInt("geneid"));
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  public static String ResultProduct(DCProduct product, PrintWriter fout_writer)
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
    for (DCIngredient ingr: product.getIngredients())
    {
      ++i;
      DCCompound cpd = ingr.getCompound();
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
    if (fout_writer!=null) fout_writer.write(txt);
    return txt;
  }

  /////////////////////////////////////////////////////////////////////////////
  public static String ResultCompound(DCCompound cpd, boolean full, PrintWriter fout_writer)
  {
    String txt=("Compound [ID="+cpd.getDCID()+"]\n");
    txt+=String.format("MF:\t%s\n",cpd.getMolformula());
    txt+=String.format("MWT:\t%.2f\n",cpd.getMwt());
    txt+="Smiles:\t"+cpd.getSmiles()+"\n";

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
    for (DCActivity act: cpd.getActivityList().getAllSortedByRelevance())
    {
      DCTarget tgt = act.getTarget();
      txt+=("\t"+(++i_tgt)+". ["+tgt.getID()+"]");
      txt+=((act.getMoa().equals(1) && act.getMoaType()!=null)?"(MOA:"+act.getMoaType()+") ":"");
      txt+=("\""+tgt.getName()+"\"");
      txt+=(" (components: "+tgt.componentCount()+"; ");
      int i_tgtc=0;
      for (DCTargetComponent tgtc: tgt.getComponents())
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
    txt+="matchpoints:\t"+cpd.getMatchpoints()+"\n";
    if (fout_writer!=null) fout_writer.write(txt);
    return txt;
  }

  /////////////////////////////////////////////////////////////////////////////
  public static String ResultCompounds(CompoundList cpds, PrintWriter fout_writer)
  {
    List<DCCompound> cpds_sorted;
    if (cpds.getType().startsWith("simstr"))
      cpds_sorted = cpds.getAllSortedBySimilarity();
    else
      cpds_sorted = cpds.getAllSortedByRelevance();
    String txt="";
    for (DCCompound cpd: cpds_sorted)
    {
      Integer cpd_id = cpd.getDCID();
      txt+=("-----------------------------------------------------------------------------\n");
      if (cpds.getType().startsWith("simstr"))
        txt+=("similarity: "+cpd.getSimilarity()+"\n");
      txt+=ResultCompound(cpd, false, null);
    }
    txt+=("-----------------------------------------------------------------------------\n");
    txt+=("Compound count: "+cpds.size()+"\n");
    fout_writer.write(txt);
    return txt;
  }

  /////////////////////////////////////////////////////////////////////////////
  public static String ResultCompoundActivities(DCCompound cpd, PrintWriter fout_writer)
  {
    String txt="";
    txt+=("Compound ID: "+cpd.getDCID()+"\n");
    int i_tgt=0;
    for (DCActivity act: cpd.getActivityList().getAllSortedByRelevance())
    {
      DCTarget tgt = act.getTarget();
      txt+=(""+(++i_tgt)+".\tTID: "+tgt.getID()+"\n");
      txt+=("\tName: "+tgt.getName()+"\n");
      txt+=("\tACT_UNIT: "+act.getUnit()+"\n"); //15549 of 15551 are NULL
      txt+=("\tACT_TYPE: "+act.getType()+"\n");
      txt+=("\tACT_VALUE: "+(String.valueOf(act.getRelation()).matches("[><]")?act.getRelation():"")+String.format("%.3f",act.getValue())+"\n");
      txt+=("\tACT_SOURCE: "+act.getSource()+"\n");
      txt+=("\tACT_SOURCE_URL: "+act.getSourceUrl()+"\n");
      if (act.getComment()!=null)
        txt+=("\tACT_COMMENT: "+act.getComment()+"\n");
      //txt+=("\tREF_ID: "+act.getRefID()+"\n");
      //txt+=("\tREF_PMID: "+act.getRefPMID()+"\n");
      //txt+=("\tREF_TITLE: "+act.getRefTitle()+"\n");
      //txt+=("\tREF_JOURNAL: "+act.getRefJournal()+"\n");
      //txt+=("\tREF_AUTHORS: "+act.getRefAuthors()+"\n");
      //txt+=("\tREF_YEAR: "+act.getRefYear()+"\n");
      //txt+=("\tREF_DOI: "+act.getRefDOI()+"\n");
      //txt+=("\tREF_URL: "+act.getRefURL()+"\n");
      if (act.getMoa().equals(1) && act.getMoaType()!=null)
      {
        //txt+=("\tMOA: "+act.getMoa()+"\n");
        txt+=("\tMOA_TYPE: "+act.getMoaType()+"\n");
        txt+=("\tMOA_SOURCE: "+act.getMoaSource()+"\n");
        txt+=("\tMOA_SOURCE_URL: "+act.getMoaSourceUrl()+"\n");
        //txt+=("\tMOA_REF_ID: "+act.getMoaRefID()+"\n");
        //txt+=("\tMOA_REF_PMID: "+act.getMoaRefPMID()+"\n");
        //txt+=("\tMOA_REF_TITLE: "+act.getMoaRefTitle()+"\n");
        //txt+=("\tMOA_REF_JOURNAL: "+act.getMoaRefJournal()+"\n");
        //txt+=("\tMOA_REF_AUTHORS: "+act.getMoaRefAuthors()+"\n");
        //txt+=("\tMOA_REF_YEAR: "+act.getMoaRefYear()+"\n");
        //txt+=("\tMOA_REF_DOI: "+act.getMoaRefDOI()+"\n");
        //txt+=("\tMOA_REF_URL: "+act.getMoaRefURL()+"\n");
      }
      txt+=("\n");
    }
    fout_writer.write(txt);
    return txt;
  }

  /////////////////////////////////////////////////////////////////////////////
  public static String ResultProducts(ProductList products, PrintWriter fout_writer)
  {
    String txt="";
    int i=0;
    for (DCProduct product: products.getAllSortedByRelevance())
    {
      ++i;
      txt+="--- "+i+".\n";
      Integer product_id = product.getID();
      txt+=ResultProduct(product, null);
    }
    txt+=("Product count: "+products.size()+"\n");
    fout_writer.write(txt);
    return txt;
  }
}
