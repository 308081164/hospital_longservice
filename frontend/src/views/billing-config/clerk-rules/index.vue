<template>
  <div class="clerk-rules-page p-4">
    <ElCard shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <div>
            <span class="text-lg font-semibold">{{ t('menus.billingConfig.clerkRules') }}</span>
            <p class="text-sm text-gray-500 mt-1">{{ t('menus.billingConfig.clerkRulesDesc') }}</p>
          </div>
          <ElTag v-if="indexInfo" type="info" size="small">
            {{ t('menus.billingConfig.clerkRulesCount', { count: indexInfo.customerCount }) }}
          </ElTag>
        </div>
      </template>

      <ElTable v-loading="loading" :data="customers" stripe @row-click="openDetail">
        <ElTableColumn prop="customerCode" :label="t('menus.billingConfig.clerkRuleCode')" width="140" />
        <ElTableColumn prop="customerName" :label="t('menus.billingConfig.clerkRuleHospital')" min-width="220" />
        <ElTableColumn :label="t('menus.billingConfig.clerkRuleActive')" width="100" align="center">
          <template #default="{ row }">
            {{ row.activeRuleCount }} / {{ row.ruleCount }}
          </template>
        </ElTableColumn>
        <ElTableColumn :label="t('menus.billingConfig.clerkRulePending')" width="100" align="center">
          <template #default="{ row }">
            <ElTag v-if="row.pendingMigrationCount > 0" type="warning" size="small">
              {{ row.pendingMigrationCount }}
            </ElTag>
            <span v-else class="text-gray-400">—</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="notes" :label="t('menus.billingConfig.clerkRuleNotes')" min-width="240" show-overflow-tooltip />
      </ElTable>
    </ElCard>

    <ElDrawer v-model="drawerVisible" :title="selected?.customerName" size="560px">
      <template v-if="detail">
        <ElAlert
          type="info"
          :closable="false"
          class="mb-4"
          :title="t('menus.billingConfig.clerkRulesScopeHint')"
        />
        <p class="mb-4 text-xs text-gray-500">
          全行业默认物流费、结款函与导出命名请在
          <RouterLink to="/billing-config/bill-operations" class="text-primary">账单运营</RouterLink>
          中维护。
        </p>

        <div v-if="attachmentRefs.length" class="mb-4">
          <div class="text-sm font-medium mb-2">{{ t('menus.billingConfig.clerkRuleAttachments') }}</div>
          <div class="flex flex-wrap gap-2">
            <ElLink
              v-for="(ref, idx) in attachmentRefs"
              :key="idx"
              :href="getClerkRuleAttachmentUrl(ref)"
              target="_blank"
              type="primary"
            >
              {{ fileLabel(ref) }}
            </ElLink>
          </div>
        </div>

        <div v-for="(rule, idx) in detailRules" :key="idx" class="rule-card mb-3">
          <div class="flex items-center justify-between mb-2">
            <strong>{{ rule.name }}</strong>
            <ElTag :type="rule.isActive ? 'success' : 'info'" size="small">
              {{ rule.isActive ? t('menus.billingConfig.clerkRuleEnabled') : t('menus.billingConfig.clerkRuleDisabled') }}
            </ElTag>
          </div>
          <div class="text-sm text-gray-600">
            <div>{{ rule.ruleType }} · {{ rule.stage }}</div>
            <div v-if="rule.params" class="mt-1 text-xs font-mono text-gray-500">
              {{ formatParams(rule.params) }}
            </div>
            <div v-if="rule.sourceText" class="mt-1 text-xs text-gray-500">
              {{ t('menus.billingConfig.clerkRuleSourceText') }}：{{ rule.sourceText }}
            </div>
            <div v-if="rule.migrationStatus" class="mt-1">
              {{ t('menus.billingConfig.clerkRuleMigration') }}: {{ rule.migrationStatus }}
            </div>
            <div v-if="rule.migrateFrom" class="mt-1 text-xs">{{ rule.migrateFrom }}</div>
          </div>
        </div>

        <ElCollapse v-if="detail.compiled" class="mt-4">
          <ElCollapseItem :title="t('menus.billingConfig.clerkRuleCompiled')" name="compiled">
            <pre class="compiled-json">{{ JSON.stringify(detail.compiled, null, 2) }}</pre>
          </ElCollapseItem>
        </ElCollapse>
      </template>
    </ElDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  getClerkRuleAttachmentUrl,
  getClerkRuleCustomer,
  getClerkRuleIndex,
  listClerkRuleCustomers,
  type ClerkRuleCustomerDetail,
  type ClerkRuleCustomerSummary
} from '@/api/billing/clerkRulesApi'

const { t } = useI18n()
const loading = ref(false)
const customers = ref<ClerkRuleCustomerSummary[]>([])
const indexInfo = ref<{ customerCount: number } | null>(null)
const drawerVisible = ref(false)
const selected = ref<ClerkRuleCustomerSummary | null>(null)
const detail = ref<ClerkRuleCustomerDetail | null>(null)

const detailRules = computed(() => {
  const rules = detail.value?.baseline?.rules
  return Array.isArray(rules) ? rules : []
})

const attachmentRefs = computed(() => {
  const refs = detail.value?.baseline?.attachmentRefs
  return Array.isArray(refs) ? refs.map(String) : []
})

function fileLabel(path: string) {
  const parts = path.split('/')
  return parts[parts.length - 1] || path
}

async function load() {
  loading.value = true
  try {
    const [list, index] = await Promise.all([listClerkRuleCustomers(), getClerkRuleIndex()])
    customers.value = list
    indexInfo.value = index
  } finally {
    loading.value = false
  }
}

function formatParams(params: unknown) {
  if (!params || typeof params !== 'object') return ''
  const text = JSON.stringify(params)
  return text.length > 180 ? text.slice(0, 180) + '…' : text
}

async function openDetail(row: ClerkRuleCustomerSummary) {
  selected.value = row
  detail.value = await getClerkRuleCustomer(row.customerCode)
  drawerVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.rule-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
}

.compiled-json {
  font-size: 12px;
  line-height: 1.4;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
