package edu.unm.health.biocomp.drugcentral;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import edu.unm.health.biocomp.text.*; //NameList,Name

/**	Represents one DC product, defined by ID, comprised of 1+ 
	ingredients, and associated data.

	In this version all ingredients assumed to be active.
*/

public class DCProduct
	implements Comparable<Object>
{
  private HashMap<Integer,DCIngredient> ingredients;
  private Integer n_ingredient; //Use if ingredients not loaded.
  private Integer id; //DC ID
  private String ndc; //NDC_PRODUCT_CODE
  private String form;
  private String route;
  private String product_name;
  private String generic_name;
  private String marketing_status;

  private DCProduct() {} //disallow default constructor

  public DCProduct(Integer _id)
  {
    this.id = _id;
    ingredients = new HashMap<Integer,DCIngredient>();
  }

  public Integer getID() { return this.id; }
  public void setNdc(String _ndc) { this.ndc = _ndc; }
  public String getNdc() { return this.ndc; }
  public void setForm(String _form) { this.form = _form; }
  public String getForm() { return this.form; }
  public void setRoute(String _route) { this.route = _route; }
  public String getRoute() { return this.route; }
  public void setProductname(String _product_name) { this.product_name = _product_name; }
  public String getProductname() { return this.product_name; }
  public void setGenericname(String _generic_name) { this.generic_name = _generic_name; }
  public String getGenericname() { return this.generic_name; }
  public void setStatus(String _marketing_status) { this.marketing_status = _marketing_status; }
  public String getStatus() { return this.marketing_status; }

  public void addIngredient(DCIngredient _igt)
  {
    this.ingredients.put(_igt.getID(),_igt);
    this.n_ingredient = Math.max(this.n_ingredient,this.ingredientCount());
  }
  public DCIngredient getIngredient(Integer _iid) { return this.ingredients.get(_iid); }
  public boolean hasIngredient(Integer _iid) { return this.ingredients.containsKey(_iid); }
  public Collection<DCIngredient> getIngredients() { return this.ingredients.values(); }
  public int ingredientCount() { return this.ingredients.size(); }
  public int getIngredientCount() { return this.n_ingredient; }
  public void setIngredientCount(int _n) { this.n_ingredient=_n; }

  /**	Multi-component compounds possible. */
  public String getMixtureSmiles()
  {
    HashSet<String> partsmis = new HashSet<String>();
    for (DCIngredient ingr: this.getIngredients())
    {
      for (String partsmi: ingr.getCompound().getSmiles().replaceFirst("\\s.*$","").split("\\."))
        partsmis.add(partsmi);
    }
    int i=0;
    String smi="";
    for (String partsmi: partsmis)
      smi+=((((i++)>0)?".":"")+partsmi);
    return smi;
  }
  public boolean hasLargeCompound()
  {
    boolean b=false;
    for (DCIngredient ingr: this.getIngredients())
      b |= ingr.getCompound().isLarge();
    return b;
  }

  /////////////////////////////////////////////////////////////////////////////
  public int compareTo(Object o)        //native-order (by id)
  {
    return (id>((DCProduct)o).id ? 1 : (id<((DCProduct)o).id ? -1 : 0));
  }
}

