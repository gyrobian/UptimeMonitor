package nl.gyrobian.uptime_monitor.config;

import lombok.Data;

/**
 * Configuration for report distribution strategies.
 */
@Data
public class ReportDistributionConfig {
	/**
	 * The means of distribution, such as email or http.
	 */
	private String via;

	/**
	 * The destination to send the report to. This takes different forms
	 * depending on the means of distribution, and might be an email address or
	 * a URL.
	 */
	private String to;
}
