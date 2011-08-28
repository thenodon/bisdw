package com.ingby.socbox.bisdw;

import java.util.Map;

import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class ETLJobExecute implements Job {

	static Logger  logger = Logger.getLogger(ETLJobExecute.class);

	private ETLJob etljob;
	
	
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {

		
		JobDataMap dataMap = context.getJobDetail().getJobDataMap();

		etljob = (ETLJob) dataMap.get("etljob");
		
		logger.debug("Executing Service: " + etljob.getName());
        					
		// Open the connection specific for the service
		for (Map.Entry<String, ETLInf> etlentry: etljob.getETLs().entrySet()){
			ETLInf etl = etlentry.getValue();
			try {
				etl.runETL();
			} catch (Exception e) {
				logger.error("ETL execution failed for etl " + etl.getName() + " with exception " + e);
			}
		}
	}
	
}


