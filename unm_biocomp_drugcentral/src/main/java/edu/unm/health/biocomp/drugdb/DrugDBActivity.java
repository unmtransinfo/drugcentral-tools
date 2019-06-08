package edu.unm.health.biocomp.drugdb;

import java.io.*;
import java.util.*;

/**	Represents one DrugDB activity, an interaction between one compound
	and one target.  A compound may have zero or more activities.
	An activity has exactly one target, but multiple activities for the
	same compound may have the same target.
*/

public class DrugDBActivity
	implements Comparable<Object>
{
  private Integer id;
  private DrugDBCompound cpd;
  private DrugDBTarget tgt;
  private String relation; 
  private String type; 
  private Double value; 
  private String unit; 
  private String source; 
  private String comment; 

  private Integer ref_id; 
  private String ref_pmid; 
  private String ref_title; 
  private String ref_journal; 
  private String ref_authors; 
  private String ref_doi; 
  private String ref_url; 
  private Integer ref_year; 

  private Integer moa; //Currently per OU, only use MOA where MOA=1.
  private String moa_type; 
  private String moa_source; 

  private Integer moa_ref_id; 
  private String moa_ref_pmid; 
  private String moa_ref_title; 
  private String moa_ref_journal; 
  private String moa_ref_authors; 
  private String moa_ref_doi; 
  private String moa_ref_url; 
  private Integer moa_ref_year; 

  private DrugDBActivity() {} //disallow default constructor

  public DrugDBActivity(Integer _id)
  {
    this.id = _id;
  }

  public void setID(int _id) { this.id = _id; }
  public Integer getID() { return this.id; }

  public void setCompound(DrugDBCompound _cpd) { this.cpd = _cpd; }
  public DrugDBCompound getCompound() { return this.cpd; }
  public void setTarget(DrugDBTarget _tgt) { this.tgt = _tgt; }
  public DrugDBTarget getTarget() { return this.tgt; }

  public void setRelation(String _relation) { this.relation = _relation; }
  public String getRelation() { return this.relation; }

  public void setType(String _type) { this.type = _type; }
  public String getType() { return this.type; }

  public void setUnit(String _unit) { this.unit = _unit; }
  public String getUnit() { return this.unit; }

  public void setComment(String _comment) { this.comment = _comment; }
  public String getComment() { return this.comment; }

  public void setSource(String _source) { this.source = _source; }
  public String getSource() { return this.source; }

  public void setValue(double _value) { this.value = _value; }
  public Double getValue() { return this.value; }

  public void setRefID(int _ref_id) { this.ref_id = _ref_id; }
  public Integer getRefID() { return this.ref_id; }
  public void setRefPMID(String _ref_pmid) { this.ref_pmid = _ref_pmid; }
  public String getRefPMID() { return this.ref_pmid; }
  public void setRefTitle(String _ref_title) { this.ref_title = _ref_title; }
  public String getRefTitle() { return this.ref_title; }
  public void setRefJournal(String _ref_journal) { this.ref_journal = _ref_journal; }
  public String getRefJournal() { return this.ref_journal; }
  public void setRefAuthors(String _ref_authors) { this.ref_authors = _ref_authors; }
  public String getRefAuthors() { return this.ref_authors; }
  public void setRefDOI(String _ref_doi) { this.ref_doi = _ref_doi; }
  public String getRefDOI() { return this.ref_doi; }
  public void setRefURL(String _ref_url) { this.ref_url = _ref_url; }
  public String getRefURL() { return this.ref_url; }
  public void setRefYear(int _ref_year) { this.ref_year = _ref_year; }
  public Integer getRefYear() { return this.ref_year; }

  public void setMoa(Integer _moa) { this.moa = _moa; }
  public Integer getMoa() { return this.moa; }
  public void setMoaType(String _moa_type) { this.moa_type = _moa_type; }
  public String getMoaType() { return this.moa_type; }
  public void setMoaSource(String _moa_source) { this.moa_source = _moa_source; }
  public String getMoaSource() { return this.moa_source; }
  public void setMoaRefID(int _moa_ref_id) { this.moa_ref_id = _moa_ref_id; }
  public Integer getMoaRefID() { return this.moa_ref_id; }
  public void setMoaRefPMID(String _moa_ref_pmid) { this.moa_ref_pmid = _moa_ref_pmid; }
  public String getMoaRefPMID() { return this.moa_ref_pmid; }
  public void setMoaRefTitle(String _moa_ref_title) { this.moa_ref_title = _moa_ref_title; }
  public String getMoaRefTitle() { return this.moa_ref_title; }
  public void setMoaRefJournal(String _moa_ref_journal) { this.moa_ref_journal = _moa_ref_journal; }
  public String getMoaRefJournal() { return this.moa_ref_journal; }
  public void setMoaRefAuthors(String _moa_ref_authors) { this.moa_ref_authors = _moa_ref_authors; }
  public String getMoaRefAuthors() { return this.moa_ref_authors; }
  public void setMoaRefDOI(String _moa_ref_doi) { this.moa_ref_doi = _moa_ref_doi; }
  public String getMoaRefDOI() { return this.moa_ref_doi; }
  public void setMoaRefURL(String _moa_ref_url) { this.moa_ref_url = _moa_ref_url; }
  public String getMoaRefURL() { return this.moa_ref_url; }
  public void setMoaRefYear(int _moa_ref_year) { this.moa_ref_year = _moa_ref_year; }
  public Integer getMoaRefYear() { return this.moa_ref_year; }


  /////////////////////////////////////////////////////////////////////////////
  public int compareTo(Object o)
  {
    if (this.getMoa()==1 && ((DrugDBActivity)o).getMoa()!=1) return -1;
    else if (this.getMoa()!=1 && ((DrugDBActivity)o).getMoa()==1) return 1;
    else if (this.getMoaType()!=null && ((DrugDBActivity)o).getMoaType()==null) return -1;
    else if (this.getMoaType()==null && ((DrugDBActivity)o).getMoaType()!=null) return 1;
    else return ((String)(this.getTarget().getName())).compareToIgnoreCase((String)(((DrugDBActivity)o).getTarget().getName()));
  }
}
