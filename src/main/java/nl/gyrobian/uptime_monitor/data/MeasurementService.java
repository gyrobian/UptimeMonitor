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
import java.util.*;

/**
 * The main logic which analyzes data and produces a cumulative dataset result.
 */
public class MeasurementService {
	/**
	 * Computes a full report dataset for a given site, within a set interval.
	 * @param siteName The name of the site to gather data for. This should
	 *                 exactly match the site's name in the configuration file.
	 * @param startDate The start of the measurement period, inclusive.
	 * @param endDate The end of the measurement period, inclusive.
	 * @param focusIntervals A list of focus intervals, which are periods of
	 *                       time that should have their own set of statistics.
	 * @return The report data.
	 * @throws IOException If an error occurs while reading data.
	 */
	public ReportData getData(String siteName, LocalDate startDate, LocalDate endDate, List<FocusInterval> focusIntervals) throws IOException {
		Path siteDir = Path.of("sites", siteName);
		long fileSizeTraversed = 0;
		int fileCount = 0;
		List<MonitorEntry> entries = new ArrayList<>();
		Map<FocusInterval, List<MonitorEntry>> focusIntervalEntries = new HashMap<>();
		long measurementStartedAt = System.currentTimeMillis();
		// Iterate over all files that have been generated for the selected site.
		try (var s = Files.list(siteDir)
				.sorted(Comparator.comparing(Path::getFileName))
				.filter(path -> shouldReadFile(path, endDate))) {
			for (var path : s.toList()) {
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
							entries.add(entry);
							// Add the entry to any applicable focus intervals.
							for (var focusInterval : focusIntervals) {
								if (focusInterval.contains(entry.timestamp())) {
									focusIntervalEntries.computeIfAbsent(focusInterval, fi -> new ArrayList<>()).add(entry);
								}
							}
						}
					}
					fileSizeTraversed += Files.size(path);
					fileCount++;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		Map<FocusInterval, ReportData.PerformanceData> focusIntervalPerformanceData = new HashMap<>();
		for (var interval : focusIntervals) {
			var e = focusIntervalEntries.computeIfAbsent(interval, fi -> new ArrayList<>());
			focusIntervalPerformanceData.put(interval, computePerformanceData(e));
		}

		return new ReportData(
				OffsetDateTime.now(),
				startDate != null ? startDate : entries.get(0).timestamp().toLocalDate(),
				endDate != null ? endDate : entries.get(entries.size() - 1).timestamp().toLocalDate(),
				siteName,
				System.currentTimeMillis() - measurementStartedAt,
				fileSizeTraversed,
				fileCount,
				entries.toArray(new MonitorEntry[0]),
				computePerformanceData(entries),
				focusIntervalPerformanceData
		);
	}

	/**
	 * Computes performance data for a set of entries.
	 * @param entries The entries to extract performance data from.
	 * @return The data that was obtained.
	 */
	private ReportData.PerformanceData computePerformanceData(List<MonitorEntry> entries) {
		if (entries.size() == 0) return new ReportData.PerformanceData(0, 100, Duration.ZERO, Duration.ZERO, 100, 0);

		long responseTimeSum = 0;
		long totalDowntime = 0;
		long totalUptime = 0;
		long errorResponses = 0;
		MonitorEntry previousEntry = null;

		Collections.sort(entries);
		for (var entry : entries) {
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
			responseTimeSum += entry.responseTime();
			previousEntry = entry;
		}

		float averageResponseTime = (float) (responseTimeSum / (double) entries.size());
		float successPercentage = (float) ((entries.size() - errorResponses) / (double) entries.size()) * 100.0f;
		float uptimePercentage = 100.0f;
		if (totalUptime > 0 || totalDowntime > 0) {
			uptimePercentage *= (float) (totalUptime / (double) (totalUptime + totalDowntime));
		}

		return new ReportData.PerformanceData(
				averageResponseTime,
				successPercentage,
				Duration.ofMillis(totalUptime),
				Duration.ofMillis(totalDowntime),
				uptimePercentage,
				entries.size()
		);
	}

	/**
	 * Determines if it is necessary to read a file for a measurement, based on
	 * the end date of the measurement period, and the file's timestamp name.
	 * @param path The path to the file.
	 * @param endDate The measurement period's ending date (inclusive).
	 * @return True if the file's contents should be processed, or false otherwise.
	 */
	private boolean shouldReadFile(Path path, LocalDate endDate) {
		if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".csv")) return false;
		LocalDateTime fileStartTimestamp = LocalDateTime.parse(
				path.getFileName().toString().split("\\.")[0],
				DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
		);
		// Skip this file if it starts outside our measurement period.
		return endDate == null || !fileStartTimestamp.isAfter(endDate.plusDays(1).atStartOfDay());
	}

	/**
	 * Determines if a single record should be read, based on the measurement
	 * period's start and end date, and the record's timestamp.
	 * @param recordTimestamp The record's timestamp.
	 * @param startDate The measurement period's starting date (inclusive).
	 * @param endDate The measurement period's ending date (inclusive).
	 * @return True if the record should be read and included in data, or false
	 * otherwise.
	 */
	private boolean shouldReadRecord(OffsetDateTime recordTimestamp, LocalDate startDate, LocalDate endDate) {
		return (startDate == null || recordTimestamp.toLocalDateTime().isAfter(startDate.atStartOfDay())) &&
				(endDate == null || recordTimestamp.toLocalDateTime().isBefore(endDate.plusDays(1).atStartOfDay()));
	}
}
