<template>
  <div class="bill-operations-page p-4">
    <header class="page-header">
      <div class="page-header__text">
        <h1 class="page-title">{{ t('menus.billingConfig.billOperations') }}</h1>
        <p class="page-desc">
          {{ t('menus.billingConfig.billOperationsDesc') }}
          <RouterLink to="/billing-config/clerk-rules" class="link-inline">{{ t('menus.billingConfig.clerkRules') }}</RouterLink>
          {{ t('menus.billingConfig.billOperationsClerkHint') }}
        </p>
      </div>
      <div v-if="currentRule" class="page-header__actions">
        <ElTag v-if="currentRule.isActive" type="success" size="small">已激活</ElTag>
        <ElTag v-else type="info" size="small">未激活</ElTag>
        <ElButton type="primary" :loading="saving" @click="handleSave">
          保存<span v-if="dirty" class="dirty-dot">●</span>
        </ElButton>
      </div>
    </header>

    <ElEmpty v-if="loading" description="正在加载账单运营规则..." class="py-16" />
    <div v-else-if="loadError" class="state-panel">
      <p class="state-title">无法加载规则数据</p>
      <p class="state-desc">请确认后端服务已启动</p>
      <ElButton type="primary" @click="loadRules">重新加载</ElButton>
    </div>
    <div v-else-if="!currentRule" class="state-panel">
      <p class="state-title">尚未配置通用计价规则</p>
      <p class="state-desc">账单运营配置依附于全行业通用计价方案，请先创建通用规则</p>
      <RouterLink to="/settings/pricing-rules">
        <ElButton type="primary">前往通用计价规则</ElButton>
      </RouterLink>
    </div>

    <template v-else>
      <ElAlert
        v-if="validationErrors.length"
        title="配置校验警告"
        type="warning"
        :description="validationErrors.join('；')"
        show-icon
        closable
        class="mb-4"
      />

      <ElCard shadow="never" class="meta-card mb-4">
        <div class="meta-row">
          <span class="meta-label">关联方案</span>
          <span class="meta-value">{{ currentRule.name }}</span>
          <RouterLink to="/settings/pricing-rules" class="link-inline text-xs">在通用计价规则中查看灭菌价目</RouterLink>
        </div>
      </ElCard>

      <ElTabs v-model="activeTab" class="rule-tabs mb-4">
        <ElTabPane :label="t('menus.billingConfig.billOpsLogistics')" name="logistics" />
        <ElTabPane :label="t('menus.billingConfig.billOpsSettlement')" name="settlement" />
        <ElTabPane :label="t('menus.billingConfig.billOpsExport')" name="export" />
      </ElTabs>

      <RuleCategoryPanel
        v-if="activeTab === 'logistics'"
        category="物流"
        title="物流规则"
        subtitle="按发货日期去重计次收取物流费（全局默认，各院可在客户策略中覆盖）"
        theme="logistics"
        badge="物流"
        tag="运费"
      >
        <RuleLogisticsPanel v-model="currentRule.rules.logistics" @change="markDirty" />
      </RuleCategoryPanel>

      <RuleCategoryPanel
        v-else-if="activeTab === 'settlement'"
        category="结款函"
        title="结款函规则"
        subtitle="导出结款函的模板、格式与费用项"
        theme="settlement"
        badge="结款"
        tag="导出"
      >
        <RuleSettlementLetterPanel v-model="currentRule.rules.settlementLetter" @change="markDirty" />
      </RuleCategoryPanel>

      <RuleCategoryPanel
        v-else-if="activeTab === 'export'"
        category="导出"
        title="导出选项"
        subtitle="账单、异常表与结款函的文件命名及页面选项"
        theme="export"
        badge="导出"
        tag="文件"
      >
        <RuleExportOptionsPanel v-model="currentRule.rules.exportOptions" @change="markDirty" />
      </RuleCategoryPanel>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { listHospitalPricingRules, updateHospitalPricingRule } from '@/api/hospital/pricingRulesApi'
import { validatePricingRules } from '@/api/hospital/pricingRules'
import { isGeneralPricingRule } from '@/utils/pricingRuleScope'
import RuleCategoryPanel from '@/components/business/pricing-rules/RuleCategoryPanel.vue'
import RuleLogisticsPanel from '@/components/business/pricing-rules/RuleLogisticsPanel.vue'
import RuleSettlementLetterPanel from '@/components/business/pricing-rules/RuleSettlementLetterPanel.vue'
import RuleExportOptionsPanel from '@/components/business/pricing-rules/RuleExportOptionsPanel.vue'

defineOptions({ name: 'BillingConfigBillOperations' })

const { t } = useI18n()

const ruleList = ref<Api.Hospital.PricingRuleRecord[]>([])
const loading = ref(true)
const loadError = ref(false)
const saving = ref(false)
const dirty = ref(false)
const activeTab = ref('logistics')

const currentRule = computed(() => {
  if (!ruleList.value.length) return null
  return ruleList.value.find((r) => r.name?.includes('标准')) ?? ruleList.value[0]
})

const validationErrors = computed(() => {
  if (!currentRule.value) return []
  return validatePricingRules(currentRule.value.rules).errors
})

function markDirty() {
  dirty.value = true
}

async function loadRules() {
  loading.value = true
  loadError.value = false
  try {
    const all = await listHospitalPricingRules()
    ruleList.value = all.filter(isGeneralPricingRule)
    dirty.value = false
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!currentRule.value) return
  const check = validatePricingRules(currentRule.value.rules)
  if (!check.valid) {
    ElMessage.warning(`配置校验未通过：${check.errors.slice(0, 3).join('；')}`)
    return
  }
  saving.value = true
  try {
    const payload: Api.Hospital.SavePricingRulePayload = {
      name: currentRule.value.name,
      version: currentRule.value.rules.version,
      description: currentRule.value.description,
      rules: currentRule.value.rules,
    }
    const updated = await updateHospitalPricingRule(currentRule.value.id, payload)
    const idx = ruleList.value.findIndex((r) => r.id === updated.id)
    if (idx >= 0) ruleList.value[idx] = updated
    dirty.value = false
    ElMessage.success('保存成功')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadRules()
})
</script>

<style scoped>
.bill-operations-page {
  max-width: 1280px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.page-desc {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.link-inline {
  color: var(--el-color-primary);
  text-decoration: none;
}

.link-inline:hover {
  text-decoration: underline;
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dirty-dot {
  margin-left: 4px;
  color: #f59e0b;
}

.state-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64px 16px;
  color: var(--el-text-color-secondary);
}

.state-title {
  margin: 0 0 8px;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.state-desc {
  margin: 0 0 16px;
  font-size: 13px;
}

.meta-card :deep(.el-card__body) {
  padding: 14px 20px;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.meta-label {
  color: var(--el-text-color-secondary);
}

.meta-value {
  font-weight: 600;
}
</style>
