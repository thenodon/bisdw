package com.ingby.socbox.bisdw.etlprovider;

import java.io.File;
import java.util.Properties;

import com.ingby.socbox.bisdw.ConfigurationManager;
import com.ingby.socbox.bisdw.ETLInf;
import com.ingby.socbox.bisdw.ETLJob;

import scriptella.execution.EtlExecutor;


public class ETLScriptella implements ETLInf {

	private String name = null;
	private String desc;
	private Properties properties;
	
	public ETLScriptella() {
	}

	@Override
	public void setETLJob(ETLJob dbcopy) {
		// TODO Auto-generated method stub
	}

	@Override
	public void setDecscription(String desc) {
		this.desc = desc;
		
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getDecscription() {
		return desc;
	}
	
	

	@Override
	public void runETL() throws Exception {
		
		EtlExecutor.newExecutor(new File(ConfigurationManager.getInstance().initConfigDir(),
				properties.getProperty("configFile"))).execute(); //Execute etl.xml file
				
	}

	@Override
	public void setProperties(Properties properties) {
		this.properties = properties;
		
	}

	@Override
	public Properties getProperties() {
		return properties;
	}

	
}
