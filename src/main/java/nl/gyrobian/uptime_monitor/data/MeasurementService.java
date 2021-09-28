package nl.gyrobian.uptime_monitor.data;

import org.apache.commons.csv.CSVFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MeasurementService {
	public PerformanceData getData(String siteName, LocalDate startDate, LocalDate endDate) throws IOException {
		Path siteDir = Path.of("sites", siteName);

		long responseTimeSum = 0;
		long totalDowntime = 0;
		long totalUptime = 0;
		long errorResponses = 0;
		long fileSizeTraversed = 0;
		int fileCount = 0;
		long startedAt = System.currentTimeMillis();

		List<MonitorEntry> entries = new ArrayList<>();
		MonitorEntry previousEntry = null;

		try (var s = Files.list(siteDir)) {
			for (var path : s.toList()) {
				if (shouldReadFile(path, endDate)) {
					try (var reader = Files.newBufferedReader(path)) {
						boolean isHeader = true;
						for (var record : CSVFormat.DEFAULT.parse(reader)) {
							if (isHeader) {
								isHeader = false;
								continue;
							}

							var entry = MonitorEntry.fromCsvRecord(record);
							// Skip this record if its timestamp is outside the measurement period.
							if (shouldReadRecord(entry.timestamp(), startDate, endDate)) {
								responseTimeSum += entry.responseTime();
								Duration timeSinceLastEntry = previousEntry == null ? Duration.ZERO : Duration.between(previousEntry.timestamp(), entry.timestamp());
								if (!entry.isOk()) {
									errorResponses++;
									// If we have noticed a continuous span of time in which the service is consistently not ok, measure this as totalDowntime.
									if (previousEntry != null && !previousEntry.isOk()) {
										totalDowntime += timeSinceLastEntry.toMillis();
									}
								} else if (previousEntry != null && previousEntry.isOk()) {
									// If we have a continuous span of time in which the service is consistently ok, measure this as totalUptime.
									totalUptime += timeSinceLastEntry.toMillis();
								}
								entries.add(entry);
								previousEntry = entry;
							}
						}
						fileSizeTraversed += Files.size(path);
						fileCount++;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}

		float averageResponseTime = (float) (responseTimeSum / (double) entries.size());
		float successPercentage = (float) ((entries.size() - errorResponses) / (double) entries.size()) * 100.0f;
		float uptimePercentage = 100.0f;
		if (totalUptime > 0 || totalDowntime > 0) {
			uptimePercentage *= (float) (totalUptime / (double) (totalUptime + totalDowntime));
		}

		return new PerformanceData(
				OffsetDateTime.now(),
				startDate != null ? startDate : entries.get(0).timestamp().toLocalDate(),
				endDate != null ? endDate : entries.get(entries.size() - 1).timestamp().toLocalDate(),
				siteName,
				averageResponseTime,
				successPercentage,
				Duration.ofMillis(totalUptime),
				Duration.ofMillis(totalDowntime),
				uptimePercentage,
				System.currentTimeMillis() - startedAt,
				fileSizeTraversed,
				fileCount,
				entries.toArray(new MonitorEntry[0])
		);
	}

	private boolean shouldReadFile(Path path, LocalDate endDate) {
		if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".csv")) return false;
		LocalDateTime fileStartTimestamp = LocalDateTime.parse(
				path.getFileName().toString().split("\\.")[0],
				DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
		);
		// Skip this file if it starts outside our measurement period.
		return endDate == null || !fileStartTimestamp.isAfter(endDate.atStartOfDay());
	}

	private boolean shouldReadRecord(OffsetDateTime recordTimestamp, LocalDate startDate, LocalDate endDate) {
		return (startDate == null || recordTimestamp.toLocalDateTime().isAfter(startDate.atStartOfDay())) &&
				(endDate == null || recordTimestamp.toLocalDateTime().isBefore(endDate.plusDays(1).atStartOfDay()));
	}
}
