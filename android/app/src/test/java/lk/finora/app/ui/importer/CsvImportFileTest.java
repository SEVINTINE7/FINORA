package lk.finora.app.ui.importer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class CsvImportFileTest {
    @Test public void acceptsBomAndQuotedHeaderWithoutChangingBytes() throws IOException {
        byte[] csv = ("\uFEFF\"DATE\",type,amount,category,account,merchant,note\r\n"
                + "2026-11-23,EXPENSE,100.09,,Cash,කඩය,\r\n").getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(csv, CsvImportFile.read(new ByteArrayInputStream(csv)));
    }

    @Test public void rejectsOversizeMalformedUtf8AndUnrelatedText() {
        assertThrows(CsvImportFile.TooLarge.class, () -> CsvImportFile.read(
                new ByteArrayInputStream(new byte[CsvImportFile.MAX_BYTES + 1])));
        assertThrows(IOException.class, () -> CsvImportFile.read(
                new ByteArrayInputStream(new byte[]{(byte) 0xC3, 0x28})));
        assertThrows(IOException.class, () -> CsvImportFile.read(
                new ByteArrayInputStream("not a CSV file".getBytes(StandardCharsets.UTF_8))));
    }

    @Test public void treatsTransportFailureAsAnUncertainConfirmation() {
        assertTrue(CsvImportViewModel.isUncertain(new IOException("connection dropped")));
    }
}
