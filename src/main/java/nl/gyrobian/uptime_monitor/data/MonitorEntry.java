package nl.gyrobian.uptime_monitor.data;

import org.apache.commons.csv.CSVRecord;

import java.time.OffsetDateTime;

/**
 * Represents a single measurement for a site.
 */
public record MonitorEntry(
		OffsetDateTime timestamp,
		String url,
		int responseCode,
		int responseTime,
		String details
) {
	public static MonitorEntry fromCsvRecord(CSVRecord record) {
		return new MonitorEntry(
				OffsetDateTime.parse(record.get(0)),
				record.get(1),
				Integer.parseInt(record.get(2)),
				Integer.parseInt(record.get(3)),
				record.get(4)
		);
	}

	public boolean isOk() {
		return responseCode < 400;
	}
}
