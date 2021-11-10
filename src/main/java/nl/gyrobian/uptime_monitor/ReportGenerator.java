package nl.gyrobian.uptime_monitor;

import java.time.Instant;
import java.time.Period;

/**
 * A report generator is responsible for preparing the data for, and generating
 * a report.
 */
public class ReportGenerator {
	public enum Format {TEXT, JSON, PDF}
	public enum Interval {WEEKLY, MONTHLY}

	private final Format format;
	private final Interval interval;
	private final Period span;
	private Instant lastGeneratedAt;

	public ReportGenerator(Format format, Interval interval, Period span) {
		this.format = format;
		this.interval = interval;
		this.span = span;
		this.lastGeneratedAt = Instant.EPOCH;
	}
}
