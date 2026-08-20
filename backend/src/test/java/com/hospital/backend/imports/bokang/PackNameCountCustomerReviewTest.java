package com.hospital.backend.imports.bokang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.service.BillRowFieldConsistencyValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户《数量比对.xlsx》106 条复核样本回归：验证 T01–T99 包名计数解析与字段核对已落地。
 * <p>夹具 {@code pack-name-count-customer-review.json} 由 scripts 与客户表对齐生成。</p>
 */
class PackNameCountCustomerReviewTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static List<ReviewCase> cases;

    @BeforeAll
    static void loadFixture() throws Exception {
        try (InputStream in = PackNameCountCustomerReviewTest.class
                .getResourceAsStream("/pack-name-count-customer-review.json")) {
            assertThat(in).as("pack-name-count-customer-review.json").isNotNull();
            JsonNode root = MAPPER.readTree(in);
            cases = new ArrayList<>();
            for (JsonNode node : root.path("cases")) {
                cases.add(new ReviewCase(
                        node.path("id").asText(),
                        node.path("typeId").asText(),
                        node.path("packName").asText(),
                        node.path("packCount").asInt(),
                        node.path("billInstrumentCount").isNull()
                                ? null
                                : node.path("billInstrumentCount").asInt(),
                        node.path("expectedPerPack").isNull()
                                ? null
                                : node.path("expectedPerPack").asInt(),
                        node.path("expectedTotal").isNull()
                                ? null
                                : node.path("expectedTotal").asInt(),
                        node.path("behavior").asText()));
            }
        }
        assertThat(cases).as("customer review cases").isNotEmpty();
    }

    static Stream<ReviewCase> parserCases() {
        return cases.stream().filter(c -> c.expectedPerPack() != null);
    }

    static Stream<ReviewCase> allCases() {
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} {1} perPack={5}")
    @MethodSource("parserCases")
    void parserMatchesCustomerExpectedPerPack(ReviewCase reviewCase) {
        assertThat(PackNameSpecParser.extractTotalPieceCountFromPackName(reviewCase.packName()))
                .as("%s %s", reviewCase.id(), reviewCase.typeId())
                .isEqualTo(reviewCase.expectedPerPack());
    }

    @ParameterizedTest(name = "{0} {1} total={6}")
    @MethodSource("parserCases")
    void parserTimesPackCountMatchesExpectedTotal(ReviewCase reviewCase) {
        int perPack = PackNameSpecParser.extractTotalPieceCountFromPackName(reviewCase.packName());
        assertThat(perPack).isNotNull();
        assertThat(perPack * reviewCase.packCount())
                .isEqualTo(reviewCase.expectedTotal());
    }

    @Test
    void fixtureCoversAllTaxonomyTypesFromCustomerReview() {
        assertThat(cases.stream().map(ReviewCase::typeId).distinct().sorted().toList())
                .contains(
                        "T01", "T03", "T04", "T05", "T06", "T07", "T08", "T09", "T10",
                        "T11", "T12", "T13", "T99");
    }

    @Test
    void matchCasesProduceNoInstrumentCountViolation() {
        long matchCount = cases.stream().filter(c -> "match".equals(c.behavior())).count();
        assertThat(matchCount).isGreaterThanOrEqualTo(50);

        for (ReviewCase reviewCase : cases) {
            if (!"match".equals(reviewCase.behavior())) {
                continue;
            }
            List<BillRowFieldConsistencyValidator.Violation> violations =
                    BillRowFieldConsistencyValidator.validate(
                            "额外包(纸塑袋)",
                            reviewCase.packName(),
                            "高温纸塑袋75*200",
                            reviewCase.billInstrumentCount(),
                            reviewCase.packCount());
            assertThat(violations)
                    .extracting(BillRowFieldConsistencyValidator.Violation::code)
                    .as("%s %s", reviewCase.id(), reviewCase.packName())
                    .doesNotContain(BillRowFieldConsistencyValidator.CODE_INSTRUMENT_COUNT_MISMATCH);
        }
    }

    @Test
    void mismatchCasesFlagInstrumentCountViolation() {
        long mismatchCount = cases.stream().filter(c -> "mismatch".equals(c.behavior())).count();
        assertThat(mismatchCount).isGreaterThanOrEqualTo(15);

        for (ReviewCase reviewCase : cases) {
            if (!"mismatch".equals(reviewCase.behavior())) {
                continue;
            }
            List<BillRowFieldConsistencyValidator.Violation> violations =
                    BillRowFieldConsistencyValidator.validate(
                            "额外包(纸塑袋)",
                            reviewCase.packName(),
                            "高温纸塑袋75*200",
                            reviewCase.billInstrumentCount(),
                            reviewCase.packCount());
            assertThat(violations)
                    .extracting(BillRowFieldConsistencyValidator.Violation::code)
                    .as("%s %s bill=%d expect=%d", reviewCase.id(), reviewCase.packName(),
                            reviewCase.billInstrumentCount(), reviewCase.expectedTotal())
                    .contains(BillRowFieldConsistencyValidator.CODE_INSTRUMENT_COUNT_MISMATCH);
        }
    }

    @Test
    void skipCasesDoNotFlagInstrumentCountMismatch() {
        for (ReviewCase reviewCase : cases) {
            if (!"skip".equals(reviewCase.behavior())) {
                continue;
            }
            if (reviewCase.billInstrumentCount() == null) {
                continue;
            }
            List<BillRowFieldConsistencyValidator.Violation> violations =
                    BillRowFieldConsistencyValidator.validate(
                            "额外包(纸塑袋)",
                            reviewCase.packName(),
                            "高温纸塑袋75*200",
                            reviewCase.billInstrumentCount(),
                            reviewCase.packCount());
            assertThat(violations)
                    .extracting(BillRowFieldConsistencyValidator.Violation::code)
                    .as("%s %s", reviewCase.id(), reviewCase.packName())
                    .doesNotContain(BillRowFieldConsistencyValidator.CODE_INSTRUMENT_COUNT_MISMATCH);
        }
    }

    @Test
    void zeroInstrumentDressingRowsSkipCountCheck() {
        for (ReviewCase reviewCase : cases) {
            if (!"skip_zero_dressing".equals(reviewCase.behavior())) {
                continue;
            }
            List<BillRowFieldConsistencyValidator.Violation> violations =
                    BillRowFieldConsistencyValidator.validate(
                            "敷料包(纸塑袋)",
                            reviewCase.packName(),
                            "高温纸塑袋75*200",
                            0,
                            reviewCase.packCount());
            assertThat(violations)
                    .extracting(BillRowFieldConsistencyValidator.Violation::code)
                    .doesNotContain(BillRowFieldConsistencyValidator.CODE_INSTRUMENT_COUNT_MISMATCH);
        }
    }

    @Test
    void implicitSinglePieceCasesValidateAgainstPackCount() {
        List<ReviewCase> implicit = cases.stream()
                .filter(c -> c.packName().startsWith("持针器/"))
                .filter(c -> c.billInstrumentCount() != null && c.billInstrumentCount().equals(c.packCount()))
                .toList();
        assertThat(implicit).isNotEmpty();
        for (ReviewCase reviewCase : implicit) {
            assertThat(PackNameSpecParser.isImplicitSinglePiecePerPack(reviewCase.packName())).isTrue();
            List<BillRowFieldConsistencyValidator.Violation> violations =
                    BillRowFieldConsistencyValidator.validate(
                            "额外包(纸塑袋)",
                            reviewCase.packName(),
                            "高温纸塑袋75*200",
                            reviewCase.billInstrumentCount(),
                            reviewCase.packCount());
            assertThat(violations)
                    .extracting(BillRowFieldConsistencyValidator.Violation::code)
                    .doesNotContain(BillRowFieldConsistencyValidator.CODE_INSTRUMENT_COUNT_MISMATCH);
        }
    }

    private record ReviewCase(
            String id,
            String typeId,
            String packName,
            int packCount,
            Integer billInstrumentCount,
            Integer expectedPerPack,
            Integer expectedTotal,
            String behavior) {}
}
