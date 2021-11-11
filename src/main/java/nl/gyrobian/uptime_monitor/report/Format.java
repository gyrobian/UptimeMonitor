package nl.gyrobian.uptime_monitor.report;

public enum Format {
	TEXT(".txt"),
	PDF(".pdf"),
	JSON(".json");

	private final String extension;

	Format(String extension) {
		this.extension = extension;
	}

	public String extension() {
		return this.extension;
	}
}
