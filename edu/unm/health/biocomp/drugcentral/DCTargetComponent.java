package edu.unm.health.biocomp.drugcentral;

import java.io.*;
import java.util.*;

/**	Represents one DC target component, defined by ID, and associated data.
	Each target component typically corresponds to one protein molecule.
	Each target comprised of one or more components.
*/

public class DCTargetComponent
{
  private Integer id; //DC ID
  private String acc; //Accession
  private String organism;
  private String swissprot;
  private String name;
  private String gsymb;
  private Integer geneid;
  private HashMap<String,String> extids; //External IDs, e.g. UNII

  private DCTargetComponent() {} //disallow default constructor

  public DCTargetComponent(Integer _id)
  {
    this.id = _id;
    this.extids = new HashMap<String,String>();
    //this.names = new HashSet<String>();
  }

  public Integer getID() { return this.id; }

  public void setExtID(String _type,String _extid) { this.extids.put(_type,_extid); }
  public String getExtID(String _type) { return this.extids.get(_type); }
  public boolean hasExtID(String _type) { return this.extids.containsKey(_type); }
  public Collection<String> getExtIDTypes() { return this.extids.keySet(); }

  public void setName(String _name) { this.name = _name; }
  public String getName() { return this.name; }
  public void setAccession(String _acc) { this.acc = _acc; }
  public String getAccession() { return this.acc; }
  public void setOrganism(String _organism) { this.organism = _organism; }
  public String getOrganism() { return this.organism; }
  public void setSwissprot(String _sprot) { this.swissprot = _sprot; }
  public String getSwissprot() { return this.swissprot; }
  public void setGenesymbol(String _gsymb) { this.gsymb = _gsymb; }
  public String getGenesymbol() { return this.gsymb; }
  public void setGeneID(Integer _geneid) { this.geneid = _geneid; }
  public Integer getGeneID() { return this.geneid; }
}
