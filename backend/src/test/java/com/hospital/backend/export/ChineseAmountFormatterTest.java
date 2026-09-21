package com.hospital.backend.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChineseAmountFormatterTest {

    @Test
    void formatMatchesFuyiJuneTotal() {
        assertThat(ChineseAmountFormatter.format(164577.7))
                .isEqualTo("壹拾陆万肆仟伍佰柒拾柒元柒角");
    }
}
