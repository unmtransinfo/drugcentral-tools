package edu.unm.health.biocomp.drugcentral;

import java.io.*;
import java.util.*;


import edu.unm.health.biocomp.text.*; //NameList,Name

/**	Container for DCActivity list.
*/

public class ActivityList extends HashMap<Integer,DCActivity>
{
  private DCQuery query;
  public void setQuery(DCQuery _query) { this.query = _query; }
  public DCQuery getQuery() { return this.query; }

  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DCActivity> getAllSortedByID()
  {
    ArrayList<DCActivity> acts = new ArrayList<DCActivity>(this.values());
    Collections.sort(acts);
    return acts;
  }
  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DCActivity> getAllSortedByRelevance()
  {
    ArrayList<DCActivity> acts = new ArrayList<DCActivity>(this.values());
    Collections.sort(acts,ByRelevance);
    return acts;
  }

  /////////////////////////////////////////////////////////////////////////////
  // - MOA activies more relevant, better match.
  //
  public static Comparator<DCActivity> ByRelevance = new Comparator<DCActivity>()  {
    public int compare(DCActivity aA,DCActivity aB)
    {
      if (aA.getMoa()==1 && aB.getMoa()!=1) return -1;
      else if (aA.getMoa()!=1 && aB.getMoa()==1) return 1;
      else if (aA.getMoaType()!=null && aB.getMoaType()==null) return -1;
      else if (aA.getMoaType()==null && aB.getMoaType()!=null) return 1;
      else return aA.getTarget().getName().compareToIgnoreCase(aB.getTarget().getName());
    }
  };
}
