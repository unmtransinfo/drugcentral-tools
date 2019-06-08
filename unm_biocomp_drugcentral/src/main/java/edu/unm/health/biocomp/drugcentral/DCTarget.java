package edu.unm.health.biocomp.drugcentral;

import java.io.*;
import java.util.*;

/**	Represents one DC target, defined by ID, and associated data.
	Each target comprised of one or more components, typically corresponding
	to one protein molecule.  In compound context, data relative to that 
	compound may exist, such as MOA and other activity data.
*/

public class DCTarget
	implements Comparable<Object>
{
  private Integer id; //DC ID
  private String name;
  private HashMap<Integer,DCTargetComponent> components;
  private String tgtclass;

  private DCTarget() {} //disallow default constructor

  public DCTarget(Integer _id)
  {
    this.id = _id;
    this.components = new HashMap<Integer,DCTargetComponent>();
  }

  public Integer getID() { return this.id; }

  public void addComponent(DCTargetComponent _tc) { this.components.put(_tc.getID(),_tc); }
  public DCTargetComponent getComponent(Integer _tcid) { return this.components.get(_tcid); }
  public boolean hasComponent(Integer _tcid) { return this.components.containsKey(_tcid); }
  public Collection<DCTargetComponent> getComponents() { return this.components.values(); }
  public int componentCount() { return this.components.size(); }

  /**	Return only OR arbitrary component. */
  public DCTargetComponent getComponent()
  {
    for (Integer i: components.keySet())
      return this.getComponent(i);
    return null;
  }

  public void setName(String _name) { this.name = _name; }
  public String getName() { return this.name; }
  public void setTargetclass(String _tgtclass) { this.tgtclass = _tgtclass; }
  public String getTargetclass() { return this.tgtclass; }

  /**	Assume all components same organism. */
  public String getOrganism()
  {
    for (DCTargetComponent tgtc: this.getComponents())
      return tgtc.getOrganism();
    return null;
  }

  /////////////////////////////////////////////////////////////////////////////
  public int compareTo(Object o)        //native-order (by 1st name ascii)
  {
//    if (this.getCompoundMoa()!=null && ((DCTarget)o).getCompoundMoa()==null) return -1;
//    else if (this.getCompoundMoa()==null && ((DCTarget)o).getCompoundMoa()!=null) return 1;
//    else
      return ((String)(this.getName())).compareToIgnoreCase((String)(((DCTarget)o).getName()));
  }
}
