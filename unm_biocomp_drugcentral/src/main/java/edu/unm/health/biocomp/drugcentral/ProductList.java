package edu.unm.health.biocomp.drugcentral;

import java.io.*;
import java.util.*;


import edu.unm.health.biocomp.text.*; //NameList,Name

/**	Container for DCProduct hitlist, result of DCQuery.
*/

public class ProductList extends HashMap<Integer,DCProduct>
{
  private DCQuery query;
  public void setQuery(DCQuery _query) { this.query = _query; }
  public DCQuery getQuery() { return this.query; }

  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DCProduct> getAllSortedByID()
  {
    ArrayList<DCProduct> products = new ArrayList<DCProduct>(this.values());
    Collections.sort(products);
    return products;
  }
  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DCProduct> getAllSortedByRelevance()
  {
    ArrayList<DCProduct> products = new ArrayList<DCProduct>(this.values());
    Collections.sort(products,ByRelevance);
    return products;
  }

  /////////////////////////////////////////////////////////////////////////////
  // - Single-ingredient products usually more relevant, better match.
  // - Shorter product names normally more relevant, "Aspirin" vs. "Adult Low Strength Aspirin"
  //
  public static Comparator<DCProduct> ByRelevance = new Comparator<DCProduct>()  {
    public int compare(DCProduct pA,DCProduct pB)
    {
      return
	(pA.ingredientCount()>pB.ingredientCount()) ? 1 :
	(pA.ingredientCount()<pB.ingredientCount()) ? -1 :

	((pA.getProductname().length()>pB.getProductname().length()) ? 1 :
	(pA.getProductname().length()<pB.getProductname().length()) ? -1 :
	0

	);
    }
  };
}
