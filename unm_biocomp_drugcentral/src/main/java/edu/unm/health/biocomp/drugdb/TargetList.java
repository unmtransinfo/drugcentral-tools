package edu.unm.health.biocomp.drugdb;

import java.io.*;
import java.util.*;


import edu.unm.health.biocomp.text.*; //NameList,Name

/**	Container for DrugDBTarget list.
*/

public class TargetList extends HashMap<Integer,DrugDBTarget>
{
  private DrugDBQuery query;
  public void setQuery(DrugDBQuery _query) { this.query = _query; }
  public DrugDBQuery getQuery() { return this.query; }

  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DrugDBTarget> getAllSortedByID()
  {
    ArrayList<DrugDBTarget> targets = new ArrayList<DrugDBTarget>(this.values());
    Collections.sort(targets);
    return targets;
  }
  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DrugDBTarget> getAllSortedByRelevance()
  {
    ArrayList<DrugDBTarget> targets = new ArrayList<DrugDBTarget>(this.values());
    Collections.sort(targets,ByRelevance);
    return targets;
  }

  /////////////////////////////////////////////////////////////////////////////
  // - MOA targets more relevant, better match.
  //
  public static Comparator<DrugDBTarget> ByRelevance = new Comparator<DrugDBTarget>()  {
    public int compare(DrugDBTarget tA,DrugDBTarget tB)
    {
//      if (tA.getCompoundMoaType()!=null && tB.getCompoundMoaType()==null) return -1;
//      else if (tA.getCompoundMoaType()==null && tB.getCompoundMoaType()!=null) return 1;
//      else
        return tA.getName().compareToIgnoreCase(tB.getName());
    }
  };
}
