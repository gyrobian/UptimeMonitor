package nl.gyrobian.uptime_monitor.data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Statistical information that is gathered as a result of parsing one or more
 * report files for a single site.
 */
public record PerformanceData(
		OffsetDateTime generatedAt,
		LocalDate startDate,
		LocalDate endDate,
		String siteName,
		float averageResponseTime,
		float successPercent,
		long measurementDuration,
		long totalFilesSize,
		int fileCount,
		MonitorEntry[] entries
) {}
