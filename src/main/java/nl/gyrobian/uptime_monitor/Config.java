package nl.gyrobian.uptime_monitor;

import lombok.Data;

import java.util.List;

@Data
public class Config {
	private String maxFileSize;
	private List<SiteConfig> sites;

	@Data
	public static class SiteConfig {
		private String name;
		private String url;
		private int interval;
	}
}
