package nl.gyrobian.uptime_monitor.command.format;

import nl.gyrobian.uptime_monitor.data.ReportData;

import java.io.OutputStream;

/**
 * A writer capable of producing reports for the given performance data of a
 * single site.
 */
public interface PerformanceDataWriter {
	void write(ReportData data, OutputStream out) throws Exception;
}
