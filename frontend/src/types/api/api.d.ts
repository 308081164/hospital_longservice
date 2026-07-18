/**
 * API 接口类型定义模块
 *
 * 提供所有后端接口的类型定义
 *
 * ## 主要功能
 *
 * - 通用类型（分页参数、响应结构等）
 * - 认证类型（登录、用户信息等）
 * - 系统管理类型（用户、角色等）
 * - 全局命名空间声明
 *
 * ## 使用场景
 *
 * - API 请求参数类型约束
 * - API 响应数据类型定义
 * - 接口文档类型同步
 *
 * ## 注意事项
 *
 * - 在 .vue 文件使用需要在 eslint.config.mjs 中配置 globals: { Api: 'readonly' }
 * - 使用全局命名空间，无需导入即可使用
 *
 * ## 使用方式
 *
 * ```typescript
 * const params: Api.Auth.LoginParams = { userName: 'admin', password: '123456' }
 * const response: Api.Auth.UserInfo = await fetchUserInfo()
 * ```
 *
 * @module types/api/api
 * @author Art Design Pro Team
 */

declare namespace Api {
  /** 通用类型 */
  namespace Common {
    /** 分页参数 */
    interface PaginationParams {
      /** 当前页码 */
      current: number
      /** 每页条数 */
      size: number
      /** 总条数 */
      total: number
    }

    /** 通用搜索参数 */
    type CommonSearchParams = Pick<PaginationParams, 'current' | 'size'>

    /** 分页响应基础结构 */
    interface PaginatedResponse<T = any> {
      records: T[]
      current: number
      size: number
      total: number
    }

    /** 启用状态 */
    type EnableStatus = '1' | '2'
  }

  /** 认证类型 */
  namespace Auth {
    /** 登录参数 */
    interface LoginParams {
      userName: string
      password: string
    }

    /** 登录响应 */
    interface LoginResponse {
      access_token: string
      refresh_token: string
      token?: string
      refreshToken?: string
      username: string
      token_type: string
      expires_in: number
    }

    /** 用户信息 */
    interface UserInfo {
      id: number
      userId?: number
      username: string
      userName?: string
      email: string
      is_active: boolean
      is_superuser: boolean
      avatar?: string | null
      roles: string[]
      buttons?: string[]
      createdAt: string
      updatedAt: string
      last_login?: string | null
    }
  }

  /** 医院计费规则类型 */
  namespace Hospital {
    /** 袋型配置 */
    interface BagSizeConfig {
      size: number
      price: number
      keywords: string[]
      label?: string
    }

    /** 高温无纺布配置 */
    interface HighTempNonWovenConfig {
      minCharge: number
      flatPerPackagePrice: number
      flatRateThreshold: number
    }

    /** 高温纸塑袋配置 */
    interface HighTempPaperPlasticConfig {
      bagSizes: BagSizeConfig[]
      perPackagePrice: number
      minCharge: number
    }

    /** 低温阶梯价格 */
    interface TierPriceConfig {
      count: number
      price: number
    }

    /** 低温无纺布配置 */
    interface LowTempNonWovenConfig {
      tierPrices: TierPriceConfig[]
      remainderPerPiecePrice?: number
      minSingleCharge: number
    }

    /** 低温纸塑袋配置 */
    interface LowTempPaperPlasticConfig {
      bagSizes: BagSizeConfig[]
      tierPrices: TierPriceConfig[]
    }

    /** 特殊固定单价规则 */
    interface SpecialFixedPriceRule {
      name: string
      hospitals?: string[]
      keywords: string[]
      price: number
      pricePerInstrument?: boolean
      skipPackaging?: boolean
      bagSizeEquals?: number
      minBagSizeInclusive?: number
      maxBagSizeInclusive?: number
      maxBagSizeExclusive?: number
      minInstrumentCount?: number
      maxInstrumentCount?: number
    }

