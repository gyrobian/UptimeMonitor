package nl.gyrobian.uptime_monitor.command;

import org.apache.commons.csv.CSVFormat;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "measure",
		description = "Perform measurements on existing site metrics."
)
public class MeasureSubcommand implements Callable<Integer> {
	@CommandLine.Parameters(index = "0", description = "The name of the site to measure.")
	String siteName;

	@CommandLine.Option(names = {"--start"}, description = "The start date for measurement, as an ISO-8601 date.")
	String startDate;

	@CommandLine.Option(names = {"--end"}, description = "The end date for measurement, as an ISO-8601 date.")
	String endDate;

	@Override
	public Integer call() throws Exception {
		System.out.println("Measuring data for site " + siteName);
		Path siteDir = Path.of("sites", siteName);

		long entries = 0;
		long responseTimeSum = 0;
		long errorResponses = 0;

		LocalDate measurementStartDate = null;
		LocalDate measurementEndDate = null;
		if (startDate != null && !startDate.isBlank()) {
			measurementStartDate = LocalDate.parse(startDate);
		}
		if (endDate != null && !endDate.isBlank()) {
			measurementEndDate = LocalDate.parse(endDate);
		}

		OffsetDateTime start = null;
		OffsetDateTime end = null;

		try (var s = Files.list(siteDir)) {
			for (var path : s.toList()) {
				if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".csv")) {
					LocalDateTime fileStartTimestamp = LocalDateTime.parse(
							path.getFileName().toString().split("\\.")[0],
							DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
					);
					// Skip this file if it starts outside of our measurement period.
					if (measurementEndDate != null && fileStartTimestamp.isAfter(measurementEndDate.atStartOfDay())) {
						continue;
					}
					System.out.println("Reading " + path);
					try {
						var reader = Files.newBufferedReader(path);
						boolean isHeader = true;
						for (var record : CSVFormat.DEFAULT.parse(reader)) {
							if (isHeader) {
								isHeader = false;
								continue;
							}

							OffsetDateTime timestamp = OffsetDateTime.parse(record.get(0));
							// Skip this record if its timestamp is outside of the measurement period.
							if (
									(measurementStartDate != null && timestamp.toLocalDateTime().isBefore(measurementStartDate.atStartOfDay())) ||
									(measurementEndDate != null && timestamp.toLocalDateTime().isAfter(measurementEndDate.plusDays(1).atStartOfDay()))
							) {
								continue;
							}
							if (start == null || start.isAfter(timestamp)) start = timestamp;
							if (end == null || end.isBefore(timestamp)) end = timestamp;

							entries++;
							responseTimeSum += Long.parseLong(record.get(3));
							int responseCode = Integer.parseInt(record.get(2));
							if (responseCode < 200 || responseCode > 299) {
								errorResponses++;
							}
						}
						reader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}

		System.out.printf("Read %d entries between %s and %s.\n", entries, start, end);
		double averageResponseTime = responseTimeSum / (double) entries;
		System.out.printf("Average response time: %.2f ms\n", averageResponseTime);
		System.out.printf("There were %d erroneous responses received.\n", errorResponses);

		return 0;
	}
}
