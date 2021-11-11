package nl.gyrobian.uptime_monitor.report;

import org.quartz.CronScheduleBuilder;
import org.quartz.DateBuilder;
import org.quartz.ScheduleBuilder;

import java.time.ZoneOffset;
import java.util.TimeZone;

public enum Interval {
	WEEKLY,
	MONTHLY;

	public ScheduleBuilder<?> getSchedule() {
		return switch (this) {
			case WEEKLY -> CronScheduleBuilder.weeklyOnDayAndHourAndMinute(DateBuilder.MONDAY, 1, 0)
					.inTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
			case MONTHLY -> CronScheduleBuilder.monthlyOnDayAndHourAndMinute(1, 1, 0)
					.inTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
		};
	}
}