    /** 特殊折算规则 */
    interface SpecialFoldRule {
      name: string
      hospitals?: string[]
      keywords: string[]
      threshold: number
      foldRatio: number
      bagSizeEquals?: number
      minBagSizeInclusive?: number
      maxBagSizeInclusive?: number
      maxBagSizeExclusive?: number
    }

    /** 特殊加收规则 */
    interface SpecialExtraFeeRule {
      name: string
      hospitals?: string[]
      keywords: string[]
      fee: number
      bagSizeEquals?: number
      minBagSizeInclusive?: number
      maxBagSizeInclusive?: number
      maxBagSizeExclusive?: number
      minInstrumentCount?: number
      maxInstrumentCount?: number
    }

    /** 特殊计费规则 */
    interface SpecialRulesConfig {
      fixedPrices: SpecialFixedPriceRule[]
      foldRules: SpecialFoldRule[]
      extraFees: SpecialExtraFeeRule[]
    }

    /** 包装收费选项 */
    interface PackagingOptionConfig {
      label: string
      price: number
      keywords: string[]
    }

    /** 包装收费项目 */
    interface PackagingChargeItemConfig {
      name: string
      keywords: string[]
      chargePerPack: boolean
      options: PackagingOptionConfig[]
    }

    /** 包装收费规则 */
    interface PackagingRulesConfig {
      enabled: boolean
      selfPackedKeywords: string[]
      items: PackagingChargeItemConfig[]
    }

    /** 高温灭菌配置 */
    interface HighTemperatureConfig {
      nonWoven: HighTempNonWovenConfig
      paperPlastic: HighTempPaperPlasticConfig
    }

    /** 低温灭菌配置 */
    interface LowTemperatureConfig {
      nonWoven: LowTempNonWovenConfig
      paperPlastic: LowTempPaperPlasticConfig
    }

    /** 小件识别配置 */
    interface NeedleConfig {
      threshold: number
      foldRatio: number
      keywords: string[]
    }

    /** 清洗规则配置 */
    interface CleaningRulesConfig {
      removeFirstRow: boolean
      dropSummaryRows: boolean
      summaryKeywords: string[]
      trimPackagingMaterial: boolean
      clearInstrumentColumnFormatting: boolean
      recomputeTotalsWhenPriceChanges: boolean
    }

    /** 物流规则配置 */
    interface LogisticsRulesConfig {
      enabled: boolean
      feePerTrip: number
      defaultLogisticsFee: number
      dayBoundaryHour: number
      mergeAdjacentDays: boolean
      mergeWindowDays: number
    }

    /** 结款函费用项 */
    interface SettlementLetterFeeItem {
      key: string
      label: string
      remark: string
      enabled: boolean
      sortOrder: number
    }

    /** 结款函配置 */
    interface SettlementLetterTemplate {
      id: string
      name: string
      hospitalName: string
      templateSheetName: string
      titleText: string
      matchKeywords: string[]
      templateRef?: string
      htmlTemplate?: string
    }

    /** 结款函配置 */
    interface SettlementLetterConfig {
      companyName: string
      rowHeight: number
      dateRangeTextTemplate: string
      uppercaseTotalLabel: string
      feeItems: SettlementLetterFeeItem[]
      templates: SettlementLetterTemplate[]
      defaultTemplateId?: string
    }

    /** 导出选项配置 */
    interface ExportOptionsConfig {
      billFilePrefix: string
      warningFilePrefix: string
      settlementFilePrefix: string
      includeWarningSheet: boolean
      defaultPageMargin: string
    }

    /** 定价规则完整配置 */
    interface PricingRules {
      version: string
      updatedAt?: string
      highTemperature: HighTemperatureConfig
      lowTemperature: LowTemperatureConfig
      packaging: PackagingRulesConfig
      needle: NeedleConfig
      cleaning: CleaningRulesConfig
      logistics: LogisticsRulesConfig
      settlementLetter: SettlementLetterConfig
      exportOptions: ExportOptionsConfig
      specialRules?: SpecialRulesConfig
      customCategoryRules?: Record<string, Record<string, number | string | boolean>>
    }

