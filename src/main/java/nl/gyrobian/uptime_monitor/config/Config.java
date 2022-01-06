package nl.gyrobian.uptime_monitor.config;

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

	private MailConfig mail;

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
