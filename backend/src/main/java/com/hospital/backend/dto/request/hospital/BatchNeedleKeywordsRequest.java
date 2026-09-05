package com.hospital.backend.dto.request.hospital;

import lombok.Data;

import java.util.List;

@Data
public class BatchNeedleKeywordsRequest {

    /** 普通识别关键词（沿用全局默认触发件数/折算比例/匹配模式） */
    private List<String> keywords;

    /** 关键词独立配置：每个关键词可单独覆盖匹配模式、触发件数、折算比例，多条共存 */
    private List<NeedleKeywordConfigItem> keywordConfigs;

    /** 全局默认触发件数（可选，提供时一并更新） */
    private Integer threshold;

    /** 全局默认折算比例（可选，提供时一并更新） */
    private Double foldRatio;

    /** 全局默认匹配模式（可选，exact_token / contains） */
    private String keywordMatchMode;

    private String operator;

    @Data
    public static class NeedleKeywordConfigItem {

        /** 独立配置关键词（必填） */
        private String keyword;

        /** 词级匹配模式：contains / exact / exact_token；缺省沿用全局默认 */
        private String matchMode;

        /** 词级触发件数；缺省沿用全局 threshold */
        private Integer threshold;

        /** 词级折算比例；缺省沿用全局 foldRatio */
        private Double foldRatio;
    }
}
