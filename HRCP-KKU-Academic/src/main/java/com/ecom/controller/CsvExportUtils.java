package com.ecom.controller;

/**
 * CSV field escaping for admin exports.
 *
 * Audit-log fields carry text an unauthenticated visitor can choose (the email
 * box on the login form is recorded on every failed attempt), and those exports
 * get opened in Excel. A field that begins with one of the formula triggers is
 * evaluated rather than displayed, so it is prefixed with an apostrophe — the
 * conventional way to force a spreadsheet to treat the value as text.
 */
public final class CsvExportUtils {

    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    private CsvExportUtils() {
    }

    public static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        String escaped = value.replace("\"", "\"\"");

        if (!escaped.isEmpty() && FORMULA_TRIGGERS.indexOf(escaped.charAt(0)) >= 0) {
            escaped = "'" + escaped;
        }

        return escaped;
    }
}