    /** 规则记录 */
    interface PricingRuleRecord {
      id: number
      name: string
      version: string
      description?: string
      isActive: boolean
      hospitalName?: string
      rules: PricingRules
      createdAt: string
      updatedAt: string
    }

    /** 保存规则负载 */
    interface SavePricingRulePayload {
      name: string
      version: string
      description?: string
      isActive?: boolean
      hospitalName?: string
      rules: PricingRules
    }

    /** 校对行数据 */
    interface ReconciliationRowPayload {
      sheetName: string
      rowNumber: number
      deliveryDate: string
      orderNo: string
      type: string
      categoryNo: string
      packName: string
      packageMaterial: string
      packCount: number
      instrumentCount: number
      unitPrice: number | null
      totalPrice: number | null
      expectedUnitPrice: number | null
      correctedTotalPrice: number | null
      difference: number | null
      status: string
      pricingRule: string
      notes: string[]
    }

    /** 校对任务摘要 */
    interface ReconciliationJobSummary {
      total: number
      corrected: number
      unchanged: number
      warning: number
      skipped: number
      totalDifference: number
      originalTotalPrice: number
      correctedTotalPrice: number
    }

    /** 结算费用行 */
    interface SettlementFeeRow {
      indexLabel: string
      itemLabel: string
      amount: number
      remark: string
    }

    /** 导出日志 */
    interface ReconciliationExportLog {
      id: number
      exportType: string
      fileName?: string
      filePath?: string
      operatorName: string
      createdAt: string
    }

    /** 校对任务 */
    interface ReconciliationJob {
      id: number
      hospitalName: string
      sourceFileName: string
      sourceFilePath: string
      sourceFileSize?: number
      ruleId?: number
      ruleName?: string
      ruleVersion?: string
      versionNo: number
      totalRows: number
      correctedRows: number
      unchangedRows: number
      warningRows: number
      skippedRows: number
      totalDifference: number
      originalTotalPrice?: number
      correctedTotalPrice?: number
      reviewStatus: string
      reviewComment?: string
      operatorName: string
      reviewerName?: string
      sourceDateRange?: string
      createdAt: string
      updatedAt: string
      exports: ReconciliationExportLog[]
      rows?: ReconciliationRowPayload[]
      sheetNames?: string[]
      sheetRowCounts?: Record<string, number>
      sheetWarningCounts?: Record<string, number>
      /** 物流次数（唯一发货单号数量），导入时由后端统计 */
      logisticsTripCount?: number
      /** 物流费总额（次数 × 单价） */
      logisticsFee?: number
      /** 物流费明细（趟次、单价、总额、来源） */
      logisticsBreakdown?: {
        tripCount?: number
        feePerTrip?: number
        total?: number
        feeSource?: 'customer' | 'global'
        policyId?: number
      }
      /** 月度结算调整额（低消/封顶） */
      settlementAdjustment?: number
      /** 月度结算明细 */
      monthlyBreakdown?: {
        rawSterilizeTotal?: number
        adjustedTotal?: number
        adjustment?: number
        minCharge?: number
        maxCap?: number
        policyId?: number
        policyName?: string
      }
      /** 加急费明细 */
      urgentBreakdown?: {
        urgentBaseTotal?: number
        urgentRowCount?: number
        nominalSurcharge?: number
        adjustedSurcharge?: number
        urgentTripCount?: number
        nominalUrgentLogisticsTotal?: number
        adjustedUrgentLogisticsTotal?: number
        policyName?: string
      }
      /** 设备抵扣明细 */
      deductionBreakdown?: {
        monthlyAmount?: number
        deductionAmount?: number
        policyName?: string
      }
    }
  }

