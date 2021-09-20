package nl.gyrobian.uptime_monitor.command.format;

import nl.gyrobian.uptime_monitor.data.PerformanceData;

import java.io.OutputStream;

/**
 * A writer capable of producing reports for the given performance data of a
 * single site.
 */
public interface PerformanceDataWriter {
	void write(PerformanceData data, OutputStream out) throws Exception;
}
