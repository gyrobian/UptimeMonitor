package nl.gyrobian.uptime_monitor.data;

import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Represents a single measurement for a site.
 */
public record MonitorEntry(
		OffsetDateTime timestamp,
		String url,
		int responseCode,
		int responseTime,
		String details
) implements Comparable<MonitorEntry> {
	public static MonitorEntry fromCsvRecord(CSVRecord record) throws IOException {
		try {
			return new MonitorEntry(
					OffsetDateTime.parse(record.get(0)),
					record.get(1),
					Integer.parseInt(record.get(2)),
					Integer.parseInt(record.get(3)),
					record.get(4)
			);
		} catch (DateTimeParseException e) {
			throw new IOException("Could not parse \"" + record.get(0) + "\" as an OffsetDateTime.", e);
		} catch (NumberFormatException e) {
			throw new IOException("Could not parse either \"" + record.get(2) + "\" or \"" + record.get(3) + "\" as an integer.", e);
		}
	}

	public boolean isOk() {
		return responseCode < 400;
	}

	@Override
	public int compareTo(MonitorEntry o) {
		return this.timestamp.compareTo(o.timestamp);
	}
}
