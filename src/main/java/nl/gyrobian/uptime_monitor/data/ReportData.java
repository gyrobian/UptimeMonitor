package nl.gyrobian.uptime_monitor.data;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Statistical information that is gathered as a result of parsing one or more
 * report files for a single site.
 *
 * @param generatedAt The timestamp for when this data was generated.
 * @param startDate The start date of the measurement range.
 * @param endDate The end date of the measurement range.
 * @param siteName The name of the site that was measured.
 * @param measurementDuration The time it took to perform the measurement.
 * @param totalFilesSize The total size of all files examined.
 * @param fileCount The number of files examined.
 * @param entries A list of all entries.
 */
public record ReportData(
		OffsetDateTime generatedAt,
		LocalDate startDate,
		LocalDate endDate,
		String siteName,
		long measurementDuration,
		long totalFilesSize,
		int fileCount,
		MonitorEntry[] entries,
		PerformanceData aggregatePerformance,
		Map<FocusInterval, PerformanceData> focusIntervalPerformanceData
) {
	/**
	 * Performance data that has been computed for a set of entries.
	 * @param averageResponseTime The average response time, in milliseconds.
	 * @param successPercent The percentage of requests that were successful.
	 * @param totalUptime The total duration during which the site was online.
	 * @param totalDowntime The total duration during which the site was down.
	 * @param uptimePercent The percentage of time that the site was up.
	 * @param entryCount The number of entries that make up this data.
	 */
	public record PerformanceData(
			float averageResponseTime,
			float successPercent,
			Duration totalUptime,
			Duration totalDowntime,
			float uptimePercent,
			long entryCount
	) {}
}
