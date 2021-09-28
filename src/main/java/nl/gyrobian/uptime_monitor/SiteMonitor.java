package nl.gyrobian.uptime_monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * A site monitor is responsible for the logic of checking the status of a
 * single site, and recording the results in a CSV file.
 */
public class SiteMonitor implements Closeable {
	public static final DateTimeFormatter FILE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

	private final Config.SiteConfig site;
	private final HttpClient httpClient;
	private final HttpRequest.Builder requestBuilder;
	private final ObjectMapper mapper;
	private final long maxFileSize;

	private CSVPrinter csvPrinter;
	private Path recordFile;

	public SiteMonitor(Config.SiteConfig site, long maxFileSize) throws IOException {
		this.site = site;
		this.maxFileSize = maxFileSize;
		this.mapper = new ObjectMapper();
		this.httpClient = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.ALWAYS)
				.connectTimeout(Duration.ofSeconds(5))
				.version(HttpClient.Version.HTTP_2)
				.build();
		this.requestBuilder = HttpRequest.newBuilder()
				.GET()
				.setHeader("Cache-Control", "no-cache")
				.setHeader("User-Agent", "SiteMonitor")
				.setHeader("Accept", "*/*")
				.timeout(Duration.ofSeconds(5));
		this.csvPrinter = initPrinter();
	}

	/**
	 * Initializes the CSV printer when starting the monitor. This will look
	 * under sites/{site.name} for a list of CSV files, and it will append to
	 * the most recent file, if its size has not yet exceeded the maximum file
	 * size. Otherwise, a new file will be created if the most recent file was
	 * too big or there are no files.
	 * @return The CSV printer to use when recording site monitoring info.
	 * @throws IOException If the printer could not be initialized.
	 */
	private CSVPrinter initPrinter() throws IOException {
		Path dir = Path.of("sites", site.getName());
		if (Files.notExists(dir)) Files.createDirectories(dir);
		boolean shouldWriteHeader = false;
		try (var s = Files.list(dir)) {
			var files = s.filter(Files::isRegularFile)
					.sorted(Comparator.comparing(Path::getFileName))
					.collect(Collectors.toList());
			if (files.isEmpty() || Files.size(files.get(files.size() - 1)) > this.maxFileSize) {
				String timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(FILE_TIMESTAMP_FORMATTER);
				this.recordFile = dir.resolve(timestamp + ".csv");
				shouldWriteHeader = true;
				System.out.println("Creating new file to record monitoring data for site " + this.site.getName() + " at " + this.recordFile);
			} else {
				this.recordFile = files.get(files.size() - 1);
				System.out.println("Appending site monitoring data for " + this.site.getName() + " to " + this.recordFile);
			}
		}
		var writer = Files.newBufferedWriter(recordFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		return CSVFormat.DEFAULT.builder()
				.setHeader("Timestamp", "URL", "Response Code", "Response Time (ms)", "Response Details")
				.setSkipHeaderRecord(!shouldWriteHeader)
				.build().print(writer);
	}

	/**
	 * Switches to a new CSV file during runtime, closing the current printer
	 * and creating a new file.
	 * @throws IOException If an error occurs while closing or opening files.
	 */
	private void switchToNewFile() throws IOException {
		this.csvPrinter.close();
		String ts = ZonedDateTime.now(ZoneOffset.UTC).format(FILE_TIMESTAMP_FORMATTER);
		this.recordFile = this.recordFile.getParent().resolve(ts + ".csv");
		System.out.println("Creating new file to record monitoring data for site " + this.site.getName() + " at " + this.recordFile);
		var writer = Files.newBufferedWriter(recordFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		this.csvPrinter = CSVFormat.DEFAULT.builder()
				.setHeader("Timestamp", "URL", "Response Code", "Response Time (ms)", "Response Details")
				.build().print(writer);
	}

	/**
	 * Performs a monitoring check of this monitor's site by sending an HTTP
	 * request to the site's URL and recording the response.
	 */
	public void monitor() {
		HttpRequest request = this.requestBuilder.copy().uri(URI.create(this.site.getUrl())).build();
		try {
			long start = System.currentTimeMillis();
			HttpResponse<InputStream> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
			long duration = System.currentTimeMillis() - start;
			String timestamp = Instant.ofEpochMilli(start).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
			String details = null;
			var contentType = response.headers().firstValue("Content-Type");
			if (contentType.isPresent() && contentType.get().equalsIgnoreCase("application/json")) {
				details = new String(response.body().readAllBytes());
			}
			response.body().close();
			this.csvPrinter.printRecord(timestamp, this.site.getUrl(), response.statusCode(), duration, details);
			this.csvPrinter.flush();

			// Close the current printer and open a new file if we've exceeded the size limit.
			if (Files.size(this.recordFile) > this.maxFileSize) {
				this.switchToNewFile();
			}
		} catch (HttpConnectTimeoutException e) {
			System.err.println("Connection timed out while sending request to " + this.site.getUrl());
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void close() throws IOException {
		this.csvPrinter.close();
	}
}
