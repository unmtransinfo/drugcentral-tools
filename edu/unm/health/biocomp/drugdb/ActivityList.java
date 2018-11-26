package edu.unm.health.biocomp.drugdb;

import java.io.*;
import java.util.*;


import edu.unm.health.biocomp.text.*; //NameList,Name

/**	Container for DrugDBActivity list.
*/

public class ActivityList extends HashMap<Integer,DrugDBActivity>
{
  private DrugDBQuery query;
  public void setQuery(DrugDBQuery _query) { this.query = _query; }
  public DrugDBQuery getQuery() { return this.query; }

  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DrugDBActivity> getAllSortedByID()
  {
    ArrayList<DrugDBActivity> acts = new ArrayList<DrugDBActivity>(this.values());
    Collections.sort(acts);
    return acts;
  }
  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DrugDBActivity> getAllSortedByRelevance()
  {
    ArrayList<DrugDBActivity> acts = new ArrayList<DrugDBActivity>(this.values());
    Collections.sort(acts,ByRelevance);
    return acts;
  }

  /////////////////////////////////////////////////////////////////////////////
  // - MOA activies more relevant, better match.
  //
  public static Comparator<DrugDBActivity> ByRelevance = new Comparator<DrugDBActivity>()  {
    public int compare(DrugDBActivity aA,DrugDBActivity aB)
    {
      if (aA.getMoa()==1 && aB.getMoa()!=1) return -1;
      else if (aA.getMoa()!=1 && aB.getMoa()==1) return 1;
      else if (aA.getMoaType()!=null && aB.getMoaType()==null) return -1;
      else if (aA.getMoaType()==null && aB.getMoaType()!=null) return 1;
      else return aA.getTarget().getName().compareToIgnoreCase(aB.getTarget().getName());
    }
  };
}
