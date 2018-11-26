package edu.unm.health.biocomp.drugdb;

import java.io.*;


/**	Represents one ATC.

	L1_CODE:	C	CARDIOVASCULAR SYSTEM
	L2_CODE:	C02	ANTIHYPERTENSIVES                                                                 
	L3_CODE:	C02K	OTHER ANTIHYPERTENSIVES
	L4_CODE:	C02KC	MAO inhibitors
	L5_CODE:	C02KC01

	(1st level, anatomical main group)
	(2nd level, therapeutic subgroup)
	(3rd level, pharmacological subgroup)
	(4th level, chemical subgroup)
	(5th level, chemical substance)

	ref: http://www.whocc.no/atc/structure_and_principles/

	L1-L4 useful as classifiers.
	L5 not used since other compound identifiers better.
*/
public class ATC
	implements Comparable
{
  private String[] code; //4-levels, specified 1-4, implemented 0-3
  private String[] name; //4-levels, specified 1-4, implemented 0-3

  public ATC() //default constructor
  {
    this.code = new String[4];
    this.name = new String[4];
  }

  public String getCode(int _lev) { return (_lev<5)?this.code[_lev-1]:null; }
  public String getName(int _lev) { return (_lev<5)?this.name[_lev-1]:null; }
  public void setCode(int _lev,String _code) { if (_lev<5) this.code[_lev-1]=_code; }
  public void setName(int _lev,String _name) { if (_lev<5) this.name[_lev-1]=_name; }

  public boolean sameLevel(ATC _atc, int _lev)
  {
    return (_lev>0 && _lev<5 && this.code[_lev-1].equals(_atc.code[_lev-1]));
  }

  /////////////////////////////////////////////////////////////////////////////
  public boolean equals(Object o)
  {
    return (
	this.code[0].equals(((ATC)o).code[0]) &&
	this.code[1].equals(((ATC)o).code[1]) &&
	this.code[2].equals(((ATC)o).code[2]) &&
	this.code[3].equals(((ATC)o).code[3])
	);
  }
  /////////////////////////////////////////////////////////////////////////////
  public int compareTo(Object o)
  {
    return (
	(this.code[0].compareTo(((ATC)o).code[0])!=0)? this.code[0].compareTo(((ATC)o).code[0]) :
	(this.code[1].compareTo(((ATC)o).code[1])!=0)? this.code[1].compareTo(((ATC)o).code[1]) :
	(this.code[2].compareTo(((ATC)o).code[2])!=0)? this.code[2].compareTo(((ATC)o).code[2]) :
	this.code[3].compareTo(((ATC)o).code[3])
	);
  }
}

