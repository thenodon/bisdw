/*
#
# Copyright (C) 2010-2011 Anders Håål, Ingenjorsbyn AB
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
 */

package com.ingby.socbox.bisdw;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.quartz.CronExpression;
import org.quartz.Trigger;
import org.xml.sax.SAXException;

import com.ingby.socbox.bisdw.xsd.bisdw.XMLBisdw;
import com.ingby.socbox.bisdw.xsd.bisdw.XMLEtlconfig;
import com.ingby.socbox.bisdw.xsd.bisdw.XMLEtljob;
import com.ingby.socbox.bisdw.xsd.properties.XMLProperties;
import com.ingby.socbox.bisdw.xsd.properties.XMLProperty;


public class ConfigurationManager {

	public enum XMLCONFIG  { 
		BISDW { 
			public String toString() {
				return "BISDW";
			}
			public String xml() {
				return "bisdw.xml";
			}
			public String xsd() {
				return "bisdw.xsd";
			}
			public String instance() {
				return "com.ingby.socbox.bisdw.xsd.bisdw";
			}
		}, PROPERTIES { 
			public String toString() {
				return "PROPERTIES";
			}
			public String xml() {
				return "properties.xml";
			}
			public String xsd() {
				return "properties.xsd";
			}
			public String instance() {
				return "com.ingby.socbox.bisdw.xsd.properties";
			}
		};

		public abstract String xml();
		public abstract String xsd();
		public abstract String instance();

	}

	static final Logger  LOGGER = Logger.getLogger(ConfigurationManager.class);

	private static ConfigurationManager configMgr = null;
	private Map<String,Object> cache = new HashMap<String,Object>();	
	private Map<String,ETLJob> etljobmap = new HashMap<String,ETLJob>();
	private Properties prop = new Properties();	
	private List<ETLJobConfig> schedulejobs = new ArrayList<ETLJobConfig>();


	public static void main(String[] args) throws Exception {
		CommandLineParser parser = new GnuParser();
		CommandLine line = null;
		// create the Options
		Options options = new Options();
		options.addOption( "u", "usage", false, "show usage." );
		options.addOption( "v", "verify", false, "verify all xml configuration with their xsd" );
		options.addOption( "p", "pidfile", false, "Show bisdw pid file path" );
		options.addOption( "l", "list", false, "list the bisdw configuration" );
		options.addOption( "S", "serverproperties", false, "Show server properties" );

		try {
			// parse the command line arguments
			line = parser.parse( options, args );

		} catch (org.apache.commons.cli.ParseException e) {
			System.out.println( "Command parse error:" + e.getMessage() );
			System.exit(1);
		}

		if (line.hasOption("usage")) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp( "ConfigurationMananger", options );
			System.exit(0);
		}

		ConfigurationManager.init();
		
		if (line.hasOption("verify")) {
			ConfigurationManager.verify();
		}

				
		if (line.hasOption("serverproperties")) {
			System.out.println(ConfigurationManager.getInstance().getProperties().toString());
		}


		if (line.hasOption("list")) {
			System.out.println(ConfigurationManager.getInstance().printDWConfig());

		}

