package edu.unm.health.biocomp.drugdb;

import java.io.*;
import java.util.*;

/**	Represents one DrugDB ingredient, a chemical substance contained
	in a product, and containing one compound.
*/

public class DrugDBIngredient
	implements Comparable<Object>
{
  private Integer id; //DrugDB ID

  //ACTIVE_INGREDIENT:
  private String active_moiety_unii; //ACTIVE_MOIETY_UNII
  private String active_moiety_name; //ACTIVE_MOIETY_NAME
  private String substance_unii; //SUBSTANCE_UNII
  private String substance_name; //SUBSTANCE_NAME
  private String unit;
  private String quantity;
  private String quantity_denom_unit;
  private String quantity_denom_value;

  private DrugDBCompound cpd;

  private DrugDBIngredient() {} //disallow default constructor

  public DrugDBIngredient(Integer _id)
  {
    this.id = _id;
    this.cpd = null;
  }

  public Integer getID() { return this.id; }

  public void setCompound(DrugDBCompound _cpd) { this.cpd = _cpd; }
  public DrugDBCompound getCompound() { return this.cpd; }

  public void setActivemoietyUnii(String _active_moiety_unii) { this.active_moiety_unii = _active_moiety_unii; }
  public String getActivemoietyUnii() { return this.active_moiety_unii; }
  public void setActivemoietyName(String _active_moiety_name) { this.active_moiety_name = _active_moiety_name; }
  public String getActivemoietyName() { return this.active_moiety_name; }
  public void setSubstanceUnii(String _substance_unii) { this.substance_unii = _substance_unii; }
  public String getSubstanceUnii() { return this.substance_unii; }
  public void setSubstanceName(String _substance_name) { this.substance_name = _substance_name; }
  public String getSubstanceName() { return this.substance_name; }
  public void setUnit(String _unit) { this.unit = _unit; }
  public String getUnit() { return this.unit; }
  public void setQuantity(String _quantity) { this.quantity= _quantity; }
  public String getQuantity() { return this.quantity; }
  public void setQuantityUnit(String _quantity_denom_unit) { this.quantity_denom_unit = _quantity_denom_unit; }
  public String getQuantityUnit() { return this.quantity_denom_unit; }
  public void setQuantityValue(String _quantity_denom_value) { this.quantity_denom_value = _quantity_denom_value; }
  public String getQuantityValue() { return this.quantity_denom_value; }

  /////////////////////////////////////////////////////////////////////////////
  public int compareTo(Object o)        //native-order (by id)
  {
    return (id>((DrugDBIngredient)o).id ? 1 : (id<((DrugDBIngredient)o).id ? -1 : 0));
  }
}

