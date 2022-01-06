package nl.gyrobian.uptime_monitor.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ReportConfig {
	private String name;
	private List<String> sites;
	private String interval;
	private String span;
	private String format;

	@JsonProperty("focus-intervals")
	private List<String> focusIntervals;

	@JsonProperty("distribution")
	private List<ReportDistributionConfig> distributions;
}
