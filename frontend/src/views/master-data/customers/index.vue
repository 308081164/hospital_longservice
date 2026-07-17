<template>
  <div class="customers-page p-4">
    <ElCard shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-lg font-semibold">{{ $t('menus.masterData.customers') }}</span>
          <ElButton type="primary" @click="openCreate">新增客户</ElButton>
        </div>
      </template>

      <ElForm :inline="true" :model="filterForm" class="mb-4 flex flex-wrap items-center gap-y-2">
        <ElFormItem :label="$t('menus.masterData.customerFilters.keyword')">
          <ElInput
            v-model="filterForm.keyword"
            :placeholder="$t('menus.masterData.customerFilters.keywordPlaceholder')"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </ElFormItem>
        <ElFormItem :label="$t('menus.masterData.customerFilters.status')">
          <ElSelect
            v-model="filterForm.status"
            clearable
            :placeholder="$t('menus.masterData.customerFilters.statusAll')"
            style="width: 120px"
            @change="handleSearch"
          >
            <ElOption :label="$t('menus.masterData.customerFilters.statusActive')" value="active" />
            <ElOption
              :label="$t('menus.masterData.customerFilters.statusInactive')"
              value="inactive"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="$t('menus.masterData.customerFilters.capMode')">
          <ElSelect
            v-model="filterForm.capMode"
            clearable
            :placeholder="$t('menus.masterData.customerFilters.capModeAll')"
            style="width: 140px"
            @change="handleSearch"
          >
            <ElOption
              :label="$t('menus.masterData.customerFilters.capModeStandard')"
              value="standard"
            />
            <ElOption :label="$t('menus.masterData.customerFilters.capModeNone')" value="none" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="$t('menus.masterData.customerFilters.productRules')">
          <ElSelect
            v-model="filterForm.hasProductRules"
            clearable
            :placeholder="$t('menus.masterData.customerFilters.productRulesAll')"
            style="width: 140px"
            @change="handleSearch"
          >
            <ElOption :label="$t('menus.masterData.customerFilters.productRulesYes')" value="yes" />
            <ElOption :label="$t('menus.masterData.customerFilters.productRulesNo')" value="no" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="handleSearch">{{
            $t('table.searchBar.search')
          }}</ElButton>
          <ElButton @click="resetFilters">{{ $t('table.searchBar.reset') }}</ElButton>
        </ElFormItem>
      </ElForm>

      <ElTable v-loading="loading" :data="filteredCustomers" stripe border>
        <ElTableColumn prop="code" label="编码" width="120" />
        <ElTableColumn
          prop="canonical_name"
          label="规范名称"
          min-width="200"
          show-overflow-tooltip
        />
        <ElTableColumn prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <ElTag :type="row.status === 'active' ? 'success' : 'info'" size="small">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="商品策略" width="100" align="center">
          <template #default="{ row }">
            <span v-if="productRuleSummaryForCustomer(row)">{{
              productRuleSummaryForCustomer(row)
            }}</span>
            <span v-else class="text-gray-400">—</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="cap_mode" label="封顶模式" width="100" align="center">
          <template #default="{ row }">{{ row.cap_mode || '默认' }}</template>
        </ElTableColumn>
        <ElTableColumn label="别名数" width="80" align="center">
          <template #default="{ row }">{{ row.alias_count ?? row.aliases?.length ?? 0 }}</template>
        </ElTableColumn>
        <ElTableColumn label="科室/医生" width="100" align="center">
          <template #default="{ row }">
            <span v-if="deptPhysicianCountLabel(row)">{{ deptPhysicianCountLabel(row) }}</span>
            <span v-else class="text-gray-400">—</span>
          </template>
        </ElTableColumn>
        <ElTableColumn min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="policySummaryForCustomer(row)">{{ policySummaryForCustomer(row) }}</span>
            <span v-else class="text-gray-400">—</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton type="primary" link @click="openEdit(row)">编辑</ElButton>
            <ElButton type="primary" link @click="openDeptPhysician(row)">
              {{ $t('menus.billingConfig.deptPhysician') }}
            </ElButton>
            <ElButton type="danger" link @click="handleDelete(row)">删除</ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <span class="text-gray-400">{{ $t('menus.masterData.customerFilters.noResults') }}</span>
        </template>
      </ElTable>
    </ElCard>

    <ElDrawer v-model="drawerVisible" size="840px" destroy-on-close>
      <template #header>
        <div class="flex items-center gap-2">
          <span>{{ editingId ? '编辑客户' : '新增客户' }}</span>
          <BillingRoleBadge />
        </div>
      </template>
      <ElForm
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        :disabled="isReadOnlyConfig && !!editingId"
      >
        <ElFormItem label="客户编码" prop="code">
          <ElInput v-model="form.code" placeholder="如 HRB-WY" :disabled="!!editingId" />
        </ElFormItem>
        <ElFormItem label="规范名称" prop="canonicalName">
          <ElInput v-model="form.canonicalName" placeholder="如 哈尔滨市第五医院" />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="form.status" class="w-full">
            <ElOption label="启用" value="active" />
            <ElOption label="停用" value="inactive" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="封顶模式">
          <ElSelect v-model="form.capMode" class="w-full" clearable placeholder="默认">
            <ElOption label="标准封顶 (standard)" value="standard" />
            <ElOption label="不封顶 (none)" value="none" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="封顶双袋计费">
          <ElSwitch v-model="form.chargeDoubleBagWhenCapped" />
        </ElFormItem>
        <ElFormItem :label="$t('menus.masterData.customerForm.billingEnabled')">
          <ElSwitch v-model="form.billingEnabled" />
          <span class="ml-2 text-xs text-gray-500">{{
            $t('menus.masterData.customerForm.billingEnabledHint')
          }}</span>
        </ElFormItem>
        <BillingRegressionHint :billing-enabled="form.billingEnabled" />
        <ElFormItem label="备注">
          <ElInput v-model="form.notes" type="textarea" :rows="2" />
        </ElFormItem>

        <ElDivider>客户别名</ElDivider>
        <div v-for="(alias, idx) in form.aliases" :key="idx" class="flex gap-2 mb-2">
          <ElInput v-model="alias.alias" placeholder="别名" class="flex-1" />
          <ElSelect v-model="alias.matchType" style="width: 110px">
            <ElOption label="包含" value="contains" />
            <ElOption label="精确" value="exact" />
          </ElSelect>
          <ElButton type="danger" link @click="form.aliases?.splice(idx, 1)">删除</ElButton>
        </div>
        <ElButton class="mb-4" @click="addAlias">添加别名</ElButton>

        <CustomerBillingPolicyPanel
          :state="billingPolicyState"
          :read-only="isReadOnlyConfig"
          :customer-id="editingId"
        />

        <ElFormItem v-if="editingId" :label="$t('menus.billingConfig.deptPhysician')">
          <div class="flex flex-wrap items-center gap-2 text-sm">
            <ElTag type="info" effect="plain">
              {{ $t('menus.billingConfig.departmentCount') }}:
              {{ deptPhysicianSummary.departments }}
            </ElTag>
            <ElTag type="info" effect="plain">
              {{ $t('menus.billingConfig.physicianCount') }}: {{ deptPhysicianSummary.physicians }}
            </ElTag>
            <RouterLink :to="customerDeptPhysicianPath(editingId)" class="text-primary text-xs">
              {{ $t('menus.billingConfig.goToManage') }}
            </RouterLink>
          </div>
        </ElFormItem>

        <CustomerExportTemplatePanel
          ref="exportTemplatePanelRef"
          :customer-id="editingId"
          v-model:export-name-mapping="form.exportNameMapping"
        />

        <ElDivider>{{ $t('menus.masterData.customerForm.billingPricingMode') }}</ElDivider>
        <ElFormItem :label="$t('menus.masterData.customerForm.billingPricingMode')">
          <ElSelect v-model="form.billingPricingMode" class="w-full">
            <ElOption
              v-for="opt in billingPricingModeOptions"
              :key="opt.value"
              :label="$t(opt.labelKey)"
              :value="opt.value"
            >
              <div class="billing-pricing-mode-option">
                <span>{{ $t(opt.labelKey) }}</span>
                <span class="billing-pricing-mode-option__desc">{{ $t(opt.descKey) }}</span>
              </div>
            </ElOption>
          </ElSelect>
          <div class="mt-1 text-xs text-gray-500 leading-relaxed">
            <div>{{ $t('menus.masterData.customerForm.billingPricingModeHint') }}</div>
            <div class="mt-1">{{ $t(billingPricingModeDescKey) }}</div>
          </div>
        </ElFormItem>

        <ElCollapse v-model="advancedCollapseActive" class="customer-advanced-collapse">
          <ElCollapseItem
            name="pathOverride"
            :title="$t('menus.masterData.customerForm.pathOverrideTitle')"
          >
            <div class="customer-advanced-collapse__intro">
              <p>{{ $t('menus.masterData.customerForm.pathOverrideDesc') }}</p>
              <ul>
                <li>{{ $t('menus.masterData.customerForm.disableLowTempDesc') }}</li>
                <li>{{ $t('menus.masterData.customerForm.forceHighTempUnitPriceDesc') }}</li>
              </ul>
            </div>
            <ElFormItem :label="$t('menus.masterData.customerForm.disableLowTemp')">
              <ElSwitch v-model="form.pathOverrideDisableLowTemp" />
              <span class="ml-2 text-xs text-gray-500">{{
                $t('menus.masterData.customerForm.disableLowTempHint')
              }}</span>
            </ElFormItem>
            <ElFormItem :label="$t('menus.masterData.customerForm.forceHighTempUnitPrice')">
              <ElInputNumber
                v-model="form.pathOverrideForceHighTempUnitPrice"
                :min="0"
                :step="0.5"
                :precision="2"
                :placeholder="$t('menus.masterData.customerForm.forceHighTempUnitPricePlaceholder')"
                style="width: 200px"
              />
            </ElFormItem>
          </ElCollapseItem>
        </ElCollapse>

        <ElDivider>{{ $t('menus.masterData.customerProductRules.title') }}</ElDivider>
        <div class="product-rules-panel">
          <div class="product-rules-panel__header">
            <span class="product-rules-panel__count">
              {{
                $t('menus.masterData.customerProductRules.ruleCount', {
                  count: pricingRules.length
                })
              }}
            </span>
            <ElButton
              type="primary"
              size="small"
              :disabled="isReadOnlyConfig"
              @click="openCreateRule"
            >
              {{ $t('menus.masterData.customerProductRules.add') }}
            </ElButton>
          </div>

          <div v-if="pricingRules.length === 0" class="product-rules-panel__empty">
            {{ $t('menus.masterData.customerProductRules.empty') }}
          </div>

          <ElTable
            v-else
            :data="pricingRules"
            size="small"
            border
            class="product-rules-panel__table"
          >
            <ElTableColumn
              :label="$t('menus.masterData.customerProductRules.product')"
              min-width="140"
            >
              <template #default="{ row }">
                <span class="font-medium">{{ ruleDisplayName(row, products) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn
              :label="$t('menus.masterData.customerProductRules.ruleType')"
              width="110"
            >
              <template #default="{ row }">
                <ElTag size="small" :type="ruleTagType(row.ruleType)">
                  {{ $t(ruleTypeLabelKey(row.ruleType)) }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn
              :label="$t('menus.masterData.customerProductRules.paramValue')"
              width="130"
            >
              <template #default="{ row }">
                {{ formatRuleValueLabel(row, t) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              :label="$t('menus.masterData.customerProductRules.matchSummary')"
              min-width="180"
            >
              <template #default="{ row }">
                <span class="text-xs text-gray-500">{{ formatRuleMatchSummary(row) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="优先级" width="100" align="center">
              <template #default="{ row, $index }">
                <div class="flex items-center justify-center gap-1">
                  <ElButton link size="small" :disabled="$index === 0" @click="moveRule($index, -1)"
                    >↑</ElButton
                  >
                  <span class="text-xs">{{ row.priority ?? 100 }}</span>
                  <ElButton
                    link
                    size="small"
                    :disabled="$index === pricingRules.length - 1"
                    @click="moveRule($index, 1)"
                    >↓</ElButton
                  >
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn
              :label="$t('menus.masterData.customerProductRules.status')"
              width="72"
              align="center"
            >
              <template #default="{ row }">
                <ElTag size="small" :type="row.isActive === false ? 'info' : 'success'">
                  {{
                    row.isActive === false
                      ? $t('menus.masterData.customerProductRules.inactive')
                      : $t('menus.masterData.customerProductRules.active')
                  }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn
              :label="$t('menus.masterData.customerProductRules.actions')"
              width="120"
              align="center"
              fixed="right"
            >
              <template #default="{ $index }">
                <ElButton type="primary" link @click="openEditRule($index)">
                  {{ $t('menus.masterData.customerProductRules.edit') }}
                </ElButton>
                <ElButton type="danger" link @click="confirmRemoveRule($index)">
                  {{ $t('menus.masterData.customerProductRules.delete') }}
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>

        <CustomerProductRuleDialog
          v-model:visible="ruleDialogVisible"
          :mode="ruleDialogMode"
          :draft="ruleDialogDraft"
          :products="availableProducts"
          :products-loading="productsLoading"
          :lock-product="ruleDialogMode === 'edit'"
          :saving="savingRule"
          @save="handleRuleDialogSave"
        />

        <RuleSimulator :customer-id="editingId" :default-hospital-name="form.canonicalName" />

        <CustomerRuleToolsPanel
          :customer-id="editingId"
          :read-only="isReadOnlyConfig"
          @imported="handleRulesImported"
          @copied="handleRulesImported"
        />

        <div v-if="ruleConflicts.length" class="rule-conflicts mt-3">
          <ElAlert type="warning" show-icon :closable="false" title="检测到规则匹配签名冲突">
            <ul class="text-xs">
              <li v-for="(c, idx) in ruleConflicts" :key="idx">
                规则 #{{ (c.ruleIndexes as number[]).join(', #') }} 可能重复匹配
              </li>
            </ul>
          </ElAlert>
        </div>
      </ElForm>
      <template #footer>
        <ElButton @click="drawerVisible = false">取消</ElButton>
        <ElButton
          v-if="!isReadOnlyConfig || !editingId"
          type="primary"
          :loading="saving"
          @click="submitForm"
          >保存</ElButton
        >
      </template>
    </ElDrawer>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue'
  import { useRouter } from 'vue-router'
  import { useI18n } from 'vue-i18n'
  import type { FormInstance, FormRules } from 'element-plus'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import {
    createCustomer,
    createCustomerBillingPolicy,
    deleteCustomer,
    deleteCustomerBillingPolicy,
    listCustomerBillingPolicies,
    listCustomers,
    getCustomer,
    updateCustomer,
    updateCustomerBillingPolicy,
    updateCustomerProductRule
  } from '@/api/master-data/customersApi'
  import { listProducts } from '@/api/master-data/productsApi'
  import CustomerBillingPolicyPanel from '@/components/business/customers/CustomerBillingPolicyPanel.vue'
  import CustomerExportTemplatePanel from '@/components/business/customers/CustomerExportTemplatePanel.vue'
  import CustomerProductRuleDialog from '@/components/business/customers/CustomerProductRuleDialog.vue'
  import RuleSimulator from '@/components/business/customers/RuleSimulator.vue'
  import CustomerRuleToolsPanel from '@/components/business/customers/CustomerRuleToolsPanel.vue'
  import BillingRoleBadge from '@/components/business/BillingRoleBadge.vue'
  import BillingRegressionHint from '@/components/business/reconciliation/BillingRegressionHint.vue'
  import { useBillingPermission } from '@/composables/useBillingPermission'
  import { validateRuleConflicts } from '@/api/billing/billingRulesApi'
  import {
    applyBillingPoliciesToState,
    createEmptyBillingPolicyState,
    enrichDiscountForPanel,
    formatPolicySummary,
    normalizeDiscountForSave,
    type BillingPolicyPanelState
  } from '@/utils/customerBillingPolicy'
  import { getCustomerGroup, updateCustomerGroup } from '@/api/billing-config/customerGroupsApi'
  import {
    CUSTOMER_PRODUCT_RULE_TYPES,
    createEmptyProductRuleDraft,
    draftToProductRule,
    draftToSavePayload,
    formatRuleMatchSummary,
    formatRuleValueLabel,
    hasSameMatchSignature,
    resolveProductRuleSaveName,
    ruleDisplayName,
    ruleFromRecord,
    ruleTypeLabelKey,
    validateProductRuleDraft,
    type CustomerProductRuleDraft
  } from '@/utils/customerProductRule'

  const { t } = useI18n()
  const router = useRouter()
  const { isReadOnlyConfig } = useBillingPermission()

  const billingPricingModeOptions = [
    {
      value: 'standard',
      labelKey: 'menus.masterData.customerForm.billingPricingModeStandard',
      descKey: 'menus.masterData.customerForm.billingPricingModeStandardDesc'
    },
    {
      value: 'special_only',
      labelKey: 'menus.masterData.customerForm.billingPricingModeSpecialOnly',
      descKey: 'menus.masterData.customerForm.billingPricingModeSpecialOnlyDesc'
    },
    {
      value: 'hybrid',
      labelKey: 'menus.masterData.customerForm.billingPricingModeHybrid',
      descKey: 'menus.masterData.customerForm.billingPricingModeHybridDesc'
    }
  ] as const

  const billingPricingModeDescKey = computed(() => {
    const opt = billingPricingModeOptions.find((o) => o.value === form.billingPricingMode)
    return opt?.descKey ?? billingPricingModeOptions[0].descKey
  })

  const loading = ref(false)
  const saving = ref(false)
  const savingRule = ref(false)
  const ruleDialogVisible = ref(false)
  const ruleDialogMode = ref<'create' | 'edit'>('create')
  const editingRuleIdx = ref<number | null>(null)
  const drawerVisible = ref(false)
  /** 路径覆盖 / 月度结算：默认折叠，点击标题展开 */
  const advancedCollapseActive = ref<string[]>([])
  const editingId = ref<number | null>(null)
  const customers = ref<Api.MasterData.CustomerRecord[]>([])
  const appliedKeyword = ref('')
  const filterForm = reactive({
    keyword: '',
    status: '' as '' | 'active' | 'inactive',
    capMode: '' as '' | 'standard' | 'none',
    hasProductRules: '' as '' | 'yes' | 'no'
  })
  const products = ref<Api.MasterData.ProductRecord[]>([])
  const productsLoading = ref(false)
  const formRef = ref<FormInstance>()

  const PRICING_RULE_TYPES = CUSTOMER_PRODUCT_RULE_TYPES

  const ruleDialogDraft = reactive<CustomerProductRuleDraft>(createEmptyProductRuleDraft())

  const pricingRules = computed(() =>
    (form.productRules ?? []).filter(
      (r) =>
        r.ruleType && PRICING_RULE_TYPES.includes(r.ruleType as (typeof PRICING_RULE_TYPES)[number])
    )
  )

  const billingPolicyState = reactive<BillingPolicyPanelState>(createEmptyBillingPolicyState())
  const deptPhysicianSummary = reactive({ departments: 0, physicians: 0 })
  const exportTemplatePanelRef = ref<InstanceType<typeof CustomerExportTemplatePanel> | null>(null)
  const ruleConflicts = ref<Array<{ signature: string; ruleIndexes: number[] }>>([])

  const form = reactive<
    Api.MasterData.SaveCustomerPayload & {
      pathOverrideDisableLowTemp?: boolean
      pathOverrideForceHighTempUnitPrice?: number
      exportNameMapping?: string
    }
  >({
    code: '',
    canonicalName: '',
    status: 'active',
    capMode: undefined,
    chargeDoubleBagWhenCapped: false,
    billingEnabled: false,
    billingPricingMode: 'standard',
    defaultRuleId: undefined,
    notes: '',
    aliases: [],
    discounts: [],
    productRules: [],
    pathOverrideDisableLowTemp: false,
    pathOverrideForceHighTempUnitPrice: undefined,
    exportNameMapping: undefined
  })

  const rules: FormRules = {
    code: [{ required: true, message: '请输入客户编码', trigger: 'blur' }],
    canonicalName: [{ required: true, message: '请输入规范名称', trigger: 'blur' }]
  }

  function syncBillingPolicyStateFromForm() {
    billingPolicyState.discounts = (form.discounts ?? []).map(enrichDiscountForPanel)
  }

  function syncFormFromBillingPolicyState() {
    form.discounts = (billingPolicyState.discounts ?? []).map(normalizeDiscountForSave)
  }

  function policySummaryForCustomer(row: Api.MasterData.CustomerRecord) {
    return formatPolicySummary(
      {
        discounts: row.discounts ?? [],
        logisticsFeePerTrip: undefined,
        monthlyMinCharge: undefined,
        monthlyMaxCap: undefined
      },
      t
    )
  }

  function productRuleCountForCustomer(row: Api.MasterData.CustomerRecord) {
    return (row.product_rules ?? []).filter((rule) => {
      const ruleType = rule.ruleType ?? (rule as { rule_type?: string }).rule_type
      return (
        ruleType && PRICING_RULE_TYPES.includes(ruleType as (typeof PRICING_RULE_TYPES)[number])
      )
    }).length
  }

  function productRuleSummaryForCustomer(row: Api.MasterData.CustomerRecord) {
    const count = productRuleCountForCustomer(row)
    return count > 0 ? `${count} 条` : null
  }

  const filteredCustomers = computed(() => {
    let data = customers.value
    const kw = appliedKeyword.value.trim().toLowerCase()
    if (kw) {
      data = data.filter(
        (c) => c.code?.toLowerCase().includes(kw) || c.canonical_name?.toLowerCase().includes(kw)
      )
    }
    if (filterForm.status) {
      data = data.filter((c) => (c.status ?? 'active') === filterForm.status)
    }
    if (filterForm.capMode) {
      data = data.filter((c) => c.cap_mode === filterForm.capMode)
    }
    if (filterForm.hasProductRules) {
      data = data.filter((c) => {
        const has = productRuleCountForCustomer(c) > 0
        return filterForm.hasProductRules === 'yes' ? has : !has
      })
    }
    return data
  })

  function handleSearch() {
    appliedKeyword.value = filterForm.keyword.trim()
  }

  function resetFilters() {
    filterForm.keyword = ''
    filterForm.status = ''
    filterForm.capMode = ''
    filterForm.hasProductRules = ''
    appliedKeyword.value = ''
  }

  const availableProducts = computed(() => products.value)

  function resetRuleDialog() {
    ruleDialogVisible.value = false
    editingRuleIdx.value = null
    Object.assign(ruleDialogDraft, createEmptyProductRuleDraft())
  }

  function resetForm() {
    advancedCollapseActive.value = []
    form.code = ''
    form.canonicalName = ''
    form.status = 'active'
    form.capMode = undefined
    form.chargeDoubleBagWhenCapped = false
    form.billingEnabled = false
    form.billingPricingMode = 'standard'
    form.defaultRuleId = undefined
    form.notes = ''
    form.aliases = []
    form.discounts = []
    form.productRules = []
    form.pathOverrideDisableLowTemp = false
    form.pathOverrideForceHighTempUnitPrice = undefined
    form.exportNameMapping = undefined
    Object.assign(billingPolicyState, createEmptyBillingPolicyState())
    resetRuleDialog()
  }

  function normalizeProductRule(
    rule: Api.MasterData.CustomerProductRule
  ): Api.MasterData.CustomerProductRule {
    const draft = ruleFromRecord(rule)
    const productId = rule.productId ?? rule.product_id
    const ruleType = rule.ruleType ?? (rule as { rule_type?: string }).rule_type
    return {
      ...draftToProductRule(draft, rule.productName ?? rule.product_name),
      id: rule.id,
      productId,
      ruleType,
      productName: rule.productName ?? rule.product_name,
      keywords: draft.keywords,
      excludeKeywords: draft.excludeKeywords,
      materials: draft.materials,
      matchMode: draft.matchMode,
      acceptedPrices: draft.acceptedPrices,
      temperature: draft.temperature,
      bagSizeEquals: draft.bagSizeEquals,
      maxBagSizeExclusive: draft.maxBagSizeExclusive,
      minInstrumentCount: draft.minInstrumentCount,
      maxInstrumentCount: draft.maxInstrumentCount,
      skipPackaging: draft.skipPackaging,
      skipDiscount: draft.skipDiscount,
      price: draft.price,
      fixed_price: draft.price,
      fee: draft.fee,
      threshold: draft.threshold,
      foldRatio: draft.foldRatio,
      isActive: draft.isActive
    }
  }

  function ruleTagType(ruleType?: string): 'success' | 'warning' | 'info' | '' {
    if (ruleType === 'FOLD') return 'warning'
    if (ruleType === 'EXTRA_FEE' || ruleType === 'ADD_FEE') return 'info'
    return ''
  }

  function hasRuleConflict(draft: CustomerProductRuleDraft, excludeIdx?: number) {
    return pricingRules.value.some((rule, idx) => {
      if (excludeIdx != null && idx === excludeIdx) return false
      return hasSameMatchSignature(draft, ruleFromRecord(rule))
    })
  }

  async function loadProducts() {
    productsLoading.value = true
    try {
      products.value = await listProducts()
    } catch {
      ElMessage.error('加载商品列表失败')
    } finally {
      productsLoading.value = false
    }
  }

  function openCreateRule() {
    ruleDialogMode.value = 'create'
    editingRuleIdx.value = null
    Object.assign(ruleDialogDraft, createEmptyProductRuleDraft())
    ruleDialogVisible.value = true
  }

  function openEditRule(idx: number) {
    const target = pricingRules.value[idx]
    if (!target) return
    ruleDialogMode.value = 'edit'
    editingRuleIdx.value = idx
    Object.assign(ruleDialogDraft, ruleFromRecord(target))
    ruleDialogVisible.value = true
  }

  async function handleRuleDialogSave() {
    const error = validateProductRuleDraft(ruleDialogDraft)
    if (error) {
      ElMessage.warning(error)
      return
    }
    const excludeIdx =
      ruleDialogMode.value === 'edit' ? (editingRuleIdx.value ?? undefined) : undefined
    if (hasRuleConflict(ruleDialogDraft, excludeIdx)) {
      ElMessage.warning('相同类型与匹配条件下已存在策略')
      return
    }

    if (ruleDialogMode.value === 'create') {
      const product = products.value.find((p) => p.id === ruleDialogDraft.productId)
      form.productRules = form.productRules ?? []
      form.productRules.push(draftToProductRule(ruleDialogDraft, product?.name))
      resetRuleDialog()
      return
    }

    const idx = editingRuleIdx.value
    if (idx == null) return
    const target = pricingRules.value[idx]
    if (!target) return

    const product = products.value.find((p) => p.id === ruleDialogDraft.productId)
    const resolvedProductName = product?.name ?? target.productName ?? target.product_name

    const payload = draftToSavePayload(ruleDialogDraft, resolvedProductName)
    payload.name = resolveProductRuleSaveName(ruleDialogDraft, resolvedProductName)

    if (target.id && editingId.value) {
      savingRule.value = true
      try {
        const updated = await updateCustomerProductRule(editingId.value, target.id, payload)
        const normalized = normalizeProductRule(updated)
        const globalIdx = (form.productRules ?? []).indexOf(target)
        if (globalIdx >= 0) {
          form.productRules![globalIdx] = normalized
        }
        ElMessage.success('计价策略已更新')
        resetRuleDialog()
      } catch (e: unknown) {
        ElMessage.error(e instanceof Error ? e.message : '更新失败')
      } finally {
        savingRule.value = false
      }
    } else {
      Object.assign(target, draftToProductRule(ruleDialogDraft, target.productName ?? target.name))
      resetRuleDialog()
    }
  }

  async function confirmRemoveRule(idx: number) {
    const target = pricingRules.value[idx]
    if (!target) return
    try {
      await ElMessageBox.confirm(
        t('menus.masterData.customerProductRules.deleteConfirm'),
        t('common.tips'),
        { type: 'warning' }
      )
      removePricingRule(idx)
    } catch {
      // cancelled
    }
  }

  function removePricingRule(idx: number) {
    if (editingRuleIdx.value === idx) {
      resetRuleDialog()
    } else if (editingRuleIdx.value != null && editingRuleIdx.value > idx) {
      editingRuleIdx.value -= 1
    }
    const rules = pricingRules.value
    const target = rules[idx]
    if (!target) return
    const globalIdx = (form.productRules ?? []).indexOf(target)
    if (globalIdx >= 0) {
      form.productRules?.splice(globalIdx, 1)
    }
    void refreshRuleConflicts()
  }

  function moveRule(idx: number, delta: number) {
    const rules = form.productRules ?? []
    const pricingOnly = pricingRules.value
    const target = pricingOnly[idx]
    if (!target) return
    const globalIdx = rules.indexOf(target)
    const swapTarget = pricingOnly[idx + delta]
    if (globalIdx < 0 || !swapTarget) return
    const swapGlobalIdx = rules.indexOf(swapTarget)
    if (swapGlobalIdx < 0) return
    const tempPriority = target.priority ?? 100
    target.priority = swapTarget.priority ?? 100
    swapTarget.priority = tempPriority
    ;[rules[globalIdx], rules[swapGlobalIdx]] = [rules[swapGlobalIdx], rules[globalIdx]]
    void refreshRuleConflicts()
  }

  async function refreshRuleConflicts() {
    if (!editingId.value || pricingRules.value.length < 2) {
      ruleConflicts.value = []
      return
    }
    try {
      const payloadRules = pricingRules.value.map((r) => ({
        ruleType: r.ruleType,
        productId: r.productId ?? r.product_id,
        variantId: r.variantId ?? r.variant_id,
        keywords: r.keywords,
        temperature: r.temperature,
        bagSizeEquals: r.bagSizeEquals ?? r.bag_size_equals,
        maxBagSizeExclusive: r.maxBagSizeExclusive ?? r.max_bag_size_exclusive,
        minInstrumentCount: r.minInstrumentCount ?? r.min_instrument_count,
        maxInstrumentCount: r.maxInstrumentCount ?? r.max_instrument_count
      }))
      const res = await validateRuleConflicts({ customerId: editingId.value, rules: payloadRules })
      ruleConflicts.value = (res.conflicts ?? []) as Array<{
        signature: string
        ruleIndexes: number[]
      }>
    } catch {
      ruleConflicts.value = []
    }
  }

  watch(pricingRules, () => {
    if (editingId.value) {
      void refreshRuleConflicts()
    }
  })

  function addAlias() {
    form.aliases = form.aliases ?? []
    form.aliases.push({
      alias: '',
      matchType: 'contains',
      source: 'manual',
      priority: 100,
      isActive: true
    })
  }

  async function loadData() {
    loading.value = true
    try {
      customers.value = await listCustomers()
    } catch {
      ElMessage.error('加载客户列表失败')
    } finally {
      loading.value = false
    }
  }

  async function loadBillingPolicies(customerId: number) {
    try {
      const policies = await listCustomerBillingPolicies(customerId)
      applyBillingPoliciesToState(billingPolicyState, policies)
    } catch {
      billingPolicyState.logisticsPolicyId = undefined
      billingPolicyState.logisticsFeePerTrip = undefined
      billingPolicyState.logisticsPriority = 100
      billingPolicyState.logisticsActive = true
      billingPolicyState.monthlyPolicyId = undefined
      billingPolicyState.monthlyMinCharge = undefined
      billingPolicyState.monthlyMaxCap = undefined
      billingPolicyState.monthlyPriority = 100
      billingPolicyState.monthlyActive = true
    }
  }

  async function loadLogisticsPolicy(customerId: number) {
    await loadBillingPolicies(customerId)
  }

  async function syncMonthlyPolicy(customerId: number) {
    const active = billingPolicyState.monthlyActive !== false
    const hasMin =
      billingPolicyState.monthlyMinCharge != null && billingPolicyState.monthlyMinCharge > 0
    const hasMax = billingPolicyState.monthlyMaxCap != null && billingPolicyState.monthlyMaxCap > 0
    if (!active || (!hasMin && !hasMax)) {
      if (billingPolicyState.monthlyPolicyId) {
        await deleteCustomerBillingPolicy(customerId, billingPolicyState.monthlyPolicyId)
        billingPolicyState.monthlyPolicyId = undefined
      }
      return
    }
    const payload: Api.MasterData.SaveCustomerBillingPolicyPayload = {
      policyType: 'MONTHLY_SETTLEMENT',
      name: '月度结算',
      minCharge: hasMin ? billingPolicyState.monthlyMinCharge : undefined,
      maxCap: hasMax ? billingPolicyState.monthlyMaxCap : undefined,
      priority: billingPolicyState.monthlyPriority ?? 100,
      isActive: true
    }
    if (billingPolicyState.monthlyPolicyId) {
      await updateCustomerBillingPolicy(customerId, billingPolicyState.monthlyPolicyId, payload)
    } else {
      const created = await createCustomerBillingPolicy(customerId, payload)
      billingPolicyState.monthlyPolicyId = created.id
    }
  }

  async function syncUrgentPolicy(customerId: number) {
    const active = billingPolicyState.urgentActive !== false
    if (!active) {
      if (billingPolicyState.urgentPolicyId) {
        await deleteCustomerBillingPolicy(customerId, billingPolicyState.urgentPolicyId)
        billingPolicyState.urgentPolicyId = undefined
      }
      return
    }
    const payload: Api.MasterData.SaveCustomerBillingPolicyPayload = {
      policyType: 'URGENT',
      name: '加急收费',
      baseMultiplier: billingPolicyState.urgentBaseMultiplier ?? 1.25,
      adjustedMultiplier: billingPolicyState.urgentAdjustedMultiplier ?? 1.025,
      urgentLogisticsFeePerTrip: billingPolicyState.urgentLogisticsFeePerTrip ?? 150,
      urgentLogisticsDiscountRate: billingPolicyState.urgentLogisticsDiscountRate ?? 0.9,
      priority: billingPolicyState.urgentPriority ?? 100,
      isActive: true
    }
    if (billingPolicyState.urgentPolicyId) {
      await updateCustomerBillingPolicy(customerId, billingPolicyState.urgentPolicyId, payload)
    } else {
      const created = await createCustomerBillingPolicy(customerId, payload)
      billingPolicyState.urgentPolicyId = created.id
    }
  }

  async function syncDeductionPolicy(customerId: number) {
    const active = billingPolicyState.deductionActive !== false
    const amount = billingPolicyState.deductionMonthlyAmount
    if (!active || amount == null || amount <= 0) {
      if (billingPolicyState.deductionPolicyId) {
        await deleteCustomerBillingPolicy(customerId, billingPolicyState.deductionPolicyId)
        billingPolicyState.deductionPolicyId = undefined
      }
      return
    }
    const payload: Api.MasterData.SaveCustomerBillingPolicyPayload = {
      policyType: 'DEDUCTION',
      name: '设备抵扣',
      monthlyAmount: amount,
      priority: billingPolicyState.deductionPriority ?? 100,
      isActive: true
    }
    if (billingPolicyState.deductionPolicyId) {
      await updateCustomerBillingPolicy(customerId, billingPolicyState.deductionPolicyId, payload)
    } else {
      const created = await createCustomerBillingPolicy(customerId, payload)
      billingPolicyState.deductionPolicyId = created.id
    }
  }

  function deptPhysicianCountLabel(row: Api.MasterData.CustomerRecord) {
    const d = row.department_count ?? row.departmentCount
    const p = row.physician_count ?? row.physicianCount
    if (d == null && p == null) return null
    return `${d ?? 0}/${p ?? 0}`
  }

  async function syncLogisticsMergeGroup(customerId: number) {
    const groupId = billingPolicyState.logisticsMergeGroupId
    if (!groupId) return
    try {
      const group = await getCustomerGroup(groupId)
      const members = (group.members ?? []).map((m) => ({
        customerId: m.customer_id ?? m.customerId ?? 0,
        shareRatio:
          billingPolicyState.logisticsMergeShareRatios?.[m.customer_id ?? m.customerId ?? 0] ??
          m.share_ratio ??
          m.shareRatio ??
          null
      }))
      const exists = members.some((m) => m.customerId === customerId)
      if (!exists) {
        members.push({
          customerId,
          shareRatio: billingPolicyState.logisticsMergeShareRatios?.[customerId] ?? null
        })
      }
      await updateCustomerGroup(groupId, {
        name: group.name,
        groupType: group.group_type ?? group.groupType ?? 'logistics_merge',
        config: group.config ?? undefined,
        isActive: group.is_active ?? group.isActive ?? true,
        members
      })
    } catch {
      // non-blocking: policy JSON still stores group reference
    }
  }

  async function syncLogisticsPolicy(customerId: number) {
    const fee = billingPolicyState.logisticsFeePerTrip
    const active = billingPolicyState.logisticsActive !== false
    if (!active || fee == null || fee <= 0) {
      if (billingPolicyState.logisticsPolicyId) {
        await deleteCustomerBillingPolicy(customerId, billingPolicyState.logisticsPolicyId)
        billingPolicyState.logisticsPolicyId = undefined
      }
      return
    }
    const payload: Api.MasterData.SaveCustomerBillingPolicyPayload = {
      policyType: 'LOGISTICS',
      name: '物流费',
      feePerTrip: fee,
      tripSource: billingPolicyState.logisticsTripSource,
      allocationMode: billingPolicyState.logisticsAllocationMode,
      billingWeekdays: billingPolicyState.logisticsBillingWeekdays?.length
        ? billingPolicyState.logisticsBillingWeekdays
        : undefined,
      excludeDepartments: billingPolicyState.logisticsExcludeDepartments
        ? billingPolicyState.logisticsExcludeDepartments
            .split(/[、,，]/)
            .map((s) => s.trim())
            .filter(Boolean)
        : undefined,
      cardDeductionEnabled: billingPolicyState.logisticsCardDeductionEnabled,
      cardDeductMode: billingPolicyState.logisticsCardDeductMode,
      cardMonthlyCap: billingPolicyState.logisticsCardMonthlyCap,
      logisticsMergeGroupId: billingPolicyState.logisticsMergeGroupId,
      mergeSameDay: billingPolicyState.logisticsMergeSameDay,
      singleOwnerCustomerId: billingPolicyState.logisticsSingleOwnerCustomerId,
      priority: billingPolicyState.logisticsPriority ?? 100,
      isActive: true
    }
    if (billingPolicyState.logisticsPolicyId) {
      await updateCustomerBillingPolicy(customerId, billingPolicyState.logisticsPolicyId, payload)
    } else {
      const created = await createCustomerBillingPolicy(customerId, payload)
      billingPolicyState.logisticsPolicyId = created.id
    }
    await syncLogisticsMergeGroup(customerId)
  }

  function openCreate() {
    editingId.value = null
    resetForm()
    syncBillingPolicyStateFromForm()
    drawerVisible.value = true
    void loadProducts()
  }

  function customerDeptPhysicianPath(customerId: number | string) {
    return `/master-data/customers/${customerId}/dept-physician`
  }

  function resolveCustomerRowId(row: Api.MasterData.CustomerRecord): number | undefined {
    const id = row.id ?? (row as { customer_id?: number }).customer_id
    if (id == null || Number.isNaN(Number(id))) return undefined
    return Number(id)
  }

  function openDeptPhysician(row: Api.MasterData.CustomerRecord) {
    const customerId = resolveCustomerRowId(row)
    if (!customerId) {
      ElMessage.warning('无法获取客户 ID')
      return
    }
    void router.push(customerDeptPhysicianPath(customerId))
  }

  function openEdit(row: Api.MasterData.CustomerRecord) {
    advancedCollapseActive.value = []
    editingId.value = row.id
    form.code = row.code
    form.canonicalName = row.canonical_name
    form.status = row.status ?? 'active'
    form.capMode = row.cap_mode ?? undefined
    form.chargeDoubleBagWhenCapped = row.charge_double_bag_when_capped ?? false
    form.billingEnabled = row.billing_enabled ?? row.billingEnabled ?? false
    form.billingPricingMode = row.billing_pricing_mode ?? row.billingPricingMode ?? 'standard'
    const pathOverride = row.path_override ?? row.pathOverride
    form.pathOverrideDisableLowTemp = pathOverride?.disableLowTemp ?? false
    form.pathOverrideForceHighTempUnitPrice = pathOverride?.forceHighTempUnitPrice
    form.exportNameMapping = row.export_name_mapping ?? row.exportNameMapping
    form.defaultRuleId = row.default_rule_id ?? undefined
    form.notes = row.notes ?? ''
    form.aliases = (row.aliases ?? []).map((a) => ({ ...a }))
    form.discounts = (row.discounts ?? []).map((d) =>
      enrichDiscountForPanel({
        ...d,
        temperature: d.temperature ?? 'ANY',
        priority: d.priority ?? 100,
        isActive: d.isActive ?? true
      })
    )
    syncBillingPolicyStateFromForm()
    form.productRules = (row.product_rules ?? []).map(normalizeProductRule)
    deptPhysicianSummary.departments = row.department_count ?? row.departmentCount ?? 0
    deptPhysicianSummary.physicians = row.physician_count ?? row.physicianCount ?? 0
    resetRuleDialog()
    drawerVisible.value = true
    void loadProducts()
    void loadLogisticsPolicy(row.id)
    void refreshRuleConflicts()
  }

  async function handleRulesImported() {
    if (!editingId.value) return
    try {
      const row = await getCustomer(editingId.value)
      form.productRules = (row.product_rules ?? []).map(normalizeProductRule)
      await loadData()
      void refreshRuleConflicts()
    } catch {
      ElMessage.warning(t('billingRules.import.reloadFailed'))
    }
  }

  async function submitForm() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
      syncFormFromBillingPolicyState()
      const payload: Api.MasterData.SaveCustomerPayload = {
        ...form,
        exportNameMapping:
          exportTemplatePanelRef.value?.getExportNameMapping?.() ?? form.exportNameMapping,
        pathOverride: {
          disableLowTemp: form.pathOverrideDisableLowTemp || undefined,
          forceHighTempUnitPrice: form.pathOverrideForceHighTempUnitPrice
        }
      }
      if (editingId.value) {
        await updateCustomer(editingId.value, payload)
        await syncLogisticsPolicy(editingId.value)
        await syncMonthlyPolicy(editingId.value)
        await syncUrgentPolicy(editingId.value)
        await syncDeductionPolicy(editingId.value)
        ElMessage.success('客户已更新')
      } else {
        const created = await createCustomer(payload)
        if (
          billingPolicyState.logisticsActive !== false &&
          billingPolicyState.logisticsFeePerTrip != null &&
          billingPolicyState.logisticsFeePerTrip > 0
        ) {
          await syncLogisticsPolicy(created.id)
        }
        if (
          billingPolicyState.monthlyActive !== false &&
          ((billingPolicyState.monthlyMinCharge != null &&
            billingPolicyState.monthlyMinCharge > 0) ||
            (billingPolicyState.monthlyMaxCap != null && billingPolicyState.monthlyMaxCap > 0))
        ) {
          await syncMonthlyPolicy(created.id)
        }
        if (billingPolicyState.urgentActive !== false) {
          await syncUrgentPolicy(created.id)
        }
        if (
          billingPolicyState.deductionActive !== false &&
          billingPolicyState.deductionMonthlyAmount != null &&
          billingPolicyState.deductionMonthlyAmount > 0
        ) {
          await syncDeductionPolicy(created.id)
        }
        ElMessage.success('客户已创建')
      }
      drawerVisible.value = false
      await loadData()
    } catch (e: unknown) {
      ElMessage.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      saving.value = false
    }
  }

  async function handleDelete(row: Api.MasterData.CustomerRecord) {
    try {
      await ElMessageBox.confirm(`确定删除客户「${row.canonical_name}」？`, '删除确认', {
        type: 'warning'
      })
      await deleteCustomer(row.id)
      ElMessage.success('已删除')
      await loadData()
    } catch {
      // cancelled or failed
    }
  }

  onMounted(() => {
    void loadData()
  })
</script>

<style scoped>
  .product-rules-panel__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  .product-rules-panel__count {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  .product-rules-panel__empty {
    padding: 24px 16px;
    text-align: center;
    font-size: 13px;
    color: var(--el-text-color-placeholder);
    border: 1px dashed var(--el-border-color);
    border-radius: 6px;
  }

  .product-rules-panel__table {
    width: 100%;
  }

  .billing-pricing-mode-option {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 2px 0;
    line-height: 1.4;
    white-space: normal;
  }

  .billing-pricing-mode-option__desc {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .customer-advanced-collapse {
    margin: 8px 0 16px;
    border-top: 1px solid var(--el-border-color-lighter);
  }

  .customer-advanced-collapse :deep(.el-collapse-item__header) {
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    border-bottom: 1px solid var(--el-border-color-lighter);
    padding-left: 0;
    height: 44px;
    line-height: 44px;
  }

  .customer-advanced-collapse :deep(.el-collapse-item__wrap) {
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .customer-advanced-collapse :deep(.el-collapse-item__content) {
    padding: 12px 0 4px;
  }

  .customer-advanced-collapse__intro {
    margin-bottom: 12px;
    padding: 10px 12px;
    font-size: 12px;
    line-height: 1.6;
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color-lighter);
    border-radius: 6px;
  }

  .customer-advanced-collapse__intro p {
    margin: 0 0 6px;
  }

  .customer-advanced-collapse__intro ul {
    margin: 0;
    padding-left: 1.2em;
  }

  .customer-advanced-collapse__intro li + li {
    margin-top: 4px;
  }
</style>
