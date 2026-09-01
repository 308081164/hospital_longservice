<template>
  <div class="version-mgt-page p-6">
    <ElCard shadow="never">
      <ReconciliationHistoryWorkspace
        :history="history"
        :title="t('menus.settings.versionManagement')"
        :subtitle="t('versionManagement.list.subtitle')"
        @refresh="history.loadHistoryAll()"
        @detail="(item) => jobDialogsRef?.openDetail(item)"
        @review="(item) => jobDialogsRef?.openReview(item)"
        @export="(item, type) => jobDialogsRef?.requestExport(item, type)"
      />
    </ElCard>

    <ReconciliationJobDialogs
      ref="jobDialogsRef"
      :active-rule="activeRule"
      @patch-history="history.patchHistoryItem"
      @history-changed="history.loadHistoryAll()"
    />
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import { getActiveHospitalPricingRule, listHospitalPricingRules } from '@/api/hospital/pricingRulesApi'
  import { useReconciliationHistory } from '@/composables/useReconciliationHistory'
  import ReconciliationHistoryWorkspace from '@/components/business/reconciliation/ReconciliationHistoryWorkspace.vue'
  import ReconciliationJobDialogs from '@/components/business/reconciliation/ReconciliationJobDialogs.vue'

  defineOptions({ name: 'VersionManagement' })

  const { t } = useI18n()
  const history = useReconciliationHistory()
  const jobDialogsRef = ref<InstanceType<typeof ReconciliationJobDialogs> | null>(null)
  const activeRule = ref<Api.Hospital.PricingRuleRecord | null>(null)

  onMounted(async () => {
    try {
      activeRule.value = await getActiveHospitalPricingRule()
    } catch {
      try {
        const rules = await listHospitalPricingRules()
        activeRule.value = rules.find((r) => r.name === '标准灭菌计费规则') ?? rules[0] ?? null
      } catch {
        activeRule.value = null
      }
    }
    try {
      await history.loadHistoryAll()
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : t('reconciliation.history.loadFailed'))
    }
  })
</script>
