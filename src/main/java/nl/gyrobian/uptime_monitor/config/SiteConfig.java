package nl.gyrobian.uptime_monitor.config;

import lombok.Data;

@Data
public class SiteConfig {
	private String name;
	private String url;
	private int interval;
}
