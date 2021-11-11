package nl.gyrobian.uptime_monitor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Data
public class Config {
	private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
	static {
		yamlMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	}

	private String maxFileSize;
	private List<SiteConfig> sites;
	private List<ReportConfig> reports;

	@Data
	public static class SiteConfig {
		private String name;
		private String url;
		private int interval;
	}

	@Data
	public static class ReportConfig {
		private String name;
		private List<String> sites;
		private String interval;
		private String span;
		private String format;

		@JsonProperty("focus-intervals")
		private List<String> focusIntervals;
	}

	public static Config load(Path file) {
		if (Files.notExists(file)) return null;
		try {
			return yamlMapper.readValue(Files.newBufferedReader(file), Config.class);
		} catch (IOException e) {
			System.err.println("IOException occurred while reading " + file);
			return null;
		}
	}
}
