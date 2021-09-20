package nl.gyrobian.uptime_monitor.data;

import org.apache.commons.csv.CSVFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
		long errorResponses = 0;
		long fileSizeTraversed = 0;
		int fileCount = 0;
		long startedAt = System.currentTimeMillis();

		List<MonitorEntry> entries = new ArrayList<>();

		try (var s = Files.list(siteDir)) {
			for (var path : s.toList()) {
				if (shouldReadFile(path, endDate)) {
					try {
						var reader = Files.newBufferedReader(path);
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
								if (entry.responseCode() < 200 || entry.responseCode() > 299) {
									errorResponses++;
								}
								entries.add(entry);
							}
						}
						reader.close();
						fileSizeTraversed += Files.size(path);
						fileCount++;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}

		return new PerformanceData(
				OffsetDateTime.now(),
				startDate != null ? startDate : entries.get(0).timestamp().toLocalDate(),
				endDate != null ? endDate : entries.get(entries.size() - 1).timestamp().toLocalDate(),
				siteName,
				(float) (responseTimeSum / (double) entries.size()),
				(float) ((entries.size() - errorResponses) / (double) entries.size()) * 100.0f,
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
