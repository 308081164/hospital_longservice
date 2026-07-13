package com.hospital.backend.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * JSON 工具类
 *
 * 基于 Jackson 的 JSON 序列化与反序列化工具，提供便捷的静态方法。
 * 项目中大量使用 JSON 字符串字段（如 HospitalPricingRule.rulesJson、
 * HospitalReconciliationJob.rowsJson、HospitalReconciliationRow.notesJson 等）
 * 来存储复杂结构数据，本工具类负责这些字段的 String ↔ Map/List 转换。
 *
 * ── 配置说明 ──
 * 内部维护一个全局的 ObjectMapper 单例（线程安全），配置了：
 * - JavaTimeModule：支持 LocalDateTime、LocalDate 等 Java 8 时间类型
 * - FAIL_ON_UNKNOWN_PROPERTIES = false：反序列化时忽略未知属性
 * - WRITE_DATES_AS_TIMESTAMPS = false：时间类型序列化为字符串（如 "2024-01-15T10:30:00"）
 *
 * ── 主要用途 ──
 * 1. 序列化：将定价规则 Map 转 JSON 字符串存入 rulesJson 字段
 * 2. 反序列化：将 rulesJson 字符串转 Map 返回前端
 * 3. 行数据转换：rowsJson 与 List&lt;Map&gt; 的互转
 * 4. 备注处理：notesJson 的解析
 *
 * @see com.fasterxml.jackson.databind.ObjectMapper
 * @see com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
 */
@Slf4j
public class JsonUtils {

    /**
     * 全局 ObjectMapper 单例实例
     *
     * Jackson 的 ObjectMapper 是线程安全的，可以在多个线程间共享。
     * 此处使用 private static final 创建并缓存一个全局实例，
     * 避免每次调用时都创建新的 ObjectMapper 对象，提高性能。
     *
     * 配置详解：
     * - JavaTimeModule：Jackson 官方提供的 Java 8 时间类型支持模块，
     *   使 LocalDateTime、LocalDate、LocalTime 等类型可以正确序列化/反序列化。
     * - FAIL_ON_UNKNOWN_PROPERTIES = false：当 JSON 中包含 Java 对象
     *   中不存在的字段时，不会抛出异常，而是静���忽略。此配置增强了
     *   前后端接口的兼容性。
     * - WRITE_DATES_AS_TIMESTAMPS = false：将日期时间序列化为可读的
     *   字符串格式（如 "2024-01-15T10:30:00"）而非时间戳数组。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    /**
     * 将任意对象序列化为 JSON 字符串
     *
     * 核心序列化方法，将 Java 对象（Map、List、实体等）转换为 JSON 格式的字符串。
     *
     * ── 使用场景 ──
     * 在 Service 层保存计费规则时调用：
     *   entity.setRulesJson(JsonUtils.toJson(request.getRules()));
     * 将前端提交的 Map 序列化为字符串存入数据库。
     *
     * ── 异常处理 ──
     * 序列化失败时记录错误日志，返回字符串 "null"，
     * 这样存入数据库的字段值也是一个合法的 JSON 值。
     *
     * @param obj 待序列化的 Java 对象（可为 null）
     * @return JSON 格式字符串；失败时返回 "null"
     */
    public static String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败: {}", e.getMessage(), e);
            return "null";
        }
    }

    /**
     * 将 JSON 字符串解析为指定类型的 Java 对象
     *
     * 通用反序列化方法，适用于将 JSON 字符串解析为具体的 Java Bean（POJO）类型。
     *
     * ── 使用场景 ──
     * 当需要将 JSON 字符串直接映射到某个已知类型的对象时使用。
     * 但在本项目中的 JSON 字段（rulesJson、rowsJson 等）通常结构不固定，
     * 更多使用 parseToMap() 和 parseToList() 方法。
     *
     * ── 空值处理 ──
     * 如果传入的 json 为 null 或空字符串，直接返回 null，避免不必要的解析。
     *
     * @param json  JSON 格式字符串，可为 null 或空字符串
     * @param clazz 目标 Java 类型的 Class 对象
     * @param <T>   目标类型的泛型参数
     * @return 解析后的 Java 对象；解析失败或输入为空时返回 null
     */
    public static <T> T parse(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串解析为 Map&lt;String, Object&gt;
     *
     * 最常用的反序列化方法。将结构不确定的 JSON 字符串解析为通用的 Map 结构，
     * 前端可以直接遍历和使用 Map 中的嵌套数据。
     *
     * ── 使用场景 ──
     * 查询计费规则时调用：
     *   Map&lt;String, Object&gt; rules = JsonUtils.parseToMap(entity.getRulesJson());
     *   然后封装到 PricingRuleResponse 中返回前端。
     *
     * ── 技术说明 ──
     * 使用 TypeReference 保留泛型类型信息，确保可以正确解析为
     * Map&lt;String, Object&gt; 而非 Map&lt;String, Map&gt; 等。
     *
     * ── 空值处理 ──
     * 如果传入的 json 为 null 或空字符串，直接返回 null。
     *
     * @param json JSON 格式字符串，可为 null 或空字符串
     * @return 解析后的 Map 对象；解析失败或输入为空时返回 null
     */
    public static Map<String, Object> parseToMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("JSON 转 Map 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串解析为 List&lt;Map&gt;
     *
     * 将 JSON 数组格式的字符串解析为 List&lt;Map&lt;String, Object&gt;&gt;。
     *
     * ── 使用场景 ──
     * 查询核对任务的 rowsJson 时调用：
     *   List&lt;Map&lt;String, Object&gt;&gt; rows = JsonUtils.parseToList(job.getRowsJson(), Map.class);
     *   然后封装到 ReconciliationJobResponse 中返回前端。
     *
     * ── 类型说明 ──
     * 参数 clazz 固定传入 Map.class，因为 JSON 数组的元素是动态结构，
     * 不适合映射为具体的 Java Bean。方法内部使用 Jackson 的 TypeFactory
     * 构建 CollectionType 来保证泛型安全。
     *
     * ── 空值处理 ──
     * 如果传入的 json 为 null 或空字符串，直接返回 null。
     *
     * @param json  JSON 数组格式字符串，可为 null 或空字符串
     * @param clazz List 中元素的 Class 类型（通常传入 Map.class）
     * @param <T>   元素类型的泛型参数
     * @return 解析后的 List 对象；解析失败或输入为空时返回 null
     */
    public static <T> List<T> parseToList(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            log.error("JSON 转 List 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将任意 Java 对象转换为 Map&lt;String, Object&gt;
     *
     * 使用 Jackson 的 convertValue 方法将 Java Bean 转换为 Map，
     * 仅转换对象的第一层字段（浅转换），嵌套对象仍然保持其原始类型。
     *
     * ── 与 parseToMap 的区别 ──
     * parseToMap：从 JSON 字符串解析为 Map
     * toMap：从 Java 对象转换为 Map（无需经过 JSON 字符串中转）
     *
     * ── 使用场景 ──
     * 当需要将某个 Java 对象以 Map 形式传递或展示时使用。
     *
     * @param obj 待转换的 Java 对象
     * @return 转换后的 Map 对象；转换失败时返回 null
     */
    public static Map<String, Object> toMap(Object obj) {
        try {
            return OBJECT_MAPPER.convertValue(obj, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("对象转 Map 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取全局的 ObjectMapper 实例
     *
     * 当默认的序列化/反序列化配置无法满足需求时，
     * 可以直接获取此实例进行定制化的 Jackson 操作。
     * 注意：对该实例的配置修改会影响所有使用此工具类的地方。
     *
     * @return 全局 ObjectMapper 实例
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}
