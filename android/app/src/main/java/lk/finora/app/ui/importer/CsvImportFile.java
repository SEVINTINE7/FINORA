package lk.finora.app.ui.importer;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class CsvImportFile {
    static final int MAX_BYTES = 1024 * 1024;
    private static final String[] HEADER = {"date", "type", "amount", "category", "account", "merchant", "note"};

    private CsvImportFile() {}

    static byte[] read(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("Missing import stream");
        var buffer = new byte[8192];
        var output = new ByteArrayOutputStream();
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Import canceled");
            if (count > MAX_BYTES - output.size()) throw new TooLarge();
            output.write(buffer, 0, count);
        }
        byte[] bytes = output.toByteArray();
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        if (text.indexOf('\0') >= 0 || !hasHeader(text)) {
            throw new IOException("Expected UTF-8 CSV text");
        }
        return bytes;
    }

    private static boolean hasHeader(String text) throws IOException {
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        var reader = new BufferedReader(new StringReader(text));
        String line;
        do { line = reader.readLine(); } while (line != null && line.trim().isEmpty());
        if (line == null) return false;
        String[] cells = line.split(",", -1);
        if (cells.length != HEADER.length) return false;
        for (int index = 0; index < cells.length; index++) {
            String cell = cells[index].trim();
            if (cell.length() >= 2 && cell.startsWith("\"") && cell.endsWith("\"")) {
                cell = cell.substring(1, cell.length() - 1).trim();
            }
            if (!HEADER[index].equalsIgnoreCase(cell)) return false;
        }
        return true;
    }

    static final class TooLarge extends IOException {}
}
