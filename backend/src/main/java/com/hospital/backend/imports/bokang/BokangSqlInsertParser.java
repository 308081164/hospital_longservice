package com.hospital.backend.imports.bokang;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses MySQL INSERT ... VALUES (...) lines from铂康 SQL dumps.
 */
public final class BokangSqlInsertParser {

    private BokangSqlInsertParser() {
    }

    public static List<String> parseValues(String insertLine) {
        int start = insertLine.indexOf("VALUES (");
        if (start < 0) {
            return List.of();
        }
        int i = start + "VALUES (".length();
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        char stringQuote = 0;

        while (i < insertLine.length()) {
            char c = insertLine.charAt(i);

            if (inString) {
                if (c == '\\' && i + 1 < insertLine.length()) {
                    current.append(insertLine.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == stringQuote) {
                    inString = false;
                    i++;
                    continue;
                }
                current.append(c);
                i++;
                continue;
            }

            if (c == '\'' || c == '"') {
                inString = true;
                stringQuote = c;
                i++;
                continue;
            }

            if (c == '(') {
                i++;
                continue;
            }

            if (c == ')' || c == ';') {
                values.add(normalizeToken(current.toString()));
                break;
            }

            if (c == ',') {
                values.add(normalizeToken(current.toString()));
                current.setLength(0);
                i++;
                continue;
            }

            current.append(c);
            i++;
        }

        return values;
    }

    private static String normalizeToken(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "NULL".equalsIgnoreCase(trimmed)) {
            return null;
        }
        if (trimmed.startsWith("b'") && trimmed.endsWith("'")) {
            return trimmed.substring(2, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static String extractPackNameStem(String packName) {
        if (packName == null || packName.isBlank()) {
            return null;
        }
        String stem = packName;
        int dash = stem.indexOf('-');
        if (dash > 0) {
            stem = stem.substring(0, dash);
        } else {
            int slash = stem.indexOf('/');
            if (slash > 0) {
                stem = stem.substring(0, slash);
            }
        }
        return stem.trim();
    }
}
