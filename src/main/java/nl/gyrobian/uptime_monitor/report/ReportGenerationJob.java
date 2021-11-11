package nl.gyrobian.uptime_monitor.report;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.IOException;

/**
 * Simple job that when executed, generates a report.
 */
public class ReportGenerationJob implements Job {
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		ReportGenerator generator = (ReportGenerator) context.getJobDetail().getJobDataMap().get("generator");
		try {
			generator.generate();
		} catch (IOException e) {
			throw new JobExecutionException(e);
		}
	}
}
