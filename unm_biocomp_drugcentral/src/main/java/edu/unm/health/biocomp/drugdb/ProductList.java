package edu.unm.health.biocomp.drugdb;

import java.io.*;
import java.util.*;


import edu.unm.health.biocomp.text.*; //NameList,Name

/**	Container for DrugDBProduct hitlist, result of DrugDBQuery.
*/

public class ProductList extends HashMap<Integer,DrugDBProduct>
{
  private DrugDBQuery query;
  public void setQuery(DrugDBQuery _query) { this.query = _query; }
  public DrugDBQuery getQuery() { return this.query; }

  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DrugDBProduct> getAllSortedByID()
  {
    ArrayList<DrugDBProduct> products = new ArrayList<DrugDBProduct>(this.values());
    Collections.sort(products);
    return products;
  }
  /////////////////////////////////////////////////////////////////////////////
  public ArrayList<DrugDBProduct> getAllSortedByRelevance()
  {
    ArrayList<DrugDBProduct> products = new ArrayList<DrugDBProduct>(this.values());
    Collections.sort(products,ByRelevance);
    return products;
  }

  /////////////////////////////////////////////////////////////////////////////
  // - Single-ingredient products usually more relevant, better match.
  // - Shorter product names normally more relevant, "Aspirin" vs. "Adult Low Strength Aspirin"
  //
  public static Comparator<DrugDBProduct> ByRelevance = new Comparator<DrugDBProduct>()  {
    public int compare(DrugDBProduct pA,DrugDBProduct pB)
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
