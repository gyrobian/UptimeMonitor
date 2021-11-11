package nl.gyrobian.uptime_monitor.data;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * An inclusive interval between two times.
 */
public record FocusInterval(LocalTime from, LocalTime to) {
	public boolean contains(OffsetDateTime time) {
		var lt = time.toLocalTime();
		return lt.equals(from) || lt.equals(to) || (lt.isAfter(from) && lt.isBefore(to));
	}
}
