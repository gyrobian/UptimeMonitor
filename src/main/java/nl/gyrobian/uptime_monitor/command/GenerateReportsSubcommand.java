package nl.gyrobian.uptime_monitor.command;

import nl.gyrobian.uptime_monitor.UptimeMonitor;
import nl.gyrobian.uptime_monitor.config.Config;
import nl.gyrobian.uptime_monitor.data.FocusInterval;
import nl.gyrobian.uptime_monitor.report.Format;
import nl.gyrobian.uptime_monitor.report.ReportGenerator;
import picocli.CommandLine;

import java.nio.file.Path;
import java.time.LocalTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "generate-reports",
		description = "Immediately generate and distribute reports, instead of waiting for the scheduled report times."
)
public class GenerateReportsSubcommand implements Callable<Integer> {
	@CommandLine.ParentCommand
	UptimeMonitor uptimeMonitor;

	@Override
	public Integer call() throws Exception {
		var config = Config.load(Path.of(uptimeMonitor.configPath));
		if (config == null) {
			System.err.println("Could not load configuration.");
			return 1;
		}

		for (var report : config.getReports()) {
			if (report.getSites() == null || report.getSites().isEmpty()) throw new IllegalArgumentException("Missing sites for report " + report.getName());
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
			var generator = new ReportGenerator(report.getName(), report.getSites(), format, span, focusIntervals, report.getDistributions(), config.getMail());
			generator.generate();
		}

		return 0;
	}
}
