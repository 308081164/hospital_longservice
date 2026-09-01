package com.hospital.backend.service;

import com.hospital.backend.imports.bokang.PackNameSpecParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BillRowFieldConsistencyValidatorTest {

    @Test
    void screenshotRowReportsBagSizeMismatchOnly() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "支抗钉-3 75*20",
                        "高温纸塑袋75*200",
                        3,
                        1);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code())
                .isEqualTo(BillRowFieldConsistencyValidator.CODE_BAG_SIZE_MISMATCH);
        assertThat(violations.get(0).message()).contains("75*20").contains("75*200");
    }

    @Test
    void screenshotRowWithWrongInstrumentCountAddsSecondViolation() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "支抗钉-3 75*20",
                        "高温纸塑袋75*200",
                        2,
                        1);

        assertThat(violations).extracting(BillRowFieldConsistencyValidator.Violation::code)
                .containsExactly(
                        BillRowFieldConsistencyValidator.CODE_BAG_SIZE_MISMATCH,
                        BillRowFieldConsistencyValidator.CODE_INSTRUMENT_COUNT_MISMATCH);
    }

    @Test
    void matchingMaterialClassWithoutDimensionsIsClean() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "剪刀-3/z1530",
                        "高温纸塑袋75*200",
                        3,
                        1);

        assertThat(violations).isEmpty();
    }

    @Test
    void materialClassMismatchIsReported() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "剪刀-3/z1530",
                        "低温无纺布30*40",
                        3,
                        1);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code())
                .isEqualTo(BillRowFieldConsistencyValidator.CODE_MATERIAL_CLASS_MISMATCH);
    }

    @Test
    void multiInstrumentPackNameMatchesTotalInstrumentCount() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "止血钳-2剪-1/Z1530",
                        "高温纸塑袋75*200",
                        3,
                        1);

        assertThat(violations).isEmpty();
    }

    @Test
    void multiInstrumentPackNameWrongInstrumentCountReportsViolation() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "止血钳-2剪-1/Z1530",
                        "高温纸塑袋75*200",
                        2,
                        1);

        assertThat(violations).extracting(BillRowFieldConsistencyValidator.Violation::code)
                .containsExactly(BillRowFieldConsistencyValidator.CODE_INSTRUMENT_COUNT_MISMATCH);
    }

    @Test
    void compactPackNameWithoutHyphenMatchesInstrumentCount() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "排针20/Z1026",
                        "高温纸塑袋75*200",
                        140,
                        7);

        assertThat(violations).isEmpty();
    }

    @Test
    void compactPackNameWithoutHyphenWrongInstrumentCountReportsViolation() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "排针20/Z1026",
                        "高温纸塑袋75*200",
                        100,
                        7);

        assertThat(violations).extracting(BillRowFieldConsistencyValidator.Violation::code)
                .containsExactly(BillRowFieldConsistencyValidator.CODE_INSTRUMENT_COUNT_MISMATCH);
    }

    @Test
    void hyphenPackNameWrongInstrumentCountStillReportsViolation() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "排针-12/Z7526",
                        "高温纸塑袋75*200",
                        2,
                        1);

        assertThat(violations).extracting(BillRowFieldConsistencyValidator.Violation::code)
                .containsExactly(BillRowFieldConsistencyValidator.CODE_INSTRUMENT_COUNT_MISMATCH);
    }

    @Test
    void multiInstrumentPackNameWithMultiplePacksMatchesExpectedTotal() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "止血钳-2剪-1/Z1530",
                        "高温纸塑袋75*200",
                        6,
                        2);

        assertThat(violations).isEmpty();
    }

    @Test
    void compactCompoundPackNamePenBowlMatchesTotalInstrumentCount() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(无纺布)",
                        "盆1碗1/W9050",
                        "无纺布-90×90-50g",
                        8,
                        4);

        assertThat(violations).isEmpty();
    }

    @Test
    void compactCompoundPackNameMultiItemMatchesTotalInstrumentCount() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(无纺布)",
                        "盆1碗2盘2杯1/W9050",
                        "无纺布-90×90-50g",
                        6,
                        1);

        assertThat(violations).isEmpty();
    }

    @Test
    void boxContainerPackNameMatchesInstrumentCount() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "机扩针-6盒1/z1526",
                        "高温纸塑袋75*200",
                        7,
                        1);

        assertThat(violations).isEmpty();
    }

    @Test
    void parenthesisBasketPackNameMatchesInstrumentCount() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "外科器械包-9（筐1）/w7050",
                        "高温纸塑袋75*200",
                        10,
                        1);

        assertThat(violations).isEmpty();
    }

    @Test
    void needleRackPackNameSkipsInstrumentCountCheck() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "车针架1针4/Z1026",
                        "高温纸塑袋75*200",
                        1,
                        1);

        assertThat(violations).extracting(BillRowFieldConsistencyValidator.Violation::code)
                .doesNotContain(BillRowFieldConsistencyValidator.CODE_INSTRUMENT_COUNT_MISMATCH);
    }

    @Test
    void zeroInstrumentDressingRowSkipsInstrumentCountCheck() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "敷料包(纸塑袋)",
                        "弯盘-1/z2032",
                        "高温纸塑袋75*200",
                        0,
                        1);

        assertThat(violations).extracting(BillRowFieldConsistencyValidator.Violation::code)
                .doesNotContain(BillRowFieldConsistencyValidator.CODE_INSTRUMENT_COUNT_MISMATCH);
    }

    @Test
    void phoneModelPackNameSkipsInstrumentCountCheck() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "手机721001/z7526",
                        "高温纸塑袋75*200",
                        1,
                        1);

        assertThat(violations).extracting(BillRowFieldConsistencyValidator.Violation::code)
                .doesNotContain(BillRowFieldConsistencyValidator.CODE_INSTRUMENT_COUNT_MISMATCH);
    }

    @Test
    void standalonePieceCountPackNameMatchesInstrumentCount() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "宫腔镜包26件",
                        "高温纸塑袋75*200",
                        26,
                        1);

        assertThat(violations).isEmpty();
    }

    @Test
    void parenBoxPieceTotalMatchesInstrumentCount() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "史赛克摆锯骨动力-4（带盒5件）",
                        "高温纸塑袋75*200",
                        5,
                        1);

        assertThat(violations).isEmpty();
    }

    @Test
    void implicitSinglePieceOrderCodeMatchesPackCount() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "持针器/z1029",
                        "高温纸塑袋75*200",
                        6,
                        6);

        assertThat(violations).isEmpty();
    }

    @Test
    void implicitSinglePieceOrderCodeWrongInstrumentCountReportsViolation() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "持针器/z1029",
                        "高温纸塑袋75*200",
                        5,
                        6);

        assertThat(violations).extracting(BillRowFieldConsistencyValidator.Violation::code)
                .containsExactly(BillRowFieldConsistencyValidator.CODE_INSTRUMENT_COUNT_MISMATCH);
    }

    @Test
    void customerReviewCompactPlateBowlCupMatchesInstrumentCount() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(无纺布)",
                        "盘1碗1杯1/w6050",
                        "无纺布-90×90-50g",
                        6,
                        2);

        assertThat(violations).isEmpty();
    }

    @Test
    void customerReviewGluedOrderCodeMatchesInstrumentCount() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "钩镊-1z7526",
                        "高温纸塑袋75*200",
                        8,
                        8);

        assertThat(violations).isEmpty();
    }

    @Test
    void customerReviewLensSpacedBoxDoesNotAddExtraPiece() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "激光镜-4件 盒1",
                        "高温纸塑袋75*200",
                        4,
                        1);

        assertThat(violations).isEmpty();
    }

    @Test
    void toBillingNotesBuildsStructuredPayload() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "支抗钉-3 75*20",
                        "高温纸塑袋75*200",
                        2,
                        1);

        var billingNotes = BillRowFieldConsistencyValidator.toBillingNotes(violations);
        assertThat(billingNotes).isNotNull();
        assertThat(billingNotes.get("type")).isEqualTo("field_consistency");
        assertThat(billingNotes.get("violations")).isInstanceOf(List.class);
        assertThat((List<?>) billingNotes.get("violations")).hasSize(2);
    }

    @Test
    void hulanRedCrossGynecologyPackWithPieceBoxMatchesInstrumentCount() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(无纺布)",
                        "切开包-13件盒1",
                        "无纺布-90×90-50g",
                        14,
                        1);

        assertThat(violations).isEmpty();
    }

    @Test
    void hulanRedCrossGynecologyPacksMatchInstrumentCount() {
        for (String packName : List.of("阴切包-10件盒1", "产包-16件盒1")) {
            int expected = PackNameSpecParser.extractTotalPieceCountFromPackName(packName);
            List<BillRowFieldConsistencyValidator.Violation> violations =
                    BillRowFieldConsistencyValidator.validate(
                            "额外包(无纺布)",
                            packName,
                            "无纺布-90×90-50g",
                            expected,
                            1);
            assertThat(violations).as(packName).isEmpty();
        }
    }
}
