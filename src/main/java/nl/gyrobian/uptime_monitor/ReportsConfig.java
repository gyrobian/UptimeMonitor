package nl.gyrobian.uptime_monitor;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ReportsConfig {
	private List<SiteConfig> sites;

	@Data
	public static class SiteConfig {
		private String site;
		private String interval;
		private String span;
		private String format;

		@JsonProperty("focus-intervals")
		private List<String> focusIntervals;
	}
}