  /** 主数据 / 产品管理 */
  namespace MasterData {
    interface ProductCategoryRecord {
      id: number
      code: string
      name: string
      parent_id?: number | null
      pricing_path: string
      sort_order?: number
      is_active?: boolean
      product_count?: number
      child_count?: number
      created_at?: string
      updated_at?: string
    }

    interface SaveProductCategoryPayload {
      code: string
      name: string
      parentId?: number | null
      pricingPath: string
      sortOrder?: number
      isActive?: boolean
    }

    type MatchType = 'EXACT_NAME' | 'CONTAINS' | 'REGEX' | 'COMPOSITE'
    type MatchOperator = 'EQ' | 'NE' | 'CONTAINS' | 'NOT_CONTAINS' | 'REGEX' | 'GT' | 'GTE' | 'LT' | 'LTE'
    type MatchField = 'pack_name' | 'type' | 'package_material' | 'category_no' | 'instrument_count'

    interface MatchCondition {
      field: MatchField | string
      operator: MatchOperator | string
      value?: string
    }

    interface MatchRule {
      id?: number
      matchType: MatchType | string
      targetField?: MatchField | string
      patternValue?: string
      matchFields?: string[]
      conditions?: MatchCondition[]
      priority?: number
      isActive?: boolean
    }

    interface ProductAlias {
      id?: number
      alias: string
      matchType?: 'EXACT' | 'CONTAINS' | string
      priority?: number
      isActive?: boolean
    }

    interface ProductRecord {
      id: number
      category_id: number
      category_code?: string
      category_name?: string
      sku_code?: string
      name: string
      pricing_mode?: string
      pricing_path?: string
      public_price?: number | null
      original_price?: number | null
      priority?: number
      is_active?: boolean
      match_rules?: MatchRule[]
      aliases?: ProductAlias[]
      created_at?: string
      updated_at?: string
    }

    interface SaveProductPayload {
      categoryId: number
      skuCode?: string
      name: string
      pricingMode?: string
      publicPrice?: number | null
      originalPrice?: number | null
      priority?: number
      isActive?: boolean
      matchRules?: MatchRule[]
      aliases?: ProductAlias[]
    }

    interface MatchPreviewPayload {
      type?: string
      packName?: string
      packageMaterial?: string
      categoryNo?: string
      instrumentCount?: number
    }

    interface MatchPreviewResult {
      matched: boolean
      product_id?: number
      product_name?: string
      category_id?: number
      category_code?: string
      category_name?: string
      pricing_path?: string
      pricing_mode?: string
      matched_rule_id?: number
      matched_alias?: string
      variant_id?: number
      variant_display_name?: string
      spec_fingerprint?: string
      variant_public_price?: number
      source?: string
    }

    interface ProductVariantRecord {
      id: number
      sku_code?: string
      spec_fingerprint?: string
      pack_name?: string
      type?: string
      package_material?: string
      display_name?: string
      public_price?: number
      occurrence_count?: number
    }

    interface CustomerAlias {
      id?: number
      alias: string
      matchType?: 'exact' | 'contains' | string
      source?: 'engine' | 'bokang_job' | 'manual' | string
      priority?: number
      isActive?: boolean
    }

    interface CustomerDiscount {
      id?: number
      name?: string
      discountRate?: number
      temperature?: 'HT' | 'LT' | 'ANY'
      applyStage?: string
      apply_stage?: string
      applyStages?: string[]
      apply_stages?: string[]
      skipWhenFixedPrice?: boolean
      priority?: number
      isActive?: boolean
      effectiveFrom?: string
      effectiveTo?: string
    }

