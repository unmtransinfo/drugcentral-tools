package edu.unm.health.biocomp.drugcentral;

import java.io.*;
import java.util.*;


import edu.unm.health.biocomp.text.*; //NameList,Name

/**	Container for DCTarget list.
*/

public class TargetList extends HashMap<Integer,DCTarget>
{
  private DCQuery query;
  public void setQuery(DCQuery _query) { this.query = _query; }
  public DCQuery getQuery() { return this.query; }

  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DCTarget> getAllSortedByID()
  {
    ArrayList<DCTarget> targets = new ArrayList<DCTarget>(this.values());
    Collections.sort(targets);
    return targets;
  }
  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DCTarget> getAllSortedByRelevance()
  {
    ArrayList<DCTarget> targets = new ArrayList<DCTarget>(this.values());
    Collections.sort(targets,ByRelevance);
    return targets;
  }

  /////////////////////////////////////////////////////////////////////////////
  // - MOA targets more relevant, better match.
  //
  public static Comparator<DCTarget> ByRelevance = new Comparator<DCTarget>()  {
    public int compare(DCTarget tA,DCTarget tB)
    {
//      if (tA.getCompoundMoaType()!=null && tB.getCompoundMoaType()==null) return -1;
//      else if (tA.getCompoundMoaType()==null && tB.getCompoundMoaType()!=null) return 1;
//      else
        return tA.getName().compareToIgnoreCase(tB.getName());
    }
  };
}
