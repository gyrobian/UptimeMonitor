package nl.gyrobian.uptime_monitor.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.lingala.zip4j.ZipFile;
import nl.gyrobian.uptime_monitor.command.format.PdfWriter;
import nl.gyrobian.uptime_monitor.data.FocusInterval;
import nl.gyrobian.uptime_monitor.data.MeasurementService;
import nl.gyrobian.uptime_monitor.data.ReportData;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * A report generator is responsible for preparing the data for, and generating
 * a report.
 */
public class ReportGenerator {
	private static final Path REPORTS_DIR = Path.of("reports");

	private final String name;
	private final List<String> sites;
	private final Format format;
	private final Period span;
	private final List<FocusInterval> focusIntervals;

	public ReportGenerator(String name, List<String> sites, Format format, Period span, List<FocusInterval> focusIntervals) {
		this.name = name;
		this.sites = sites;
		this.format = format;
		this.span = span;
		this.focusIntervals = focusIntervals;
	}

	/**
	 * Generates the report. This will generate a ZIP file containing reports
	 * for every individual site that this generator is responsible for. The ZIP
	 * file has the format "yyyy-MM-dd_HH-mm-ss_report-name.zip".
	 * @throws IOException If an error occurs while generating the report.
	 */
	public void generate() throws IOException {
		System.out.println("Generating report: " + name + ".");
		String sanitizedName = name.replaceAll("\\s+", "-");
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
		Path dir = REPORTS_DIR.resolve(timestamp + "_" + sanitizedName);
		Files.createDirectories(dir);
		var zip = new ZipFile(dir.toFile().getAbsolutePath() + ".zip");
		for (var site : sites) {
			var file = generateSiteReport(site, dir, now.minus(span).toLocalDate(), now.toLocalDate());
			System.out.println("Generated site report: " + file);
			zip.addFile(file.toFile());
		}
		// Remove the directory once we're done.
		try (var s = Files.walk(dir).map(Path::toFile).sorted(Comparator.reverseOrder())) {
			s.forEach(File::delete);
		}
	}

	/**
	 * Generates an individual site report for a single site, and returns the
	 * path to the file that the report is contained in.
	 * @param site The site to generate the report for.
	 * @param dir The directory to generate the report in.
	 * @param start The starting date (inclusive) for the report.
	 * @param end The ending date (inclusive) for the report.
	 * @return The path to the generated file.
	 * @throws IOException If an error occurs while writing the file.
	 */
	private Path generateSiteReport(String site, Path dir, LocalDate start, LocalDate end) throws IOException {
		var data = new MeasurementService().getData(site, start, end, focusIntervals);
		Path file = dir.resolve(site + format.extension());
		switch (format) {
			case TEXT -> writeText(data, file);
			case JSON -> writeJson(data, file);
			case PDF -> writePdf(data, file);
		}
		return file;
	}

	/**
	 * Writes a textual report.
	 * @param data The report data.
	 * @param file The file to write to.
	 * @throws IOException If an error occurs while writing.
	 */
	private void writeText(ReportData data, Path file) throws IOException {
		PrintWriter w = new PrintWriter(Files.newBufferedWriter(file));
		w.printf("Performance data for site %s from %s to %s, generated in %d ms.\n", data.siteName(), data.startDate(), data.endDate(), data.measurementDuration());
		w.printf("Average response time: %.2f (ms)\nPercent of requests that were successful: %.2f\n", data.aggregatePerformance().averageResponseTime(), data.aggregatePerformance().successPercent());
		w.printf("Total number of entries: %d\n", data.entries().length);
		w.close();
	}

	/**
	 * Writes a JSON report that contains a full serialized version of the
	 * report data.
	 * @param data The report data.
	 * @param file The file to write to.
	 * @throws IOException If an error occurs while writing.
	 */
	private void writeJson(ReportData data, Path file) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		var out = Files.newBufferedWriter(file);
		mapper.writerWithDefaultPrettyPrinter().writeValue(out, data);
		out.close();
	}

	/**
	 * Writes a PDF report.
	 * @param data The report data.
	 * @param file The file to write to.
	 * @throws IOException If an error occurs while writing.
	 */
	private void writePdf(ReportData data, Path file) throws IOException {
		var out = Files.newOutputStream(file);
		var writer = new PdfWriter();
		writer.write(data, out);
		out.close();
	}
}
