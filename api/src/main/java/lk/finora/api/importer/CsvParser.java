package lk.finora.api.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * Small RFC 4180 CSV reader: comma separator, double-quoted cells, {@code ""} for a quote inside quotes,
 * LF or CRLF line ends, line breaks allowed inside quoted cells. A quote in the middle of an unquoted cell
 * is kept as text (e.g. {@code =HYPERLINK("x")}) so the row-level checks can report it. Cells are returned
 * exactly as written (no trimming), so the formula-injection check sees the real first character.
 */
final class CsvParser {

    /** One record and the file line it starts on (1-based). */
    record Record(int line, List<String> cells, List<Boolean> quoted) {

        boolean isBlank() {
            return cells.size() == 1 && cells.get(0).isBlank() && !quoted.get(0);
        }
    }

    /** The file is not valid CSV, e.g. a quote that is never closed. */
    static class CsvFormatException extends RuntimeException {
        CsvFormatException(String message) {
            super(message);
        }
    }

    private CsvParser() {
    }

    static List<Record> parse(String text) {
        List<Record> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        List<Boolean> quoted = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean cellQuoted = false;
        int line = 1;
        int recordLine = 1;
        int i = 0;
        int n = text.length();

        while (i < n) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    cell.append(c);
                }
                i++;
                continue;
            }

            if (c == '"' && cellQuoted) {
                throw new CsvFormatException("Line " + line + ": text after a closing quote");
            } else if (c == '"' && cell.length() == 0) {
                inQuotes = true;
                cellQuoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                quoted.add(cellQuoted);
                cell.setLength(0);
                cellQuoted = false;
            } else if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') {
                // CRLF: the LF ends the record on the next step
            } else if (c == '\n') {
                cells.add(cell.toString());
                quoted.add(cellQuoted);
                records.add(new Record(recordLine, cells, quoted));
                cells = new ArrayList<>();
                quoted = new ArrayList<>();
                cell.setLength(0);
                cellQuoted = false;
                line++;
                recordLine = line;
            } else {
                if (cellQuoted) {
                    throw new CsvFormatException("Line " + line + ": text after a closing quote");
                }
                cell.append(c);
            }
            i++;
        }

        if (inQuotes) {
            throw new CsvFormatException("Line " + recordLine + ": a quoted cell is never closed");
        }
        // Last record when the file does not end with a line break
        if (cell.length() > 0 || cellQuoted || !cells.isEmpty()) {
            cells.add(cell.toString());
            quoted.add(cellQuoted);
            records.add(new Record(recordLine, cells, quoted));
        }
        return records;
    }
}
