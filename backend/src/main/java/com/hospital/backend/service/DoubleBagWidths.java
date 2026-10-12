package com.hospital.backend.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 双层袋内外层：宽度（毫米尺寸里 * 前的数）大的是外层，不看材料列书写顺序。
 */
public final class DoubleBagWidths {

    private static final Pattern WIDTH_MM = Pattern.compile("(\\d+)\\s*[*×xX]\\s*\\d+");

    public record Pair(int outerWidthMm, int innerWidthMm) {}

    private DoubleBagWidths() {}

    public static int widthMm(String segment) {
        if (segment == null || segment.isBlank()) {
            return 0;
        }
        Matcher matcher = WIDTH_MM.matcher(segment);
        if (!matcher.find()) {
            return 0;
        }
        return Integer.parseInt(matcher.group(1));
    }

    /** 两段材料都有宽度时，较宽的一段是外层。宽度相同则保持原顺序。 */
    public static Pair order(String firstSegment, String secondSegment) {
        int first = widthMm(firstSegment);
        int second = widthMm(secondSegment);
        if (second > first) {
            return new Pair(second, first);
        }
        return new Pair(first, second);
    }
}
