package nl.gyrobian.uptime_monitor.command.format;

import be.quodlibet.boxable.BaseTable;
import be.quodlibet.boxable.Row;
import nl.gyrobian.uptime_monitor.data.PerformanceData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Writes PDF reports containing performance data.
 */
public class PdfWriter implements PerformanceDataWriter {
	private static final float LEFT_MARGIN = 60.0f;
	private static final float TOP_MARGIN = 60.0f;
	private static final Color GYROBIAN_PURPLE = new Color(79, 27, 230);
	private static final DateTimeFormatter OFFSET_DATETIME_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy 'at' h:mma',' O");
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy");

	@Override
	public void write(PerformanceData data, OutputStream out) throws Exception {
		PDDocument doc = new PDDocument();
		PDPage page = new PDPage(PDRectangle.A4);
		doc.addPage(page);
		PDPageContentStream cs = new PDPageContentStream(doc, page);
		writePdfHeading(cs, page, data);
		writePdfGeneralStats(cs, page, data);
//		writeEntries(doc, page, cs, data);
		cs.close();
		doc.save(out);
		doc.close();
	}

	private void writePdfHeading(PDPageContentStream cs, PDPage page, PerformanceData data) throws IOException {
		float leftMargin = page.getCropBox().getLowerLeftX() + LEFT_MARGIN;
		float rightMargin = page.getCropBox().getUpperRightX() - LEFT_MARGIN;
		float topMargin = page.getCropBox().getUpperRightY() - TOP_MARGIN;
		cs.beginText();
		cs.newLineAtOffset(leftMargin, topMargin);
		cs.setFont(PDType1Font.HELVETICA_BOLD, 24);
		cs.showText("Gyrobian Site Performance Report");
		cs.newLineAtOffset(0, -30);
		cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 16);
		cs.setNonStrokingColor(Color.DARK_GRAY);
		cs.showText("For the ");
		cs.setFont(PDType1Font.HELVETICA_BOLD_OBLIQUE, 16);
		cs.showText(data.siteName());
		cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 16);
		cs.showText(" site.");
		cs.newLineAtOffset(0, -20);
		cs.setFont(PDType1Font.HELVETICA, 12);
		cs.setNonStrokingColor(Color.BLACK);
		cs.showText("Measured from ");
		cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
		cs.showText(String.format("%s to %s", data.startDate().format(DATE_FORMAT), data.endDate().format(DATE_FORMAT)));
		cs.newLineAtOffset(0, -16);
		cs.setFont(PDType1Font.HELVETICA, 12);
		cs.showText("This report was generated at ");
		cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
		cs.showText(data.generatedAt().format(OFFSET_DATETIME_FORMAT));
		cs.newLineAtOffset(0, -16);
		cs.setFont(PDType1Font.HELVETICA, 12);
		cs.showText(String.format("%d files were processed in %d ms.", data.fileCount(), data.measurementDuration()));
		cs.endText();
		// Draw line.
		cs.setStrokingColor(GYROBIAN_PURPLE);
		cs.setLineWidth(3);
		cs.moveTo(leftMargin, topMargin - 100);
		cs.lineTo(rightMargin, topMargin - 100);
		cs.stroke();
	}

	private void writePdfGeneralStats(PDPageContentStream cs, PDPage page, PerformanceData data) throws IOException {
		cs.beginText();
		float tx = page.getCropBox().getLowerLeftX() + LEFT_MARGIN;
		float ty = page.getCropBox().getUpperRightY() - TOP_MARGIN - 150;
		writeLine(cs, tx, ty, PDType1Font.HELVETICA_BOLD, 18, "General Information");
		writeLine(cs, -20, "Uptime: ");
		writeMonospaceBold(cs, String.format("%.4f%%", data.uptimePercent()));
		writeLine(cs, -16, "Average Response Time: ");
		writeMonospaceBold(cs, String.format("%.4f ms", data.averageResponseTime()));

		writeLine(cs, -16, "Successful Request Percentage: ");
		writeMonospaceBold(cs, String.format("%.2f%%", data.successPercent()));
		writeLine(cs, -16, "Total Uptime: ");
		writeMonospaceBold(cs, String.format("%dd %02dh %02dm %02ds", data.totalUptime().toDays(), data.totalUptime().toHoursPart(), data.totalUptime().toMinutesPart(), data.totalUptime().toSecondsPart()));
		writeLine(cs, -16, "Total Downtime: ");
		writeMonospaceBold(cs, String.format("%dd %02dh %02dm %02ds", data.totalDowntime().toDays(), data.totalDowntime().toHoursPart(), data.totalDowntime().toMinutesPart(), data.totalDowntime().toSecondsPart()));
		writeLine(cs, -16, "Total Measurements Recorded: ");
		writeMonospaceBold(cs, String.valueOf(data.entries().length));
		cs.endText();
	}

	private void writeEntries(PDDocument doc, PDPage page, PDPageContentStream cs, PerformanceData data) throws IOException {
		cs.beginText();
		cs.setFont(PDType1Font.HELVETICA_BOLD, 18);
		cs.newLineAtOffset(LEFT_MARGIN, page.getMediaBox().getUpperRightY() - (TOP_MARGIN + 240));
		cs.showText("All Recorded Entries");
		cs.setFont(PDType1Font.HELVETICA, 12);
		cs.newLineAtOffset(0, -20);
		cs.showText("The following table shows a list of every recorded measurement.");
		cs.endText();

		BaseTable table = new BaseTable(
				page.getMediaBox().getUpperRightY() - (TOP_MARGIN + 270),
				page.getMediaBox().getHeight() - (2 * TOP_MARGIN),
				TOP_MARGIN,
				page.getMediaBox().getWidth() - (2 * LEFT_MARGIN),
				LEFT_MARGIN,
				doc,
				page,
				true,
				true
		);

		Row<PDPage> headerRow = table.createRow(10f);
		headerRow.createCell(25, "Timestamp");
		headerRow.createCell(35, "URL");
		headerRow.createCell(16, "Response Code");
		headerRow.createCell(20, "Response Time (ms)");
		for (var cell : headerRow.getCells()) {
			cell.setFont(PDType1Font.HELVETICA_BOLD);
		}
		table.addHeaderRow(headerRow);

		for (var entry : data.entries()) {
			Row<PDPage> row = table.createRow(10);
			row.createCell(entry.timestamp().toString());
			row.createCell(entry.url());
			row.createCell(String.valueOf(entry.responseCode()));
			row.createCell(String.valueOf(entry.responseTime()));
			for (var cell : row.getCells()) {
				cell.setTopPadding(2);
				cell.setBottomPadding(2);
				cell.setLeftPadding(2);
				cell.setRightPadding(2);
			}
		}

		table.draw();
	}

	private void writeLine(PDPageContentStream cs, float tx, float ty, PDFont font, float fontSize, String text) throws IOException {
		cs.newLineAtOffset(tx, ty);
		cs.setFont(font, fontSize);
		cs.showText(text);
	}

	private void writeLine(PDPageContentStream cs, float ty, PDFont font, float fontSize, String text) throws IOException {
		writeLine(cs, 0, ty, font, fontSize, text);
	}

	private void writeLine(PDPageContentStream cs, float ty, String text) throws IOException {
		writeLine(cs, ty, PDType1Font.HELVETICA, 12, text);
	}

	private void writeMonospaceBold(PDPageContentStream cs, String text) throws IOException {
		cs.setFont(PDType1Font.COURIER_BOLD, 12);
		cs.showText(text);
	}
}
