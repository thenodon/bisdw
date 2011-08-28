package com.ingby.socbox.bisdw;

import java.util.ArrayList;
import java.util.List;

import org.quartz.Trigger;


public class ETLJobConfig  {

	private ETLJob etljob;
	private List<Trigger> triggerList = new ArrayList<Trigger>();
	
	public ETLJobConfig(ETLJob etljob) {
		this.etljob = etljob;
	}

	public void addSchedule(Trigger trigger) {
		triggerList.add(trigger);
	}
	
	public ETLJob getETLJob() {
		return etljob;
	}
	
	public List<Trigger> getSchedules(){
		return triggerList;
	}


}
