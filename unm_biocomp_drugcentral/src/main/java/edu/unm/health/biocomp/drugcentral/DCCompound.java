package edu.unm.health.biocomp.drugcentral;

import java.io.*;
import java.util.*;

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
import org.openscience.cdk.tools.*; // CDKHydrogenAdder
import org.openscience.cdk.stereo.*; // StereoElementFactory

import edu.unm.health.biocomp.text.*; //NameList,Name

/**	Represents one DC compound, unique chemical entity.
	Each compound has an associated set of activities, and via those
	activities, an associated set of targets.  Conceptually targets
	are associated directly with activities, not compounds.  However
	for convenience we associated targets with compounds.  This also
	helps count targets, since the same target can be associated with
	distinct activities.

*/
public class DCCompound extends AtomContainer
	implements Comparable<Object>
{
  private HashSet<String> names;
  private Integer id; //DC ID
  private Integer n_active_moiety;
  private String smi;
  private String molfile; //MDL V2000 format
  private String cas;
  private ArrayList<ATC> atcs; //may have multiple ATCs, each 4-level codes + names
  private HashMap<String,String> extids; //External IDs, e.g. UNII
  private ActivityList activities;
  private TargetList targets;
  private ProductList products;
  private Integer matchpoints; //for ranking search hits
  private Float sim; //similarity search result
  private Double mwt;
  private String mf;
  private java.util.Date approval_date;
  private String approval_type;
  private String approval_applicant;

  private DCCompound() {} //disallow default constructor

  public DCCompound(Integer _id)
  {
    this.id = _id;
    this.names = new HashSet<String>();
    this.atcs = new ArrayList<ATC>();
    this.extids = new HashMap<String,String>();
    this.activities = new ActivityList();
    this.targets = new TargetList();
    this.products = new ProductList();
    this.matchpoints = 0;
  }

  public void setCAS(String _cas) { this.cas = _cas; }
  public String getCAS() { return this.cas; }
  public Integer getDCID() { return this.id; }
  public void addName(String _name) { this.names.add(_name); }
  public int nameCount() { return this.names.size(); }

  public ArrayList<ATC> getAtcs()
  {
    Collections.sort(this.atcs);
    return this.atcs;
  }
  public int atcCount() { return this.atcs.size(); }
  public void addATC(ATC _atc)
  {
    boolean ok=true;
    for (ATC atc: this.atcs)
    {
      if (atc.equals(_atc))
      {
        ok=false;
        break;
      }
    }
    if (ok) this.atcs.add(_atc);
  }

  public void setExtID(String _type,String _extid) { this.extids.put(_type,_extid); }
  public String getExtID(String _type) { return this.extids.get(_type); }
  public boolean hasExtID(String _type) { return this.extids.containsKey(_type); }
  public Collection<String> getExtIDTypes()
  {
    ArrayList<String> idtypes = new ArrayList<String>(this.extids.keySet());
    Collections.sort(idtypes);
    return idtypes;
  }

  public void addActivity(DCActivity _act) { this.activities.put(_act.getID(),_act); }
  public DCActivity getActivity(Integer _aid) { return this.activities.get(_aid); }
  public boolean hasActivity(Integer _aid) { return this.activities.containsKey(_aid); }
  public Collection<DCActivity> getActivities() { return this.activities.values(); }
  public ActivityList getActivityList() { return this.activities; }
  public int activityCount() { return this.activities.size(); }

  public void addTarget(DCTarget _tgt) { this.targets.put(_tgt.getID(),_tgt); }
  public DCTarget getTarget(Integer _tid) { return this.targets.get(_tid); }
  public boolean hasTarget(Integer _tid) { return this.targets.containsKey(_tid); }
  public Collection<DCTarget> getTargets() { return this.targets.values(); }
  public TargetList getTargetList() { return this.targets; }
  public int targetCount() { return this.targets.size(); }

  public void addProduct(DCProduct _prd) { this.products.put(_prd.getID(),_prd); }
  public DCProduct getProduct(Integer _pid) { return this.products.get(_pid); }
  public boolean hasProduct(Integer _pid) { return this.products.containsKey(_pid); }
  public Collection<DCProduct> getProducts() { return this.products.values(); }
  public ProductList getProductList() { return this.products; }
  public int productCount() { return this.products.size(); }

  public Integer getMatchpoints() { return this.matchpoints; }
  public void setMatchpoints(int _mp) { this.matchpoints = _mp; }
  public void addMatchpoints(int _mp) { this.matchpoints += _mp; }

  public void setApprovalDate(java.util.Date _approval_date) { this.approval_date = _approval_date; }
  public java.util.Date getApprovalDate() { return this.approval_date; }
  public void setApprovalType(String _approval_type) { this.approval_type = _approval_type; }
  public String getApprovalType() { return this.approval_type; }
  public void setApprovalApplicant(String _approval_applicant) { this.approval_applicant = _approval_applicant; }
  public String getApprovalApplicant() { return this.approval_applicant; }

  public void setSimilarity(Float _sim) { this.sim= _sim; }
  public Float getSimilarity() { return this.sim; }

  public void setMwt(Double _mwt) { this.mwt= _mwt; }
  public Double getMwt() { return this.mwt; }

  public String getMolformula() { return this.mf; }
  public void setMolformula(String _mf) { this.mf=_mf; }

  /** Returns unique set of compund names, nicely sorted.  */
  public NameList getNames()
  {
    NameList nlist = new NameList(Arrays.asList(this.names.toArray(new String[0])));
    Collections.sort(nlist);
    Collections.reverse(nlist);
    return nlist;
  }

  /** Returns unique set of product names, nicely sorted.  */
  public NameList getProductnames()
  {
    NameList nlist = new NameList();
    for (DCProduct prd: this.getProducts())
    {
      nlist.merge(new Name(prd.getProductname()));
    }
    Collections.sort(nlist);
    Collections.reverse(nlist);
    return nlist;
  }

  public void setSmiles(String _smi) { this.smi = _smi; }
  public String getSmiles() { return this.smi; }

  public void setMolfile(String _molfile)
	throws CDKException
  {
    this.molfile = _molfile;
    MDLReader mdlreader = new MDLReader(new StringReader(_molfile));
    mdlreader.read(this);
    AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(this);
    CDKHydrogenAdder.getInstance(this.getBuilder()).addImplicitHydrogens(this);
    //AtomContainerManipulator.suppressHydrogens(this); //Removes chiral Hs.
    AtomContainerManipulator.removeNonChiralHydrogens(this); //Fails on 3D mols.
    this.setStereoElements(StereoElementFactory.using2DCoordinates(this).createAll());
    SmilesGenerator smigen = SmilesGenerator.isomeric(); //stereo, Kekule
    String smi = smigen.create(this);
    this.setSmiles(smi);
    String mf = MolecularFormulaManipulator.getString(MolecularFormulaManipulator.getMolecularFormula(this));
    this.setMolformula(mf);
  }
  public String getMolfile() { return this.molfile; }

  public boolean isLarge() { return ((this.getMwt()!=null && this.getMwt()>1000.0)) || this.getAtomCount()>100; }

  /////////////////////////////////////////////////////////////////////////////
  public int compareTo(Object o)        //native-order (by id)
  {
    return (id>((DCCompound)o).id ? 1 : (id<((DCCompound)o).id ? -1 : 0));
  }
}

