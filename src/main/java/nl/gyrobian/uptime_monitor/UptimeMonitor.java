package nl.gyrobian.uptime_monitor;

import nl.gyrobian.uptime_monitor.command.GenerateReportsSubcommand;
import nl.gyrobian.uptime_monitor.command.MeasureSubcommand;
import nl.gyrobian.uptime_monitor.config.Config;
import nl.gyrobian.uptime_monitor.config.ReportConfig;
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

/**
 * The main command line interface entry point for the application. Note the
 * annotated list of available subcommands. If no subcommands are given, then
 * the uptime-monitor program will start running continuously, taking recordings
 * as specified in the configuration.
 */
@CommandLine.Command(
		name = "totalUptime-monitor",
		description = "Monitors and records the totalUptime of sites.",
		subcommands = {
				MeasureSubcommand.class,
				GenerateReportsSubcommand.class
		}
)
public class UptimeMonitor implements Callable<Integer> {
	static {
		// Disable apache logging which would otherwise show when using PDFBox.
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
	}

	/**
	 * The path to the configuration file to use.
	 */
	@CommandLine.Option(names = {"-c", "--config"}, description = "Path to the configuration file to use.", defaultValue = "./config.yaml", scope = CommandLine.ScopeType.INHERIT)
	public String configPath;

	/**
	 * Whether to ignore CLI, meaning it will run effectively in a while-loop
	 * until the system terminates it. Defaults to false, meaning that the
	 * program will enter a CLI where the user must enter the "stop" command to
	 * stop the program.
	 */
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
			initializeReportGenerators(config, scheduler);
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

	/**
	 * Initializes a list of site monitors that periodically record performance
	 * data for a site.
	 * @param config The configuration.
	 * @param maxFileSize The maximum file size, in bytes, for data files
	 *                    generated by the site monitors.
	 * @param executor The executor service to use to run the monitors.
	 * @return The list of monitors which were initialized.
	 */
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

	/**
	 * Runs the command line interface.
	 * @param executor The executor service that runs scheduled site monitoring
	 *                 tasks.
	 * @param monitors The list of active site monitors.
	 * @return The program return code.
	 * @throws InterruptedException If the program is interrupted while waiting
	 * for things to terminate.
	 * @throws IOException If an error occurs while reading from or writing to
	 * standard input/output.
	 */
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

	/**
	 * Adds a JVM shutdown hook that shuts down the giving scheduler and the
	 * list of site monitors.
	 * @param executor The executor service.
	 * @param monitors The list of active monitors.
	 * @param scheduler The quartz scheduler.
	 */
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

	/**
	 * Initializes any configured report generators, by preparing and scheduling
	 * a job for them according to their defined schedule.
	 * @param config The application configuration.
	 * @param scheduler The scheduler to schedule report generators on.
	 * @throws SchedulerException If an error occurs while scheduling jobs.
	 */
	private void initializeReportGenerators(Config config, Scheduler scheduler) throws SchedulerException {
		var reportConfigs = config.getReports();
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
			var generator = new ReportGenerator(report.getName(), report.getSites(), format, span, focusIntervals, report.getDistributions(), config.getMail());
			job.getJobDataMap().put("generator", generator);
			job.getJobDataMap().put("config", report);
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

	/**
	 * Utility method to parse a "byte size" from a string, like "4MB" or "1B".
	 * @param text The text to parse.
	 * @return The byte size that was obtained.
	 */
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
