package nl.gyrobian.uptime_monitor.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import nl.gyrobian.uptime_monitor.command.format.PdfWriter;
import nl.gyrobian.uptime_monitor.command.format.PerformanceDataWriter;
import nl.gyrobian.uptime_monitor.data.MeasurementService;
import nl.gyrobian.uptime_monitor.data.ReportData;
import picocli.CommandLine;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "measure",
		description = "Perform measurements on existing site metrics."
)
public class MeasureSubcommand implements Callable<Integer> {
	public enum Format {TEXT, JSON, PDF}

	public static final Map<Format, PerformanceDataWriter> formatWriters = Map.of(
			Format.TEXT, MeasureSubcommand::writeText,
			Format.JSON, MeasureSubcommand::writeJson,
			Format.PDF, new PdfWriter()
	);

	@CommandLine.Parameters(index = "0", description = "The name of the site to measure.")
	String siteName;

	@CommandLine.Option(names = {"--start"}, description = "The start date for measurement, as an ISO-8601 date.")
	String startDate;

	@CommandLine.Option(names = {"--end"}, description = "The end date for measurement, as an ISO-8601 date.")
	String endDate;

	@CommandLine.Option(names = {"--format"}, description = "The format in which to output the results.", defaultValue = "TEXT")
	Format format;

	@CommandLine.Option(names = {"-o", "--output"}, description = "The file to which the results should be written.")
	Path outputPath;

	@Override
	public Integer call() throws Exception {
		LocalDate measurementStartDate = null;
		LocalDate measurementEndDate = null;

		if (startDate != null && !startDate.isBlank()) {
			measurementStartDate = LocalDate.parse(startDate);
		}
		if (endDate != null && !endDate.isBlank()) {
			measurementEndDate = LocalDate.parse(endDate);
		}

		var data = new MeasurementService().getData(siteName, measurementStartDate, measurementEndDate, List.of());
		OutputStream out = outputPath == null ? System.out : Files.newOutputStream(outputPath);
		var writer = formatWriters.get(format);
		writer.write(data, out);

		return 0;
	}

	private static void writeText(ReportData data, OutputStream out) {
		PrintWriter pw = new PrintWriter(out, false);
		pw.printf("Performance data for site %s from %s to %s, generated in %d ms.\n", data.siteName(), data.startDate(), data.endDate(), data.measurementDuration());
		pw.printf("Average response time: %.2f (ms)\nPercent of requests that were successful: %.2f\n", data.aggregatePerformance().averageResponseTime(), data.aggregatePerformance().successPercent());
		pw.printf("Total number of entries: %d\n", data.entries().length);
		pw.close();
	}

	private static void writeJson(ReportData data, OutputStream out) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		mapper.writerWithDefaultPrettyPrinter().writeValue(out, data);
		out.close();
	}
}
