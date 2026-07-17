package com.hospital.backend.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RowSplitterTest {

    @Test
    void splitsRowWhenFoldRatioDoesNotDivideEvenly() throws Exception {
        ObjectNode rules = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().createObjectNode();
        ObjectNode specialRules = rules.putObject("specialRules");
        ArrayNode foldRules = specialRules.putArray("foldRules");
        ObjectNode fold = foldRules.addObject();
        fold.put("name", "排针折算");
        fold.put("foldRatio", 5);
        fold.put("threshold", 5);
        fold.putArray("keywords").add("冲洗头");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "器械包");
        row.put("packName", "冲洗头");
        row.put("packageMaterial", "无纺布");
        row.put("instrumentCount", 82);
        row.put("packCount", 1);
        row.put("rowNumber", 10);

        List<Map<String, Object>> expanded = RowSplitter.expandRow(row, rules);
        assertThat(expanded).hasSizeGreaterThan(1);
        int totalInstruments = expanded.stream()
                .mapToInt(r -> (Integer) r.get("instrumentCount"))
                .sum();
        assertThat(totalInstruments).isEqualTo(82);
    }

    @Test
    void keepsSingleRowWhenEvenlyDivisible() throws Exception {
        ObjectNode rules = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().createObjectNode();
        ObjectNode specialRules = rules.putObject("specialRules");
        ArrayNode foldRules = specialRules.putArray("foldRules");
        ObjectNode fold = foldRules.addObject();
        fold.put("name", "5件折算");
        fold.put("foldRatio", 5);
        fold.put("threshold", 5);
        fold.putArray("keywords").add("冲洗头");

        Map<String, Object> row = Map.of(
                "type", "器械包",
                "packName", "冲洗头",
                "packageMaterial", "无纺布",
                "instrumentCount", 80,
                "packCount", 1
        );

        assertThat(RowSplitter.expandRow(row, rules)).hasSize(1);
    }

    @Test
    void splitInstrumentCountProducesExpectedSegments() {
        List<RowSplitter.SplitSegment> segments = RowSplitter.splitInstrumentCount(82, 5, 5);
        assertThat(segments).isNotEmpty();
        int sum = segments.stream().mapToInt(RowSplitter.SplitSegment::instrumentCount).sum();
        assertThat(sum).isEqualTo(82);
    }
}