    interface CustomerProductRule {
      id?: number
      ruleType?: string
      rule_type?: string
      matchMode?: 'first' | 'any_price'
      match_mode?: 'first' | 'any_price'
      name?: string
      priority?: number
      productId?: number
      product_id?: number
      product_name?: string
      productName?: string
      keywords?: string[]
      excludeKeywords?: string[]
      exclude_keywords?: string[]
      materials?: string[]
      temperature?: 'HT' | 'LT' | 'ANY' | ''
      bagSizeEquals?: number
      bag_size_equals?: number
      maxBagSizeExclusive?: number
      max_bag_size_exclusive?: number
      minInstrumentCount?: number
      min_instrument_count?: number
      maxInstrumentCount?: number
      max_instrument_count?: number
      price?: number
      fixed_price?: number
      acceptedPrices?: number[]
      accepted_prices?: number[]
      multiplier?: number
      fee?: number
      threshold?: number
      foldRatio?: number
      fold_ratio?: number
      skipPackaging?: boolean
      skip_packaging?: boolean
      skipDiscount?: boolean
      skip_discount?: boolean
      isActive?: boolean
      is_active?: boolean
    }

    interface CustomerProductRuleRecord {
      id: number
      customer_id?: number
      rule_type: string
      match_mode?: 'first' | 'any_price'
      name?: string
      priority?: number
      product_id: number
      product_name?: string
      keywords?: string[]
      exclude_keywords?: string[]
      materials?: string[]
      temperature?: 'HT' | 'LT' | 'ANY'
      bag_size_equals?: number
      max_bag_size_exclusive?: number
      min_instrument_count?: number
      max_instrument_count?: number
      price?: number
      fixed_price?: number
      accepted_prices?: number[]
      multiplier?: number
      fee?: number
      threshold?: number
      fold_ratio?: number
      skip_packaging?: boolean
      skip_discount?: boolean
      is_active?: boolean
    }

    interface SaveCustomerProductRulePayload {
      productId?: number
      ruleType: 'FIXED_PRICE' | 'PRICE_PER_INSTRUMENT' | 'MULTIPLIER' | 'FOLD' | 'EXTRA_FEE' | 'ADD_FEE'
      matchMode?: 'first' | 'any_price'
      name?: string
      priority?: number
      price?: number
      acceptedPrices?: number[]
      multiplier?: number
      fee?: number
      threshold?: number
      foldRatio?: number
      keywords?: string[]
      excludeKeywords?: string[]
      materials?: string[]
      temperature?: 'HT' | 'LT' | 'ANY' | ''
      bagSizeEquals?: number
      maxBagSizeExclusive?: number
      minInstrumentCount?: number
      maxInstrumentCount?: number
      skipPackaging?: boolean
      skipDiscount?: boolean
      isActive?: boolean
    }

    interface CustomerBillingPolicyRecord {
      id: number
      customer_id?: number
      customerId?: number
      policy_type?: 'DISCOUNT' | 'LOGISTICS' | 'MONTHLY_SETTLEMENT' | 'URGENT' | 'DEDUCTION'
      policyType?: 'DISCOUNT' | 'LOGISTICS' | 'MONTHLY_SETTLEMENT' | 'URGENT' | 'DEDUCTION'
      name?: string
      temperature?: 'HT' | 'LT' | 'ANY'
      rate?: number
      skip_when_fixed_price?: boolean
      skipWhenFixedPrice?: boolean
      fee_per_trip?: number
      feePerTrip?: number
      trip_source?: 'delivery_date' | 'import'
      tripSource?: 'delivery_date' | 'import'
      allocation_mode?: 'none' | 'dept_ratio' | 'equal' | 'proportional' | 'single_owner' | 'cross_hospital_merge'
      allocationMode?: 'none' | 'dept_ratio' | 'equal' | 'proportional' | 'single_owner' | 'cross_hospital_merge'
      billing_weekdays?: number[]
      billingWeekdays?: number[]
      exclude_departments?: string[]
      excludeDepartments?: string[]
      card_deduction_enabled?: boolean
      cardDeductionEnabled?: boolean
      card_deduct_mode?: 'auto' | 'none'
      cardDeductMode?: 'auto' | 'none'
      card_monthly_cap?: number
      cardMonthlyCap?: number
      logistics_merge_group_id?: number
      logisticsMergeGroupId?: number
      merge_same_day?: boolean
      mergeSameDay?: boolean
      single_owner_customer_id?: number
      singleOwnerCustomerId?: number
      min_charge?: number
      minCharge?: number
      max_cap?: number
      maxCap?: number
      base_multiplier?: number
      baseMultiplier?: number
      adjusted_multiplier?: number
      adjustedMultiplier?: number
      urgent_logistics_fee_per_trip?: number
      urgentLogisticsFeePerTrip?: number
      urgent_logistics_discount_rate?: number
      urgentLogisticsDiscountRate?: number
      monthly_amount?: number
      monthlyAmount?: number
      priority?: number
      is_active?: boolean
      isActive?: boolean
    }

