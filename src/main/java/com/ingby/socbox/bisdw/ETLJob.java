package com.ingby.socbox.bisdw;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ETLJob {

	private Map<String,ETLInf> etls = new LinkedHashMap<String,ETLInf>();
	private List<String> schedulelist;
	private String name;
	private String desc;
	
	public ETLJob(String name) {
		this.name = name;
	}

	public void setDecscription(String desc) {
		this.desc = desc;
	}

	public void setSchedules(List<String> schedule) {
		this.schedulelist = schedule;
		
	}

	public void addETL(ETLInf etl) {
		etls.put(etl.getName(),etl);
	}

	public List<String>  getSchedules() {
		// TODO Auto-generated method stub
		return schedulelist;
	}

	public String getName() {
		return name;
	}

	public String getDecscription() {
		return desc;
	}

	public Map<String, ETLInf> getETLs() {
		return etls;
	}

}
