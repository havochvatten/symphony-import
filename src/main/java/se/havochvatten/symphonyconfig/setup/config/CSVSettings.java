package se.havochvatten.symphonyconfig.setup.config;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.apache.commons.io.input.BOMInputStream;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CSVSettings {

    // Previous import scripts for Symphony metadata have presupposed a nonstandard
    // CSV format (using semicolon as the field delimiter).

    private char separator = ';';
    private String newLine = "\n";
    private char quoteChar = '"';
    private char escapeChar = '\\';

    public static InputStream getBOMSafeStream(String path) throws IOException {
        return BOMInputStream.builder().setInputStream(new FileInputStream(path)).get();
    }

    public CSVFormat getFormat() {
        return CSVFormat.DEFAULT.builder()
            .setDelimiter(separator)
            .setRecordSeparator(newLine)
            .setQuote(quoteChar)
            .setEscape(escapeChar)
            .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
            .build();
    }

    public CSVSettings() {}

    public CSVSettings(Character _separator, String _newLine) {
        if (_separator != null) this.separator = _separator;
        if (_newLine != null)   this.newLine = _newLine;
    }

    public CSVSettings(char separator, String newLine, char quoteChar) {
        this(separator, newLine);
        this.quoteChar = quoteChar;
    }

    public CSVSettings(char separator, String newLine, char quoteChar, char escapeChar) {
        this(separator, newLine, quoteChar);
        this.escapeChar = escapeChar;
    }
}
