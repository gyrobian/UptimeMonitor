package nl.gyrobian.uptime_monitor.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Configuration for mail connections for send emails.
 */
@Data
public class MailConfig {
	private SmtpConfig smtp;

	@Data
	public static class SmtpConfig {
		private String host;
		private int port;
		private String username;
		private String password;
		@JsonProperty("from-address")
		private String fromAddress;
	}
}
