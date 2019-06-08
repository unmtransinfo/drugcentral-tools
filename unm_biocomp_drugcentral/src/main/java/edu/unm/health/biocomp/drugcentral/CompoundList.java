package edu.unm.health.biocomp.drugcentral;

import java.io.*;
import java.util.*;


/**	Container for DCCompound hitlist, result of DCQuery.
	Allowed types: substruct, fullstruct, simstruct, subtxt, fulltxt
*/
public class CompoundList extends HashMap<Integer,DCCompound>
{
  private DCQuery query;
  private String type; //redundant?
  private HashMap<Integer,Integer> hit2id;
  public void setQuery(DCQuery _query) { this.query = _query; this.type = _query.getType(); }
  public DCQuery getQuery() { return this.query; }

  public void setType(String _type) { this.type = _type; } //redundant?
  public String getType() { return this.type; } //redundant?

  public DCCompound getHit(int i_hit) { return this.get(hit2id.get(i_hit)); }

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
  public DCCompound put(Integer id,DCCompound cpd)
  {
    super.put(cpd.getDCID(),cpd);
    this.hit2id.put(this.nextHitIdx(),cpd.getDCID());
    return super.get(cpd.getDCID());
  }
  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DCCompound> getAllSortedByID()
  {
    ArrayList<DCCompound> cpds = new ArrayList<DCCompound>(this.values());
    Collections.sort(cpds);
    return cpds;
  }
  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DCCompound> getAllSortedByRelevance()
  {
    ArrayList<DCCompound> cpds = new ArrayList<DCCompound>(this.values());
    Collections.sort(cpds,ByRelevance);
    return cpds;
  }
  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DCCompound> getAllSortedBySimilarity()
  {
    ArrayList<DCCompound> cpds = new ArrayList<DCCompound>(this.values());
    Collections.sort(cpds,BySimilarity);
    return cpds;
  }
  public ArrayList<DCCompound> getAllSortedByMwt()
  {
    ArrayList<DCCompound> cpds = new ArrayList<DCCompound>(this.values());
    Collections.sort(cpds,ByMwt);
    return cpds;
  }
  /////////////////////////////////////////////////////////////////////////////
  public static Comparator<DCCompound> ByRelevance = new Comparator<DCCompound>()
  {
    public int compare(DCCompound cA,DCCompound cB)
    {
      return (cA.getMatchpoints()<cB.getMatchpoints())?1:(cA.getMatchpoints()>cB.getMatchpoints()?-1:
        (cA.getMwt()>cB.getMwt())?1:(cA.getMwt()<cB.getMwt()?-1:0)
	);
    }
    boolean equals(DCCompound cA,DCCompound cB)
    { return (cA.getMatchpoints()==(cB.getMatchpoints())); }
  };
  /////////////////////////////////////////////////////////////////////////////
  public static Comparator<DCCompound> BySimilarity = new Comparator<DCCompound>()
  {
    public int compare(DCCompound cA,DCCompound cB)
    { return (cA.getSimilarity()<cB.getSimilarity())?1:(cA.getSimilarity()>cB.getSimilarity()?-1:0); }
    boolean equals(DCCompound cA,DCCompound cB)
    { return (cA.getSimilarity()==(cB.getSimilarity())); }
  };
  /////////////////////////////////////////////////////////////////////////////
  public static Comparator<DCCompound> ByMwt = new Comparator<DCCompound>()
  {
    public int compare(DCCompound cA,DCCompound cB)
    { return (cA.getMwt()>cB.getMwt())?1:(cA.getMwt()<cB.getMwt()?-1:0); }
    boolean equals(DCCompound cA,DCCompound cB)
    { return (cA.getMwt()==(cB.getMwt())); }
  };
}