		if (line.hasOption("pidfile")) {
			System.out.println("PidFile:"+ConfigurationManager.getInstance().getPidFile().getPath());	
		}
	}


	private ConfigurationManager() {}


	private void initProperties() throws Exception {
		XMLProperties propertiesconfig = (XMLProperties) getXMLConfiguration(ConfigurationManager.XMLCONFIG.PROPERTIES);

		Iterator<XMLProperty> iter = propertiesconfig.getProperty().iterator();

		while (iter.hasNext()) {
			XMLProperty propertyconfig = iter.next(); 
			prop.put(propertyconfig.getKey(),propertyconfig.getValue());	  
		}
	}



	private void initBisdw() throws Exception {
		XMLBisdw bisdwconfig  =
			(XMLBisdw) getXMLConfiguration(ConfigurationManager.XMLCONFIG.BISDW);

		Iterator<XMLEtljob> iteretljob = bisdwconfig.getEtljob().iterator();

		while (iteretljob.hasNext() ) {
			XMLEtljob etljobconfig = iteretljob.next(); 
			ETLJob etljob = new ETLJob(etljobconfig.getName());   
			etljobmap.put(etljobconfig.getName(),etljob);

			etljob.setDecscription(etljobconfig.getDesc());
			etljob.setSchedules(etljobconfig.getSchedule());

			List<XMLEtlconfig> etlconfiglist = etljobconfig.getEtlconfig();;
			
			Collections.sort(etlconfiglist, new Comparator<XMLEtlconfig>() {
			    public int compare(XMLEtlconfig conf1, XMLEtlconfig conf2) {
			        if (conf1.getOrder() == null && conf2.getOrder() == null)
			        	return 0;
			        
			        if (conf1.getOrder() == null)
			        	return -1*conf2.getOrder().intValue();
			        
			        if(conf2.getOrder() == null)
			        	return conf1.getOrder().intValue();
			        
			        return conf1.getOrder().intValue() - conf2.getOrder().intValue();
			    }
			});
			
			
			Iterator<XMLEtlconfig> iteretlconfig = etlconfiglist.iterator();

			while (iteretlconfig.hasNext()) {
				XMLEtlconfig etlconfig = iteretlconfig.next();
				
				ETLInf etl = (ETLInf) Class.forName(etlconfig.getClazz()).newInstance();
				
				etl.setETLJob(etljob);
				etl.setDecscription(etlconfig.getDesc());
				etl.setName(etlconfig.getName());
				Properties prop = new Properties();
				Iterator<com.ingby.socbox.bisdw.xsd.bisdw.XMLProperty> iter = etlconfig.getProperty().iterator();
				while (iter.hasNext()) {
					com.ingby.socbox.bisdw.xsd.bisdw.XMLProperty xmlproperty = iter.next();
					prop.put(xmlproperty.getKey(), xmlproperty.getValue());
				}
				etl.setProperties(prop);	
								
				etljob.addETL(etl);	
			}

		}

		// Create the quartz schedule triggers and store in a List
		for (Map.Entry<String, ETLJob> etljobentry: etljobmap.entrySet()) {
			ETLJob etljob = etljobentry.getValue();
			ETLJobConfig etljobconfig = new ETLJobConfig(etljob);
			Iterator<String> schedulesIter = etljob.getSchedules().iterator();
			int triggerid = 0;
			while (schedulesIter.hasNext()) {
				String schedule = schedulesIter.next();
				Trigger trigger = triggerFactory(schedule, etljob, triggerid++);
				etljobconfig.addSchedule(trigger);
			}
			schedulejobs.add(etljobconfig);
		}	
	}



	/**
	 * Creates a simple or cron trigger based on format.
	 * @param schedule
	 * @param service
	 * @param triggerid
	 * @return 
	 * @throws Exception
	 */
	private Trigger triggerFactory(String schedule, ETLJob dbcopy, int triggerid) throws Exception {

		Trigger trigger = null;

		if (isCronTrigger(schedule)) {
			// Cron schedule	
			try {
				trigger = newTrigger()
				.withIdentity(dbcopy.getName()+"Trigger-"+(triggerid), "bisdwTriggerGroup")
				.withSchedule(cronSchedule(schedule))
				.build();
			} catch (ParseException e) {
				LOGGER.error("Tigger parse error for dbcopy " + dbcopy.getName() + 
						" for schedule " + schedule);
				throw new Exception(e.getMessage());
			}

		} else {
			// Simple schedule
			try {
				trigger = newTrigger()
				.withIdentity(dbcopy.getName()+"Trigger-"+(triggerid), " bisdwTriggerGroup")
				.withSchedule(simpleSchedule().repeatSecondlyForever(calculateInterval(schedule)))
				.build();
			} catch (Exception e) {
				LOGGER.error("Tigger parse error for dbcopy " + dbcopy.getName() + 
						" for schedule " + schedule);
				throw new Exception(e.getMessage());
			}
		}
		return trigger;
	}

	/**
	 * The method calculate the interval for continues scheduling if the format
	 * is time interval and time unit, like "50 S" where the scheduling occure
	 * every 50 seconds.
	 * @param schedule the scheduling string
	 * @return the interval in seconds
	 * @throws Exception
	 */
	private int calculateInterval(String schedule) throws Exception {
		//"^[0-9]+ *[HMS]{1} *$" - check for a
		Pattern pattern = Pattern.compile("^[0-9]+ *[HMS]{1} *$");

		// Determine if there is an exact match
		Matcher matcher = pattern.matcher(schedule);
		if (matcher.matches()) {
			String withoutSpace=schedule.replaceAll(" ","");
			char time = withoutSpace.charAt(withoutSpace.length()-1);
			String value = withoutSpace.substring(0, withoutSpace.length()-1);
			LOGGER.debug("Time selected "+ time + " : " + value);
			switch (time) {
			case 'S' : return (Integer.parseInt(value)); 
			case 'M' : return (Integer.parseInt(value)*60); 
			case 'H' : return (Integer.parseInt(value)*60*60);
			}
		}
		throw new Exception();
	}


	private boolean isCronTrigger(String schedule) { 
		return CronExpression.isValidExpression(schedule);

	}


	public File initConfigDir() throws Exception {
		String path = "";
		String xmldir;

		if (System.getProperty("bisdw") != null)
			path=System.getProperty("bisdw");
		else {

			LOGGER.warn("System property bisdw must be set");
			throw new Exception("System property bisdw must be set");
		}

		if (System.getProperty("xmlconfigdir") != null) {
			xmldir=System.getProperty("xmlconfigdir");
		}else {
			xmldir="etc";
		}

		File configDir = new File(path+File.separator+xmldir);
		if (configDir.isDirectory() && configDir.canRead()) 
			return configDir;    
		else {
			LOGGER.warn("Configuration directory " + configDir.getPath() + " does not exist or is not readable.");
			throw new Exception("Configuration directory " + configDir.getPath() + " does not exist or is not readable.");
		}
	}

	private static void verify() {
		ConfigurationManager configMgr = null;

		try {
			configMgr = getInstance();
		} catch (Exception e) {
			System.out.println("Errors was found creating Configuration Manager");
			e.printStackTrace();
			System.exit(1);
		}

		try {
			configMgr.getXMLConfiguration(ConfigurationManager.XMLCONFIG.BISDW);
		} catch (Exception e) {
			System.out.println("Errors was found validating the configuration file " + 
					ConfigurationManager.XMLCONFIG.BISDW.xml());
			e.printStackTrace();
			System.exit(1);
		}

		try {
			configMgr.getXMLConfiguration(ConfigurationManager.XMLCONFIG.PROPERTIES);
		} catch (Exception e) {
			System.out.println("Errors was found validating the configuration file " + 
					ConfigurationManager.XMLCONFIG.PROPERTIES.xml());
			e.printStackTrace();
			System.exit(1);
		}
		System.exit(0);
	}



	public Object getXMLConfiguration(XMLCONFIG config) throws Exception {
		Object obj = null;
		if (config == XMLCONFIG.BISDW) {
			obj = getXMLConfig(XMLCONFIG.BISDW.xml(),
					XMLCONFIG.BISDW.xsd(),
					XMLCONFIG.BISDW.instance());
		}
		else if (config == XMLCONFIG.PROPERTIES) {
			obj = getXMLConfig(XMLCONFIG.PROPERTIES.xml(),
					XMLCONFIG.PROPERTIES.xsd(),
					XMLCONFIG.PROPERTIES.instance());
		}

		return obj;
	}


	synchronized private Object getXMLConfig(String xmlName, String xsdName, String instanceName) throws Exception {
		Object xmlobj = null;

		xmlobj = cache.get(xmlName);
		if (xmlobj == null) {
			File configfile = new File(initConfigDir(),xmlName);
			JAXBContext jc;
			try {
				jc = JAXBContext.newInstance(instanceName);
			} catch (JAXBException e) {
				LOGGER.error("Could not get JAXB context from class");
				throw new Exception(e.getMessage());
			}
			SchemaFactory sf = SchemaFactory.newInstance(
					javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema schema = null;
			try {
				schema = sf.newSchema(new File(Thread.currentThread().getContextClassLoader().getResource(xsdName).getFile()));

				//schema = sf.newSchema(new File(initConfigDir(),"xsd"+File.separatorChar+xsdName));
			} catch (SAXException e) {
				LOGGER.error("Could not vaildate xml file " + xmlName + " with xsd file " +
						xsdName + ":" + e.getMessage());
				throw new Exception(e.getMessage());
			}

			Unmarshaller u = null;
			try {
				u = jc.createUnmarshaller();
			} catch (JAXBException e) {
				LOGGER.error("Could not create an unmarshaller for for context");
				throw new Exception(e);
			}
			u.setSchema(schema);

			try {
				xmlobj =  u.unmarshal(configfile);
			} catch (JAXBException e) {
				LOGGER.error("Could not unmarshall the file " + xmlName +":" + e);
				throw new Exception(e);
			}
			cache.put(xmlName, xmlobj);
			LOGGER.debug("Create new object for xml file " + xmlName + " and store in cache");
		}
		return xmlobj;
	}


	synchronized public static void init() throws Exception{
		if (configMgr == null ) {
			configMgr = new ConfigurationManager();
			try {
				// Init all data structures. 
				configMgr.initProperties();
				configMgr.initBisdw();

				// Verify all structures
				if (configMgr.getPidFile().canWrite()) {
					throw new Exception("Can not write to pid file " + configMgr.getPidFile());
				}
			} catch (Exception e) {
				LOGGER.error("Configuration Manager initzialization failed with " + e);
				throw e;
			}
		}
	}


	/*
	 * Public Configuration methods
	 */
	synchronized public static ConfigurationManager getInstance() {
		if (configMgr == null ) {
			return null;
		}
		return configMgr;
		
	}


	public Properties getProperties() {
		return prop;
	}


	public Map<String, ETLJob> getDBCopyConfig() {
		return etljobmap;
	}


	public List<ETLJobConfig> getScheduleJobConfigs() {
		return schedulejobs;
	}

	public  File getPidFile() {
		return new File(prop.getProperty("pidfile","/var/tmp/bisdw.pid"));
	}



	

	public String printDWConfig() {
		StringBuffer str = new StringBuffer();
		for (Map.Entry<String, ETLJob> etljobentry: etljobmap.entrySet()) {
			ETLJob etljob = etljobentry.getValue();
			str.append("ETL Job Name: ").append(etljob.getName()).append("\n");
			str.append("  Desc: ").append(etljob.getDecscription()).append("\n");

			for (Map.Entry<String, ETLInf> etlentry: etljob.getETLs().entrySet()) {
				ETLInf etl = etlentry.getValue();

				str.append("  ETL Name: ").append(etl.getName()).append("\n");
				str.append("      Desc: ").append(etl.getDecscription()).append("\n");
				str.append("      Class: ").append(etl.getClass()).append("\n");
				str.append("      Property: ").append("\n");
				Iterator<Object> iter = etl.getProperties().keySet().iterator();
				while (iter.hasNext()) {
					String key = (String) iter.next();
					str.append("        ").append(key).append("=").append(etl.getProperties().get(key)).append("\n");
				}
			}
		}
		return str.toString();
	}

}