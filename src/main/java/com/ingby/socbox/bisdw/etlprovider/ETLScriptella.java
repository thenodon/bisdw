package com.ingby.socbox.bisdw.etlprovider;

import java.io.File;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.ingby.socbox.bisdw.ConfigurationManager;
import com.ingby.socbox.bisdw.ETLInf;
import com.ingby.socbox.bisdw.ETLRunException;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.core.TimerContext;

import scriptella.execution.EtlExecutor;
import scriptella.execution.EtlExecutorException;
import scriptella.execution.ExecutionStatistics;


/**
 * The class execute the Scriptella script according to the specification in the
 * bisdw.xml file. The Scriptella file is re-read every time the job is 
 * executed. This means that a new EtlExecutor is created at each schedule.
 */
public class ETLScriptella implements ETLInf {
	static final Logger LOGGER = Logger.getLogger(ETLScriptella.class);
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
	public void runETL() throws ETLRunException {
		final Timer timer = Metrics.newTimer(ETLScriptella.class, 
				this.name, TimeUnit.MILLISECONDS, TimeUnit.SECONDS);
		final TimerContext context = timer.time();
		
		ExecutionStatistics stats = null;
		EtlExecutor etlExec;
		try {
			etlExec = EtlExecutor.newExecutor(new File(ConfigurationManager.getInstance().initConfigDir(),
					properties.getProperty("configFile")));
		} catch (Exception e) {
			throw new ETLRunException(e);	
		} 
		
		etlExec.setJmxEnabled(true);

		try {
			stats = etlExec.execute();
		} catch (EtlExecutorException e) {
			LOGGER.error("Execution failed for script" + this.name,e);
			throw new ETLRunException(e);
		} finally {
			long duration = context.stop()/1000000;
			if (LOGGER.isInfoEnabled()) {
				Collection<ExecutionStatistics.ElementInfo> infoColl = stats.getElements();
				for (ExecutionStatistics.ElementInfo info : infoColl) {
					StringBuffer strbuf = new StringBuffer();
					strbuf.append("{");
					strbuf.append("\"id\":\"").append(this.name).append(info.getId()).append("\" , ");
					strbuf.append("\"worktime_ms:\"").append((info.getWorkingTime()/1000000)).append("\" , ");
					strbuf.append("\"statement_count:\"").append(info.getStatementsCount()).append("\" , ");
					strbuf.append("\"success_count:\"").append(info.getSuccessfulExecutionCount()).append("\" , ");
					strbuf.append("\"failed_count:\"").append(info.getFailedExecutionCount()).append("\" , ");
					strbuf.append("\"throughput:\"").append(info.getThroughput());
					strbuf.append("}");
					LOGGER.info(strbuf.toString());
				}
			}
			
			if (LOGGER.isInfoEnabled()) {
				LOGGER.info(this.name +" total execution time: " + duration + " ms");
			}
		}
		
		
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
