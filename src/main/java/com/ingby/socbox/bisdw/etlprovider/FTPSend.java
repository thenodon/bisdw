package com.ingby.socbox.bisdw.etlprovider;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;


import org.apache.log4j.Logger;


import com.ingby.socbox.bisdw.ETLInf;
import com.ingby.socbox.bisdw.ETLRunException;
import com.ingby.socbox.bisdw.FTPManager;
import com.ingby.socbox.bisdw.FTPManagerException;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.core.TimerContext;

public class FTPSend implements ETLInf {

	static final Logger LOGGER = Logger.getLogger(FTPSend.class);
	private String name = null;
	private String desc;
	private Properties properties;
	

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
		return this.desc;
	}

	@Override
	public void setProperties(Properties properties) {
		this.properties = properties;
		
	}

	@Override
	public Properties getProperties() {
		return properties;
	}
	
	
	@Override
	public void runETL() throws ETLRunException {
		final Timer timer = Metrics.newTimer(FTPSend.class, 
				this.name, TimeUnit.MILLISECONDS, TimeUnit.SECONDS);
		final TimerContext context = timer.time();
	
		FTPManager ftp = new FTPManager(properties);
	    
		try {
			
			ftp.init();
			
            ftp.putAllDirectory();
            
		} catch (IOException e) {
			LOGGER.error("Execution failed for script " + this.name,e);
			throw new ETLRunException(e);
		} catch(FTPManagerException e) {
			LOGGER.error("Execution failed for script " + this.name,e);
			throw new ETLRunException(e);
		}finally {
			ftp.disconnect();
			long duration = context.stop()/1000000;
			
			if (LOGGER.isInfoEnabled()) {
				LOGGER.info(this.name +" total execution time: " + duration + " ms");
			}
		}
	}

}
