package com.ingby.socbox.bisdw;

import java.util.Properties;

public interface ETLInf {
	
	public void setETLJob(ETLJob dbcopy);
	public void setDecscription(String desc);
	public void setName(String name);
	public String getName();
	public String getDecscription();
	public void setProperties(Properties properties);
	public Properties getProperties();
	public void runETL() throws Exception;
}
