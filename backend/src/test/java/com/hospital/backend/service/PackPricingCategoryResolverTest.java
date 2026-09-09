package com.hospital.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackPricingCategoryResolverTest {

    @Test
    void paperPlasticTourniquetIsInstrumentPaper() {
        var resolution = PackPricingCategoryResolver.resolve(
                "额外包(纸塑袋)",
                "驱血带(高温)/Z2032",
                "高温纸塑袋200*320",
                1,
                1);
        assertThat(resolution.category()).isEqualTo(PackPricingCategory.INSTRUMENT_PAPER);
        assertThat(resolution.note()).contains("纸塑");
    }

    @Test
    void nonWovenTourniquetIsDressingNonWoven() {
        var resolution = PackPricingCategoryResolver.resolve(
                "敷料包(无纺布包)",
                "驱血带(高温)/W90",
                "无纺布-90×90-50g",
                1,
                1);
        assertThat(resolution.category()).isEqualTo(PackPricingCategory.DRESSING_NONWOVEN);
    }

    @Test
    void dressingPaperPlasticTypeIsDressingPaper() {
        var resolution = PackPricingCategoryResolver.resolve(
                "敷料包(纸塑袋)",
                "孔巾/Z2032",
                "高温纸塑袋200*320",
                0,
                1);
        assertThat(resolution.category()).isEqualTo(PackPricingCategory.DRESSING_PAPER);
    }

    @Test
    void cottonBallPaperPlasticIsDressingPaper() {
        var resolution = PackPricingCategoryResolver.resolve(
                "敷料包(纸塑袋)",
                "棉球/Z2032",
                "高温纸塑袋200*320",
                0,
                119);
        assertThat(resolution.category()).isEqualTo(PackPricingCategory.DRESSING_PAPER);
    }

    @Test
    void extraPackPaperPlasticIsInstrumentPaper() {
        var resolution = PackPricingCategoryResolver.resolve(
                "额外包(纸塑袋)",
                "咬针器-1/W6050",
                "高温纸塑袋75*200",
                1,
                1);
        assertThat(resolution.category()).isEqualTo(PackPricingCategory.INSTRUMENT_PAPER);
    }

    @Test
    void extraPackNonWovenIsInstrumentNonWoven() {
        var resolution = PackPricingCategoryResolver.resolve(
                "额外包(无纺布包)",
                "钢丝钳-1/W505060",
                "无纺布-50×50-60g",
                1,
                1);
        assertThat(resolution.category()).isEqualTo(PackPricingCategory.INSTRUMENT_NONWOVEN);
    }

    @Test
    void unknownMaterialAndTypeIsUnknown() {
        var resolution = PackPricingCategoryResolver.resolve(
                "高温灭菌",
                "未知包",
                "",
                1,
                1);
        assertThat(resolution.category()).isEqualTo(PackPricingCategory.UNKNOWN);
    }

    @Test
    void extraPaperPlasticWithBlankMaterialIsUnknown() {
        var resolution = PackPricingCategoryResolver.resolve(
                "额外包(纸塑袋)",
                "驱血带(高温)/Z2032",
                "",
                1,
                1);
        assertThat(resolution.category()).isEqualTo(PackPricingCategory.UNKNOWN);
        assertThat(resolution.note()).contains("包材");
    }

    @Test
    void extraNonWovenTypeWithPaperMaterialUsesPaperPlasticPricing() {
        var resolution = PackPricingCategoryResolver.resolve(
                "额外包(无纺布)",
                "碗1盘1/Z2535",
                "高温纸塑袋250*350",
                2,
                1);
        assertThat(resolution.category()).isEqualTo(PackPricingCategory.INSTRUMENT_PAPER);
        assertThat(resolution.note()).contains("按包装材料列").contains("高温纸塑袋");
    }

    @Test
    void extraPaperPlasticTypeWithNonWovenMaterialUsesNonWovenPricing() {
        var resolution = PackPricingCategoryResolver.resolve(
                "额外包(纸塑袋)",
                "剪刀-3/z1530",
                "无纺布-90×90-50g",
                3,
                1);
        assertThat(resolution.category()).isEqualTo(PackPricingCategory.INSTRUMENT_NONWOVEN);
        assertThat(resolution.note()).contains("按包装材料列").contains("无纺布");
    }

    @Test
    void dressingPaperTypeWithNonWovenMaterialUsesDressingNonWovenPricing() {
        var resolution = PackPricingCategoryResolver.resolve(
                "敷料包(纸塑袋)",
                "孔巾/Z2032",
                "无纺布-90×90-50g",
                0,
                1);
        assertThat(resolution.category()).isEqualTo(PackPricingCategory.DRESSING_NONWOVEN);
        assertThat(resolution.note()).contains("按包装材料列");
    }
}
