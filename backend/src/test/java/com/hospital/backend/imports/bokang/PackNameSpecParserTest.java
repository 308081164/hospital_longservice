package com.hospital.backend.imports.bokang;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PackNameSpecParserTest {

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("totalPieceCountCases")
    void extractTotalPieceCountFromPackName(String packName, Integer expected) {
        assertThat(PackNameSpecParser.extractTotalPieceCountFromPackName(packName)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "skip {0}")
    @MethodSource("skipPieceCountCases")
    void shouldSkipPieceCountExtraction(String packName) {
        assertThat(PackNameSpecParser.shouldSkipPieceCountExtraction(packName)).isTrue();
        assertThat(PackNameSpecParser.extractTotalPieceCountFromPackName(packName)).isNull();
    }

    @ParameterizedTest(name = "implicit single {0}")
    @MethodSource("implicitSinglePieceCases")
    void isImplicitSinglePiecePerPack(String packName, boolean expected) {
        assertThat(PackNameSpecParser.isImplicitSinglePiecePerPack(packName)).isEqualTo(expected);
    }

    private static Stream<Arguments> totalPieceCountCases() {
        return Stream.of(
                Arguments.of("粉刺针-3/Z7526", 3),
                Arguments.of("止血钳-2剪-1/Z1530", 3),
                Arguments.of("排针-12/Z7526", 12),
                Arguments.of("排针20/Z1026", 20),
                Arguments.of("止血钳3/Z1530", 3),
                Arguments.of("盆1碗1/W9050", 2),
                Arguments.of("盆1碗2盘2杯1/W9050", 6),
                Arguments.of("镊子1止血钳2/z1526", 3),
                Arguments.of("剪刀1持针器1止血钳1/Z1530", 3),
                Arguments.of("宫腔镜包26件", 26),
                Arguments.of("开腹包-50件", 50),
                Arguments.of("机扩针-6盒1/z1526", 7),
                Arguments.of("种植扳手-8盒1/z2032", 9),
                Arguments.of("宫腔镜-2件盒1/W12050", 2),
                Arguments.of("种植盒-10件 盒1/w6050", 11),
                Arguments.of("种植盒-8件盒1/W6050", 9),
                Arguments.of("电切内窥镜-9件盒1", 9),
                Arguments.of("种植9件盒1/w7050", 10),
                Arguments.of("抛光车针盒6件盒1/Z1026", 7),
                Arguments.of("针盒1针58/z1026", 59),
                Arguments.of("外科器械包-9（筐1）/w7050", 10),
                Arguments.of("ICL器械-8件（盒1）/W6050", 8),
                Arguments.of("旧轧皮机（2号）-2件盒1/W12050", 2),
                Arguments.of("史赛克摆锯骨动力-4（带盒5件）", 5),
                Arguments.of("新3光源-1（带盒两件）/W12050", 2),
                Arguments.of("种植盒-53盒1/W7050", 54),
                Arguments.of("屈光器械盘（钩镊）-17盒1/W6050", 17),
                Arguments.of("分体镜-1盒1/Z2044", 1),
                Arguments.of("盘1碗1杯1/w6050", 3),
                Arguments.of("取冠器-1z7534", 1),
                Arguments.of("钩镊-1z7526", 1),
                Arguments.of("胸外镜头-1（盒1）W9050", 1),
                Arguments.of("胸外镜头30°-1（盒1）W9050", 1),
                Arguments.of("DMD光源2号-1（带盒）", 1),
                Arguments.of("妇科腔镜-17（铁帽3胶帽7）", 17),
                Arguments.of("妇腔镜器械-18（铁帽3胶帽8）", 18),
                Arguments.of("激光镜-4件 盒1", 4),
                Arguments.of("25°镜头1件 盒1/z2060", 1),
                Arguments.of("种植盒-11件 盒1/w6050", 12),
                Arguments.of("显微有钩镊1，无钩镊1/W5050", 2),
                Arguments.of("人流包（22件）", 22),
                Arguments.of("取上环包-21件", 21),
                Arguments.of("器械包-2袋/z1526", 2),
                Arguments.of("镊子包-5件（带框）/w9050", 5),
                Arguments.of("扩棒（3-5.5号）-6 /z1526", 6),
                Arguments.of("窥器1宫颈钳1镊子1/W6050", 3),
                Arguments.of("外科腹腔镜-16件筐1/W12050", 17),
                Arguments.of("电切内窥镜-9件筐1", 10),
                Arguments.of("切开包-13件盒1", 14),
                Arguments.of("阴切包-10件盒1", 11),
                Arguments.of("产包-16件盒1", 17),
                Arguments.of("气腹管-1/Z3040", 1),
                Arguments.of("克氏针-12/Z7530", 12),
                Arguments.of("排针-15/Z7526", 15),
                Arguments.of("套筒-1/Z7520", 1),
                // 括号内连字符：外套无连字符时参与计数（妇幼人口 全冠套装）
                Arguments.of("全冠套装（针-8盒-1）", 9),
                Arguments.of("全冠套装(针-8盒-1)/W9050", 9),
                // 外套有连字符时括号内规格区间仍忽略
                Arguments.of("扩棒（3-5.5号）-6/z1526", 6),
                // 紧凑复合括号内容器不双重计数（针7（盒1）→ 8 而非 9）
                Arguments.of("针7（盒1）", 8),
                Arguments.of("针7盒1", 8),
                Arguments.of("针7盒1/z1026", 8),
                // 生产复核样本：市五院 / 平房区人民
                Arguments.of("开口器4件/Z1526", 4),
                Arguments.of("车针-5件/Z7520", 5),
                Arguments.of("戳卡4转换器1气腹针1/Z1026", 6),
                Arguments.of("腹腔镜下胆囊切除（戳卡4转换器1气腹针1）/Z1026", 6),
                Arguments.of("洗手服/w12050", null),
                Arguments.of("刮宫包", null));
    }

    private static Stream<Arguments> skipPieceCountCases() {
        return Stream.of(
                Arguments.of("车针架1针4/Z1026"),
                Arguments.of("手机721001/z7526"),
                Arguments.of("手机5X1729/z7526"),
                Arguments.of("手机-Z0034/z7526"));
    }

    private static Stream<Arguments> implicitSinglePieceCases() {
        return Stream.of(
                Arguments.of("持针器/z1029", true),
                Arguments.of("拔牙挺（套保护套）/z7526", false),
                Arguments.of("宫腔镜12度/z2060", false),
                Arguments.of("手机721001/z7526", false));
    }
}