    interface SaveCustomerBillingPolicyPayload {
      policyType: 'DISCOUNT' | 'LOGISTICS' | 'MONTHLY_SETTLEMENT' | 'URGENT' | 'DEDUCTION'
      name?: string
      temperature?: 'HT' | 'LT' | 'ANY'
      rate?: number
      skipWhenFixedPrice?: boolean
      feePerTrip?: number
      tripSource?: 'delivery_date' | 'import'
      allocationMode?: 'none' | 'dept_ratio' | 'equal' | 'proportional' | 'single_owner' | 'cross_hospital_merge'
      billingWeekdays?: number[]
      excludeDepartments?: string[]
      cardDeductionEnabled?: boolean
      cardDeductMode?: 'auto' | 'none'
      cardMonthlyCap?: number
      logisticsMergeGroupId?: number
      mergeSameDay?: boolean
      singleOwnerCustomerId?: number
      minCharge?: number
      maxCap?: number
      baseMultiplier?: number
      adjustedMultiplier?: number
      urgentLogisticsFeePerTrip?: number
      urgentLogisticsDiscountRate?: number
      monthlyAmount?: number
      priority?: number
      isActive?: boolean
    }

    interface CustomerPathOverride {
      disableLowTemp?: boolean
      forceHighTempUnitPrice?: number
    }

    interface CustomerRecord {
      id: number
      code: string
      canonical_name: string
      status?: string
      cap_mode?: string | null
      charge_double_bag_when_capped?: boolean
      billing_enabled?: boolean
      billingEnabled?: boolean
      billing_pricing_mode?: 'standard' | 'special_only' | 'hybrid'
      billingPricingMode?: 'standard' | 'special_only' | 'hybrid'
      path_override?: CustomerPathOverride
      pathOverride?: CustomerPathOverride
      export_name_mapping?: string
      exportNameMapping?: string
      default_rule_id?: number | null
      notes?: string
      aliases?: CustomerAlias[]
      discounts?: CustomerDiscount[]
      product_rules?: CustomerProductRule[]
      alias_count?: number
      department_count?: number
      departmentCount?: number
      physician_count?: number
      physicianCount?: number
      created_at?: string
      updated_at?: string
    }

    interface SaveCustomerPayload {
      code: string
      canonicalName: string
      status?: string
      capMode?: string | null
      chargeDoubleBagWhenCapped?: boolean
      billingEnabled?: boolean
      billingPricingMode?: 'standard' | 'special_only' | 'hybrid'
      pathOverride?: CustomerPathOverride
      exportNameMapping?: string
      defaultRuleId?: number | null
      notes?: string
      aliases?: CustomerAlias[]
      discounts?: CustomerDiscount[]
      productRules?: CustomerProductRule[]
    }
  }

  namespace Billing {
    interface RuleSimulateResult {
      expected_unit_price?: number
      corrected_total_price?: number
      difference?: number
      status?: string
      pricing_rule?: string
      matched_rule_id?: number
      matched_price_option?: number
      notes?: string[]
      policy_traces?: string[]
      match_chain?: Record<string, unknown>[]
    }

    interface RuleChangeLogEntry {
      id: number
      customer_id: number
      change_type: string
      entity_type: string
      change_summary?: string
      operator_name?: string
      created_at?: string
    }
  }
}
