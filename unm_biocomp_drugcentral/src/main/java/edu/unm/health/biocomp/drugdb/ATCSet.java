package edu.unm.health.biocomp.drugdb;

import java.util.*;


/**	Represents a set of unique ATCs, one or more trees rooted by level-1 codes.
*/
public class ATCSet
{
  private ArrayList<ATC> atcs;

  public ATCSet() //default constructor
  {
    this.atcs = new ArrayList<ATC>();
  }

  public void addATC(ATC _atc)
  {
    boolean exists = false;
    for (ATC atc: this.atcs)
      if (atc.equals(_atc)) { exists=true; break; }
    if (!exists) this.atcs.add(_atc);
  }

  /////////////////////////////////////////////////////////////////////////////
}

