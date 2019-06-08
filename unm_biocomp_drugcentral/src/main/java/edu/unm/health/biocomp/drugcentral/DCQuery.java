package edu.unm.health.biocomp.drugcentral;

import java.text.*;
import java.util.*;
import java.util.regex.*;

/**	Supported query types: (1) subtxt, (2) fulltxt, (3) substr[uct],
	(4) fullstr[uct], (5) simstr[uct];  Default is subtxt.

	Examples:

	penicillin[fulltxt]
	amphetamine[subtxt]
	C12CCCC1CCC1C2CCC2=CCCCC12[substruct]
	J01CE[atc4]
	3386[cidext]
	3386[cidext]&amp;idtype=PUBCHEM_CID
	CHEMBL41[cidext]&amp;idtype=ChEMBL_ID
	D005473[cidext]&amp;idtype=MESH
*/
public class DCQuery
{
  private String qtxt;
  private String qtype;
  private String extidtype;
  private static final Pattern QPAT=Pattern.compile("(.+)\\[(.+)\\]\\s*$");

  public DCQuery(String _query)
  {
    setQuery(_query);
  }
  public String getType() { return this.qtype; }
  public void setType(String _qtype) { this.qtype=_qtype; }
  public String getText() { return this.qtxt; }
  public void setText(String _txt) { this.qtxt=_txt; }
  public String getExtIdType() { return this.extidtype; }
  public void setExtIdType(String _extidtype) { this.extidtype=_extidtype; }
  public String toString() { return this.qtxt+"["+this.qtype+"]"; }
  public void setQuery(String _query)
  {
    this.qtxt=_query; //default
    this.qtype="subtxt"; //default
    Matcher qmat=QPAT.matcher(_query);
    if (qmat.find())
    {
      this.qtxt=qmat.group(1);
      this.qtype=qmat.group(2).toLowerCase();
    }
  }
  public boolean isValid()
  {
    return (
	this.getType().equalsIgnoreCase("subtxt")
	|| this.getType().equalsIgnoreCase("fulltxt")
	|| this.getType().equalsIgnoreCase("cid")
	|| this.getType().equalsIgnoreCase("cidext")
	|| this.getType().equalsIgnoreCase("tid")
	|| this.getType().equalsIgnoreCase("pid")
	|| this.getType().equalsIgnoreCase("substruct")
	|| this.getType().equalsIgnoreCase("simstruct")
	|| this.getType().equalsIgnoreCase("fullstruct")
	|| this.getType().equalsIgnoreCase("unii")
	|| this.getType().matches("atc[1-4]")
    );
  }
}
