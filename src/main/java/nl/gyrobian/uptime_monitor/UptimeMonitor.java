package nl.gyrobian.uptime_monitor;

import nl.gyrobian.uptime_monitor.command.MeasureSubcommand;
import nl.gyrobian.uptime_monitor.data.FocusInterval;
import nl.gyrobian.uptime_monitor.report.Format;
import nl.gyrobian.uptime_monitor.report.Interval;
import nl.gyrobian.uptime_monitor.report.ReportGenerationJob;
import nl.gyrobian.uptime_monitor.report.ReportGenerator;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@CommandLine.Command(
		name = "totalUptime-monitor",
		description = "Monitors and records the totalUptime of sites.",
		subcommands = {
				MeasureSubcommand.class
		}
)
public class UptimeMonitor implements Callable<Integer> {
	static {
		// Disable apache logging which would otherwise show when using PDFBox.
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
	}

	@CommandLine.Option(names = {"-c", "--config"}, description = "Path to the configuration file to use.", defaultValue = "./config.yaml")
	String configPath;

	@CommandLine.Option(names = {"--no-cli"}, description = "Start the application without CLI support. It will run until it is forcibly shut down.", defaultValue = "false")
	boolean ignoreCli;

	@Override
	public Integer call() throws Exception {
		var config = Config.load(Path.of(this.configPath));
		if (config == null) {
			System.err.println("Could not load configuration.");
			return 1;
		}
		long maxFileSize = parseSize(config.getMaxFileSize());
		ScheduledExecutorService executor = Executors.newScheduledThreadPool(config.getSites().size());
		List<SiteMonitor> monitors = initializeSiteMonitors(config, maxFileSize, executor);
		if (monitors.isEmpty()) {
			System.err.println("No site monitors were initialized. Please add some and run again.");
			return 1;
		}
		System.out.println("Started monitoring all configured sites.");
		Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
		if (config.getReports() != null && !config.getReports().isEmpty()) {
			initializeReportGenerators(config.getReports(), scheduler);
		}
		addShutdownHook(executor, monitors, scheduler);
		if (!ignoreCli) {
			return this.runCLI(executor, monitors);
		} else {
			while (!executor.isTerminated()) {
				Thread.sleep(3000);
			}
			return 0;
		}
	}

	private List<SiteMonitor> initializeSiteMonitors(Config config, long maxFileSize, ScheduledExecutorService executor) {
		List<SiteMonitor> monitors = new ArrayList<>(config.getSites().size());
		for (var site : config.getSites()) {
			System.out.printf("Initializing monitoring of site \"%s\" every %d seconds.\n", site.getName(), site.getInterval());
			try {
				var monitor = new SiteMonitor(site, maxFileSize);
				executor.scheduleAtFixedRate(monitor::monitor, 0, site.getInterval(), TimeUnit.SECONDS);
				monitors.add(monitor);
			} catch (IOException e) {
				System.err.println("An error occurred and the site monitor for site \"" + site.getName() + "\" could not be started.");
			}
		}
		return monitors;
	}

	private int runCLI(ScheduledExecutorService executor, List<SiteMonitor> monitors) throws InterruptedException, IOException {
		String line;
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		while ((line = reader.readLine()) != null) {
			if (line.trim().equalsIgnoreCase("stop")) {
				break;
			}
		}
		System.out.println("Stopping monitoring...");
		executor.shutdown();
		while (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
			System.err.println("Waiting for all remaining tasks to finish...");
		}
		for (var monitor : monitors) {
			monitor.close();
		}
		return 0;
	}

	private void addShutdownHook(ExecutorService executor, List<SiteMonitor> monitors, Scheduler scheduler) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			executor.shutdown();
			try {
				scheduler.shutdown();
			} catch (SchedulerException e) {
				e.printStackTrace();
			}
			for (var monitor : monitors) {
				try {
					monitor.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}));
	}

	private void initializeReportGenerators(List<Config.ReportConfig> reportConfigs, Scheduler scheduler) throws SchedulerException {
		for (var report : reportConfigs) {
			if (report.getSites() == null || report.getSites().isEmpty()) throw new IllegalArgumentException("Missing sites for report " + report.getName());
			Interval interval = Interval.valueOf(report.getInterval().trim().toUpperCase());
			Format format = Format.valueOf(report.getFormat().trim().toUpperCase());
			Period span = Period.parse(report.getSpan());
			List<FocusInterval> focusIntervals = new ArrayList<>();
			if (report.getFocusIntervals() != null && !report.getFocusIntervals().isEmpty()) {
				for (var focusIntervalString : report.getFocusIntervals()) {
					String[] parts = focusIntervalString.split("-");
					if (parts.length != 2) throw new IllegalArgumentException("Invalid focus interval format: " + focusIntervalString);
					var from = LocalTime.parse(parts[0].trim());
					var to = LocalTime.parse(parts[1].trim());
					if (from.isAfter(to)) throw new IllegalArgumentException("Invalid focus interval format: " + focusIntervalString);
					focusIntervals.add(new FocusInterval(from, to));
				}
			}
			JobDetail job = JobBuilder.newJob(ReportGenerationJob.class)
					.withIdentity("report-generation-" + report.getName(), "reports")
					.build();
			var generator = new ReportGenerator(report.getName(), report.getSites(), format, span, focusIntervals);
			job.getJobDataMap().put("generator", generator);
			Trigger trigger = TriggerBuilder.newTrigger()
					.withIdentity("report-generation-trigger-" + report.getName(), "report-triggers")
					.withSchedule(interval.getSchedule())
					.build();
			scheduler.scheduleJob(job, trigger);
		}
		scheduler.start();
	}

	public static void main(String[] args) {
		int exitCode = new CommandLine(new UptimeMonitor()).execute(args);
		System.exit(exitCode);
	}

	public static long parseSize(String text) {
		double d = Double.parseDouble(text.replaceAll("[GMK]B$", ""));
		long l = Math.round(d * 1024 * 1024 * 1024L);
		switch (text.charAt(Math.max(0, text.length() - 2))) {
			default:  l /= 1024;
			case 'K': l /= 1024;
			case 'M': l /= 1024;
			case 'G': return l;
		}
	}
}
