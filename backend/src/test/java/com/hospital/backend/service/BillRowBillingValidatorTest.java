package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BillRowBillingValidatorTest {

    @Test
    void bareDressingPackAllowsBlankMaterial() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("敷料包", "", 0);

        assertThat(violations).isEmpty();
    }

    @Test
    void dressingPackWithPaperPlasticTypeRequiresPaperMaterial() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("敷料包(纸塑袋)", "", 0);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_PACK_TYPE_MATERIAL_MISMATCH);
    }

    @Test
    void dressingPackWithNonWovenMaterialIsValid() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("敷料包(无纺布)", "无纺布-90×90-50g", 0);

        assertThat(violations).isEmpty();
    }

    @Test
    void nonDressingPackWithBlankPackagingReportsMismatch() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(纸塑袋)", "", 3);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_PACK_TYPE_MATERIAL_MISMATCH);
        assertThat(violations.get(0).message()).contains("额外包（纸塑袋）");
    }

    @Test
    void nonDressingPackWithWrongMaterialFamilyReportsMismatch() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(纸塑袋)", "无纺布-90×90", 3);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_PACK_TYPE_MATERIAL_MISMATCH);
    }

    @Test
    void nonDressingPackWithZeroInstrumentCountReportsViolation() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(纸塑袋)", "高温纸塑袋75*200", 0);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_ZERO_INSTRUMENT_COUNT);
    }

    @Test
    void zsdWithBlankMaterialReportsMismatchAndZeroCount() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("器械包(ZSD)", "", 0);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(
                        BillRowBillingValidator.CODE_PACK_TYPE_MATERIAL_MISMATCH,
                        BillRowBillingValidator.CODE_ZERO_INSTRUMENT_COUNT);
    }

    @Test
    void nonDressingPackWithBothFilledIsClean() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(纸塑袋)", "高温纸塑袋75*200", 3);

        assertThat(violations).isEmpty();
    }

    @Test
    void blankTypeReportsUnknownPackType() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate(null, "", 0);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_UNKNOWN_PACK_TYPE);
    }

    @Test
    void unknownTypeReportsUnknownPackType() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("高温灭菌", "高温纸塑袋75*200", 3);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_UNKNOWN_PACK_TYPE);
        assertThat(violations.get(0).message())
                .contains("对照表 " + PackTypeRegistry.PACK_TYPE_COUNT + " 种");
    }

    @Test
    void cottonExtraPackWithNonWovenIsClean() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(棉布包装)", "无纺布-60×60-50g", 6, 9.9);

        assertThat(violations).isEmpty();
    }

    @Test
    void cottonExtraPackWithBareClothIsMaterialMismatchNotUnknownType() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包（棉布包装）", "布-120×120-50g", 3, 9.9);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_PACK_TYPE_MATERIAL_MISMATCH);
    }

    @Test
    void disinfectOnlyExtraPackWithBlankMaterialIsCleanWhenPricePresent() {
        assertThat(BillRowBillingValidator.validate("额外包(仅消毒)", "", 333, 9.9)).isEmpty();
        assertThat(BillRowBillingValidator.validate("额外包（仅消毒）", null, 2, 9.9)).isEmpty();
    }

    @Test
    void disinfectOnlyExtraPackWithHighTempPaperIsCleanWhenPricePresent() {
        List<BillRowBillingValidator.Violation> violations = BillRowBillingValidator.validate(
                "额外包(仅消毒)", "高温纸塑袋150*260", 12, 8.0);

        assertThat(violations).isEmpty();
    }

    @Test
    void disinfectOnlyExtraPackWithNonWovenIsMaterialMismatchNotUnknownType() {
        List<BillRowBillingValidator.Violation> violations = BillRowBillingValidator.validate(
                "额外包（仅消毒）", "无纺布-60×60-50g", 2, 8.0);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_PACK_TYPE_MATERIAL_MISMATCH);
    }

    @Test
    void disinfectOnlyExtraPackStillFlagsZeroUnitPriceBeforeRuleMatch() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(仅消毒)", "", 333, 0.0);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_ZERO_UNIT_PRICE);
    }

    @Test
    void zeroUnitPriceOnNonDressingPackReportsViolation() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(纸塑袋)", "高温纸塑袋75*200", 3, 0.0);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_ZERO_UNIT_PRICE);
    }

    @Test
    void zeroUnitPriceOnDressingPackIsNotExempt() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("敷料包(纸塑袋)", "高温纸塑袋75*200", 0, 0.0);

        assertThat(violations).extracting(BillRowBillingValidator.Violation::code)
                .containsExactly(BillRowBillingValidator.CODE_ZERO_UNIT_PRICE);
    }

    @Test
    void toBillingNotesBuildsBillingValidationPayload() {
        List<BillRowBillingValidator.Violation> violations =
                BillRowBillingValidator.validate("额外包(纸塑袋)", "", 0);

        Map<String, Object> billingNotes = BillRowBillingValidator.toBillingNotes(violations);

        assertThat(billingNotes).isNotNull();
        assertThat(billingNotes.get("type")).isEqualTo("billing_validation");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) billingNotes.get("violations");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("code"))
                .isEqualTo(BillRowBillingValidator.CODE_PACK_TYPE_MATERIAL_MISMATCH);
    }
}
