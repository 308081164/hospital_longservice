package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BillRowBillingValidatorTest {

    @Test
    void dressingPackWithBlankPackagingIsExempt() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("敷料包(纸塑袋)", "", 5);

        assertThat(violations).isEmpty();
    }

    @Test
    void dressingPackWithZeroInstrumentCountIsExempt() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("敷料包(无纺布)", "无纺布-90×90-50g", 0);

        assertThat(violations).isEmpty();
    }

    @Test
    void dressingPackWithBlankPackagingAndZeroCountIsFullyExempt() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("敷料包(纸塑袋)", null, 0);

        assertThat(violations).isEmpty();
    }

    @Test
    void nonDressingPackWithBlankPackagingReportsViolation() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(纸塑袋)", "", 3);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_BLANK_PACKAGE_MATERIAL);
        assertThat(violations.get(0).severity())
                .isEqualTo(BillRowBillingValidator.SEVERITY_ERROR);
        assertThat(violations.get(0).message()).contains("包装材料为空");
    }

    @Test
    void nonDressingPackWithWhitespacePackagingReportsViolation() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(无纺布)", "   ", 3);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_BLANK_PACKAGE_MATERIAL);
    }

    @Test
    void nonDressingPackWithNullPackagingReportsViolation() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(纸塑袋)", null, 3);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_BLANK_PACKAGE_MATERIAL);
    }

    @Test
    void nonDressingPackWithZeroInstrumentCountReportsViolation() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(纸塑袋)", "高温纸塑袋75*200", 0);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_ZERO_INSTRUMENT_COUNT);
        assertThat(violations.get(0).severity())
                .isEqualTo(BillRowBillingValidator.SEVERITY_ERROR);
        assertThat(violations.get(0).message()).contains("器械数为0");
    }

    @Test
    void nonDressingPackWithBlankPackagingAndZeroCountReportsBoth() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("器械包(ZSD)", "", 0);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(
                        BillRowBillingValidator.CODE_BLANK_PACKAGE_MATERIAL,
                        BillRowBillingValidator.CODE_ZERO_INSTRUMENT_COUNT);
    }

    @Test
    void nonDressingPackWithBothFilledIsClean() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(纸塑袋)", "高温纸塑袋75*200", 3);

        assertThat(violations).isEmpty();
    }

    @Test
    void blankTypeIsTreatedAsNonDressingAndValidated() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate(null, "", 0);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(
                        BillRowBillingValidator.CODE_BLANK_PACKAGE_MATERIAL,
                        BillRowBillingValidator.CODE_ZERO_INSTRUMENT_COUNT);
    }

    @Test
    void zeroUnitPriceOnNonDressingPackReportsViolation() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(纸塑袋)", "高温纸塑袋75*200", 3, 0.0);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_ZERO_UNIT_PRICE);
        assertThat(violations.get(0).severity())
                .isEqualTo(BillRowBillingValidator.SEVERITY_ERROR);
        assertThat(violations.get(0).message()).contains("单价为 0");
    }

    @Test
    void zeroUnitPriceOnDressingPackIsNotExempt() {
        // 敷料包豁免仅针对包装材料/器械数；0 元导入的敷料包正是需要被发现的行
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("敷料包(纸塑袋)", "", 0, 0.0);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_ZERO_UNIT_PRICE);
    }

    @Test
    void positiveUnitPriceHasNoZeroPriceViolation() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(纸塑袋)", "高温纸塑袋75*200", 3, 8.0);

        assertThat(violations).isEmpty();
    }

    @Test
    void nullUnitPriceSkipsZeroPriceCheck() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(纸塑袋)", "高温纸塑袋75*200", 3, null);

        assertThat(violations).isEmpty();
    }

    @Test
    void zeroUnitPriceCombinesWithOtherViolations() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("器械包(ZSD)", "", 0, 0.0);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(
                        BillRowBillingValidator.CODE_ZERO_UNIT_PRICE,
                        BillRowBillingValidator.CODE_BLANK_PACKAGE_MATERIAL,
                        BillRowBillingValidator.CODE_ZERO_INSTRUMENT_COUNT);
    }

    @Test
    void toBillingNotesBuildsBillingValidationPayload() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(纸塑袋)", "", 0);

        Map<String, Object> billingNotes = BillRowBillingValidator.toBillingNotes(violations);

        assertThat(billingNotes).isNotNull();
        assertThat(billingNotes.get("type")).isEqualTo("billing_validation");
        assertThat(billingNotes.get("violations")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) billingNotes.get("violations");
        assertThat(items).hasSize(2);
        assertThat(items.get(0))
                .containsEntry("code", BillRowBillingValidator.CODE_BLANK_PACKAGE_MATERIAL)
                .containsEntry("severity", "error")
                .containsEntry("message", "包装材料为空");
        assertThat(items.get(1))
                .containsEntry("code", BillRowBillingValidator.CODE_ZERO_INSTRUMENT_COUNT)
                .containsEntry("severity", "error");
    }

    @Test
    void toBillingNotesReturnsNullWhenNoViolations() {
        assertThat(BillRowBillingValidator.toBillingNotes(List.of())).isNull();
        assertThat(BillRowBillingValidator.toBillingNotes(null)).isNull();
    }
}
