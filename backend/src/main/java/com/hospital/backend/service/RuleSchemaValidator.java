package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.backend.common.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 后端规则 JSON 校验，镜像前端 validatePricingRules 逻辑，防止 API 绕过。
 */
@Component
public class RuleSchemaValidator {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    public ValidationResult validate(Map<String, Object> rules) {
        if (rules == null || rules.isEmpty()) {
            return ValidationResult.fail(List.of("规则内容不能为空"));
        }
        return validateJsonNode(MAPPER.valueToTree(rules));
    }

    public ValidationResult validateJsonNode(JsonNode rules) {
        List<String> errors = new ArrayList<>();
        if (rules == null || rules.isNull() || !rules.isObject()) {
            return ValidationResult.fail(List.of("规则格式不正确"));
        }

        String version = textOrEmpty(rules, "version");
        if (version.isBlank()) {
            errors.add("版本号不能为空");
        }

        JsonNode ht = rules.path("highTemperature");
        if (ht.isMissingNode() || !ht.isObject()) {
            errors.add("缺少高温规则");
        } else {
            validateHighTemperature(ht, errors);
        }

        JsonNode lt = rules.path("lowTemperature");
        if (lt.isMissingNode() || !lt.isObject()) {
            errors.add("缺少低温规则");
        } else {
            validateLowTemperature(lt, errors);
        }

        JsonNode packaging = rules.path("packaging");
        if (packaging.isMissingNode() || !packaging.isObject()) {
            errors.add("缺少包装收费规则");
        } else {
            validatePackaging(packaging, errors);
        }

        JsonNode needle = rules.path("needle");
        if (needle.isMissingNode() || !needle.isObject()) {
            errors.add("缺少小件识别规则");
        } else {
            if (needle.path("threshold").asInt(-1) < 0) {
                errors.add("小件识别触发件数不能小于 0");
            }
            if (needle.path("foldRatio").asInt(-1) < 0) {
                errors.add("小件折算比例不能小于 0");
            }
            if (!needle.has("keywords") || !needle.path("keywords").isArray()
                    || needle.path("keywords").isEmpty()) {
                errors.add("小件识别关键词不能为空");
            }
        }

        JsonNode cleaning = rules.path("cleaning");
        if (cleaning.isMissingNode() || !cleaning.isObject()) {
            errors.add("缺少清洗规则");
        } else if (!cleaning.path("summaryKeywords").isArray()
                || cleaning.path("summaryKeywords").isEmpty()) {
            errors.add("至少需要一个汇总关键词");
        }

        JsonNode logistics = rules.path("logistics");
        if (logistics.isMissingNode() || !logistics.isObject()) {
            errors.add("缺少物流规则");
        } else {
            int hour = logistics.path("dayBoundaryHour").asInt(-1);
            if (hour < 0 || hour > 23) {
                errors.add("物流跨天时间点必须在 0-23 之间");
            }
            if (logistics.path("feePerTrip").asDouble(-1) < 0) {
                errors.add("物流单次费用不能小于 0");
            }
        }

        JsonNode settlement = rules.path("settlementLetter");
        if (settlement.isMissingNode() || !settlement.isObject()) {
            errors.add("缺少结款函规则");
        } else {
            if (settlement.path("rowHeight").asInt(0) <= 0) {
                errors.add("结款函行高必须大于 0");
            }
            if (!settlement.path("feeItems").isArray() || settlement.path("feeItems").isEmpty()) {
                errors.add("至少需要一个结款函费用项");
            }
            if (!settlement.path("templates").isArray() || settlement.path("templates").isEmpty()) {
                errors.add("至少需要一个结款函模板");
            }
        }

        JsonNode exportOptions = rules.path("exportOptions");
        if (exportOptions.isMissingNode() || !exportOptions.isObject()) {
            errors.add("缺少导出规则");
        } else {
            if (textOrEmpty(exportOptions, "billFilePrefix").isBlank()) {
                errors.add("账单导出文件名前缀不能为空");
            }
            if (textOrEmpty(exportOptions, "settlementFilePrefix").isBlank()) {
                errors.add("结款函导出文件名前缀不能为空");
            }
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }

    private void validateHighTemperature(JsonNode ht, List<String> errors) {
        JsonNode nw = ht.path("nonWoven");
        if (nw.path("minCharge").asDouble(0) <= 0) {
            errors.add("高温无纺布最低收费必须大于 0");
        }
        if (nw.path("flatPerPackagePrice").asDouble(0) <= 0) {
            errors.add("高温无纺布件单价必须大于 0");
        }
        if (nw.path("flatRateThreshold").asDouble(0) <= 0) {
            errors.add("高温无纺布阶梯阈值必须大于 0");
        }

        JsonNode pp = ht.path("paperPlastic");
        if (pp.path("perPackagePrice").asDouble(0) <= 0) {
            errors.add("高温纸塑袋件单价必须大于 0");
        }
        if (pp.path("minCharge").asDouble(0) <= 0) {
            errors.add("高温纸塑袋最低收费必须大于 0");
        }
        validateBagSizes(pp.path("bagSizes"), "高温纸塑袋", errors);
    }

    private void validateLowTemperature(JsonNode lt, List<String> errors) {
        JsonNode nw = lt.path("nonWoven");
        if (nw.path("minSingleCharge").asDouble(0) <= 0) {
            errors.add("低温无纺布单件最低收费必须大于 0");
        }
        if (nw.path("remainderPerPiecePrice").asDouble(0) <= 0) {
            errors.add("低温无纺布阶梯余数单价必须大于 0");
        }
        validateTierPrices(nw.path("tierPrices"), "低温无纺布", errors);

        JsonNode pp = lt.path("paperPlastic");
        validateTierPrices(pp.path("tierPrices"), "低温纸塑袋", errors);
        validateBagSizes(pp.path("bagSizes"), "低温纸塑袋", errors);
    }

    private void validateBagSizes(JsonNode bagSizes, String label, List<String> errors) {
        if (!bagSizes.isArray() || bagSizes.isEmpty()) {
            errors.add("至少需要配置一个" + label + "袋型");
            return;
        }
        for (int i = 0; i < bagSizes.size(); i++) {
            JsonNode bag = bagSizes.get(i);
            if (bag.path("size").asInt(0) <= 0) {
                errors.add(label + "袋型 " + (i + 1) + " 的尺寸必须大于 0");
            }
            if (bag.path("price").asDouble(0) <= 0) {
                errors.add(label + "袋型 " + (i + 1) + " 的袋费必须大于 0");
            }
            if (!bag.path("keywords").isArray() || bag.path("keywords").isEmpty()) {
                errors.add(label + "袋型 " + (i + 1) + " 至少需要一个关键词");
            }
        }
    }

    private void validateTierPrices(JsonNode tiers, String label, List<String> errors) {
        if (!tiers.isArray() || tiers.isEmpty()) {
            errors.add("至少需要配置一个" + label + "阶梯价格");
            return;
        }
        for (int i = 0; i < tiers.size(); i++) {
            JsonNode tier = tiers.get(i);
            if (tier.path("count").asInt(0) <= 0) {
                errors.add(label + "阶梯 " + (i + 1) + " 的件数必须大于 0");
            }
            if (tier.path("price").asDouble(0) <= 0) {
                errors.add(label + "阶梯 " + (i + 1) + " 的价格必须大于 0");
            }
        }
    }

    private void validatePackaging(JsonNode packaging, List<String> errors) {
        JsonNode items = packaging.path("items");
        if (!items.isArray()) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            if (textOrEmpty(item, "name").isBlank()) {
                errors.add("包装收费项目 " + (i + 1) + " 名称不能为空");
            }
            if (!item.path("keywords").isArray() || item.path("keywords").isEmpty()) {
                errors.add("包装收费项目 " + (i + 1) + " 至少需要一个匹配关键词");
            }
            JsonNode options = item.path("options");
            if (options.isArray()) {
                for (int j = 0; j < options.size(); j++) {
                    JsonNode option = options.get(j);
                    if (textOrEmpty(option, "label").isBlank()) {
                        errors.add("包装收费项目 " + (i + 1) + " 的选项 " + (j + 1) + " 名称不能为空");
                    }
                    if (option.path("price").asDouble(-1) < 0) {
                        errors.add("包装收费项目 " + (i + 1) + " 的选项 " + (j + 1) + " 价格不能小于 0");
                    }
                    if (!option.path("keywords").isArray() || option.path("keywords").isEmpty()) {
                        errors.add("包装收费项目 " + (i + 1) + " 的选项 " + (j + 1) + " 至少需要一个关键词");
                    }
                }
            }
        }
    }

    private String textOrEmpty(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult fail(List<String> errors) {
            return new ValidationResult(false, List.copyOf(errors));
        }

        public String message() {
            return String.join("；", errors);
        }
    }
}
