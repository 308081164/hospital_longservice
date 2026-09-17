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

    <ElDrawer v-model="drawerVisible" :title="selected?.customerName" size="520px">
      <template v-if="detail">
        <ElAlert
          type="info"
          :closable="false"
          class="mb-4"
          :title="t('menus.billingConfig.clerkRulesScopeHint')"
        />
        <div v-for="(rule, idx) in detailRules" :key="idx" class="rule-card mb-3">
          <div class="flex items-center justify-between mb-2">
            <strong>{{ rule.name }}</strong>
            <ElTag :type="rule.isActive ? 'success' : 'info'" size="small">
              {{ rule.isActive ? t('menus.billingConfig.clerkRuleEnabled') : t('menus.billingConfig.clerkRuleDisabled') }}
            </ElTag>
          </div>
          <div class="text-sm text-gray-600">
            <div>{{ rule.ruleType }} · {{ rule.stage }}</div>
            <div v-if="rule.migrationStatus" class="mt-1">
              {{ t('menus.billingConfig.clerkRuleMigration') }}: {{ rule.migrationStatus }}
            </div>
            <div v-if="rule.migrateFrom" class="mt-1 text-xs">{{ rule.migrateFrom }}</div>
          </div>
        </div>
      </template>
    </ElDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
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
</style>
