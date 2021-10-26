package nl.gyrobian.uptime_monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import nl.gyrobian.uptime_monitor.command.MeasureSubcommand;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

	private Config loadConfig() {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		Path file = Path.of(this.configPath);
		if (Files.notExists(file)) return null;
		try {
			return mapper.readValue(Files.newBufferedReader(file), Config.class);
		} catch (IOException e) {
			System.err.println("IOException occurred while reading " + file);
			return null;
		}
	}

	@Override
	public Integer call() throws Exception {
		var config = this.loadConfig();
		if (config == null) {
			System.err.println("Could not load configuration.");
			return 1;
		}
		long maxFileSize = parseSize(config.getMaxFileSize());
		List<SiteMonitor> monitors = new ArrayList<>(config.getSites().size());
		ScheduledExecutorService executor = Executors.newScheduledThreadPool(config.getSites().size());
		for (var site : config.getSites()) {
			System.out.printf("Initializing monitoring of site \"%s\" every %d seconds.\n", site.getName(), site.getInterval());
			var monitor = new SiteMonitor(site, maxFileSize);
			executor.scheduleAtFixedRate(monitor::monitor, 0, site.getInterval(), TimeUnit.SECONDS);
			monitors.add(monitor);
		}
		System.out.println("Started monitoring all configured sites.");
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			executor.shutdown();
			for (var monitor : monitors) {
				try {
					monitor.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}));
		if (!ignoreCli) {
			return this.runCLI(executor, monitors);
		} else {
			while (!executor.isTerminated()) {
				Thread.sleep(3000);
			}
			return 0;
		}
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
