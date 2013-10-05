package com.ingby.socbox.bisdw;

import java.util.Properties;

/**
 * Specify the methods that all classes must implement that can be specified in 
 * the class tag of the etlconfig of the bisdw.xml file.
 *
 */
public interface ETLInf {
	
	/**
	 * The job that the specific etl config belongs to. 
	 * @param dbcopy the job 
	 */
	//public void setETLJob(ETLJob dbcopy);
	
	
	/**
	 * The description of the etl configuration
	 * @param desc
	 */
	public void setDecscription(String desc);
	
	
	/**
	 * The name of the etl config
	 * @param name
	 */
	public void setName(String name);
	
	
	/**
	 * Get the name of the etl config
	 * @return
	 */
	public String getName();
	
	
	/**
	 * Get the name of the etl config
	 * @return
	 */
	public String getDecscription();
	
	
	/**
	 * Set the property list for the etl config. How its used is specific to
	 * the etlprovider.
	 * @param properties
	 */
	public void setProperties(Properties properties);
	
	
	/**
	 * Get the properties.
	 * @return
	 */
	public Properties getProperties();

	
	/**
	 * Execute the etlprovider
	 * @throws Exception
	 */
	public void runETL() throws ETLRunException;
	
}
