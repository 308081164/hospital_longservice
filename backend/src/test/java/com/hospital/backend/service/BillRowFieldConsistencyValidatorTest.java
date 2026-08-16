package com.hospital.backend.service;

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
                        3);

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
                        2);

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
                        3);

        assertThat(violations).isEmpty();
    }

    @Test
    void materialClassMismatchIsReported() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "剪刀-3/z1530",
                        "低温无纺布30*40",
                        3);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code())
                .isEqualTo(BillRowFieldConsistencyValidator.CODE_MATERIAL_CLASS_MISMATCH);
    }

    @Test
    void toBillingNotesBuildsStructuredPayload() {
        List<BillRowFieldConsistencyValidator.Violation> violations =
                BillRowFieldConsistencyValidator.validate(
                        "额外包(纸塑袋)",
                        "支抗钉-3 75*20",
                        "高温纸塑袋75*200",
                        2);

        var billingNotes = BillRowFieldConsistencyValidator.toBillingNotes(violations);
        assertThat(billingNotes).isNotNull();
        assertThat(billingNotes.get("type")).isEqualTo("field_consistency");
        assertThat(billingNotes.get("violations")).isInstanceOf(List.class);
        assertThat((List<?>) billingNotes.get("violations")).hasSize(2);
    }
}
