package edu.unm.health.biocomp.drugdb;

import java.io.*;
import java.util.*;


/**	Container for DrugDBCompound hitlist, result of DrugDBQuery.
	Allowed types: substruct, fullstruct, simstruct, subtxt, fulltxt
*/
public class CompoundList extends HashMap<Integer,DrugDBCompound>
{
  private DrugDBQuery query;
  private String type; //redundant?
  private HashMap<Integer,Integer> hit2id;
  public void setQuery(DrugDBQuery _query) { this.query = _query; }
  public DrugDBQuery getQuery() { return this.query; }

  public void setType(String _type) { this.type = _type; } //redundant?
  public String getType() { return this.type; } //redundant?

  public DrugDBCompound getHit(int i_hit) { return this.get(hit2id.get(i_hit)); }

  public CompoundList()
  {
    this.type="";
    this.hit2id = new HashMap<Integer,Integer>();
  }
  /////////////////////////////////////////////////////////////////////////////
  /**	Numbered from 1.	*/
  private int nextHitIdx()
  {
    int i=0;
    for (int i_hit: hit2id.keySet()) i = Math.max(i,i_hit);
    return (i+1);
  }
  /////////////////////////////////////////////////////////////////////////////
  public DrugDBCompound put(Integer id,DrugDBCompound cpd)
  {
    super.put(cpd.getID(),cpd);
    this.hit2id.put(this.nextHitIdx(),cpd.getID());
    return super.get(cpd.getID());
  }
  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DrugDBCompound> getAllSortedByID()
  {
    ArrayList<DrugDBCompound> cpds = new ArrayList<DrugDBCompound>(this.values());
    Collections.sort(cpds);
    return cpds;
  }
  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DrugDBCompound> getAllSortedByRelevance()
  {
    ArrayList<DrugDBCompound> cpds = new ArrayList<DrugDBCompound>(this.values());
    Collections.sort(cpds,ByRelevance);
    return cpds;
  }
  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DrugDBCompound> getAllSortedBySimilarity()
  {
    ArrayList<DrugDBCompound> cpds = new ArrayList<DrugDBCompound>(this.values());
    Collections.sort(cpds,BySimilarity);
    return cpds;
  }
  public ArrayList<DrugDBCompound> getAllSortedByMwt()
  {
    ArrayList<DrugDBCompound> cpds = new ArrayList<DrugDBCompound>(this.values());
    Collections.sort(cpds,ByMwt);
    return cpds;
  }
  /////////////////////////////////////////////////////////////////////////////
  public static Comparator<DrugDBCompound> ByRelevance = new Comparator<DrugDBCompound>()
  {
    public int compare(DrugDBCompound cA,DrugDBCompound cB)
    {
      return (cA.getMatchpoints()<cB.getMatchpoints())?1:(cA.getMatchpoints()>cB.getMatchpoints()?-1:
        (cA.getMwt()>cB.getMwt())?1:(cA.getMwt()<cB.getMwt()?-1:0)
	);
    }
    boolean equals(DrugDBCompound cA,DrugDBCompound cB)
    { return (cA.getMatchpoints()==(cB.getMatchpoints())); }
  };
  /////////////////////////////////////////////////////////////////////////////
  public static Comparator<DrugDBCompound> BySimilarity = new Comparator<DrugDBCompound>()
  {
    public int compare(DrugDBCompound cA,DrugDBCompound cB)
    { return (cA.getSimilarity()<cB.getSimilarity())?1:(cA.getSimilarity()>cB.getSimilarity()?-1:0); }
    boolean equals(DrugDBCompound cA,DrugDBCompound cB)
    { return (cA.getSimilarity()==(cB.getSimilarity())); }
  };
  /////////////////////////////////////////////////////////////////////////////
  public static Comparator<DrugDBCompound> ByMwt = new Comparator<DrugDBCompound>()
  {
    public int compare(DrugDBCompound cA,DrugDBCompound cB)
    { return (cA.getMwt()>cB.getMwt())?1:(cA.getMwt()<cB.getMwt()?-1:0); }
    boolean equals(DrugDBCompound cA,DrugDBCompound cB)
    { return (cA.getMwt()==(cB.getMwt())); }
  };
}
