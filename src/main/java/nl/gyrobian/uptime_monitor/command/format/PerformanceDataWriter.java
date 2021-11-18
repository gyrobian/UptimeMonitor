package nl.gyrobian.uptime_monitor.command.format;

import nl.gyrobian.uptime_monitor.data.ReportData;

import java.io.OutputStream;

/**
 * A writer capable of producing reports for the given performance data of a
 * single site.
 */
public interface PerformanceDataWriter {
	/**
	 * Writes the given report data to the given output stream.
	 * @param data The report data.
	 * @param out The output stream to write to.
	 * @throws Exception If an error occurs while writing.
	 */
	void write(ReportData data, OutputStream out) throws Exception;
}
