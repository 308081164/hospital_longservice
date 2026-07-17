<template>
  <div class="customer-rule-tools">
    <ElDivider>{{ t('billingRules.tools.title') }}</ElDivider>

    <ElAlert
      v-if="readOnly"
      type="info"
      :closable="false"
      show-icon
      class="mb-3"
      :title="t('billing.permission.readOnlyConfig')"
    />

    <div class="flex flex-wrap gap-2 mb-3">
      <ElSelect
        v-model="copySourceId"
        filterable
        clearable
        :disabled="!customerId || readOnly"
        :placeholder="t('billingRules.tools.copyFromPlaceholder')"
        class="w-64"
      >
        <ElOption
          v-for="c in otherCustomers"
          :key="c.id"
          :label="`${c.canonical_name} (${c.code})`"
          :value="c.id"
        />
      </ElSelect>
      <ElButton
        :disabled="!copySourceId || !customerId || readOnly"
        :loading="copying"
        @click="handleCopyFrom"
      >
        {{ t('billingRules.tools.copyFrom') }}
      </ElButton>
      <ElSelect
        v-model="selectedTemplateKey"
        clearable
        :disabled="!customerId || readOnly"
        :placeholder="t('billingRules.tools.templatePlaceholder')"
        class="w-56"
      >
        <ElOption
          v-for="tpl in templates"
          :key="String(tpl.key ?? tpl.id ?? tpl.name)"
          :label="String(tpl.name ?? tpl.label ?? tpl.key)"
          :value="String(tpl.key ?? tpl.id ?? tpl.name)"
        />
      </ElSelect>
      <ElButton
        :disabled="!selectedTemplateKey || !customerId || readOnly"
        :loading="applyingTemplate"
        @click="handleApplyTemplate"
      >
        {{ t('billingRules.tools.applyTemplate') }}
      </ElButton>
      <ElButton :disabled="!customerId" @click="loadChangeLog">
        {{ t('billingRules.tools.viewAuditLog') }}
      </ElButton>
    </div>

    <RuleBatchImport
      :customer-id="customerId"
      :can-import="!readOnly"
      @imported="emit('imported', $event)"
    />

    <ElDrawer v-model="auditVisible" :title="t('billingRules.tools.auditLogTitle')" size="480px">
      <ElTable v-loading="auditLoading" :data="changeLog" size="small" border>
        <ElTableColumn :label="t('billingRules.tools.changeType')" width="100">
          <template #default="{ row }">{{ row.change_type ?? row.changeType }}</template>
        </ElTableColumn>
        <ElTableColumn
          :label="t('billingRules.tools.summary')"
          min-width="160"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{
            row.change_summary ?? row.summary ?? row.changeSummary
          }}</template>
        </ElTableColumn>
        <ElTableColumn :label="t('billingRules.tools.operator')" width="100">
          <template #default="{ row }">{{ row.operator_name ?? row.operatorName }}</template>
        </ElTableColumn>
        <ElTableColumn :label="t('billingRules.tools.time')" width="160">
          <template #default="{ row }">{{ row.created_at ?? row.createdAt }}</template>
        </ElTableColumn>
      </ElTable>
    </ElDrawer>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import {
    copyRulesFromCustomer,
    listBuiltinRuleTemplates,
    listRuleChangeLog
  } from '@/api/billing/billingRulesApi'
  import { listCustomers } from '@/api/master-data/customersApi'
  import { useUserStore } from '@/store/modules/user'
  import RuleBatchImport from '@/components/business/customers/RuleBatchImport.vue'

  const props = defineProps<{
    customerId?: number | null
    readOnly?: boolean
  }>()

  const emit = defineEmits<{
    imported: [count: number]
    copied: [count: number]
  }>()

  const { t } = useI18n()
  const userStore = useUserStore()

  const customers = ref<Api.MasterData.CustomerRecord[]>([])
  const copySourceId = ref<number | undefined>()
  const copying = ref(false)
  const templates = ref<Record<string, unknown>[]>([])
  const selectedTemplateKey = ref<string | undefined>()
  const applyingTemplate = ref(false)
  const auditVisible = ref(false)
  const auditLoading = ref(false)
  const changeLog = ref<Api.Billing.RuleChangeLogEntry[]>([])

  const otherCustomers = computed(() => customers.value.filter((c) => c.id !== props.customerId))

  onMounted(async () => {
    try {
      customers.value = await listCustomers()
      templates.value = await listBuiltinRuleTemplates()
    } catch {
      // ignore
    }
  })

  watch(
    () => props.customerId,
    () => {
      copySourceId.value = undefined
      selectedTemplateKey.value = undefined
    }
  )

  async function handleCopyFrom() {
    if (!props.customerId || !copySourceId.value) return
    try {
      await ElMessageBox.confirm(t('billingRules.tools.copyConfirm'), t('common.tips'), {
        type: 'warning'
      })
    } catch {
      return
    }
    copying.value = true
    try {
      const operatorName = userStore.info?.userName ?? userStore.info?.username
      const res = await copyRulesFromCustomer(props.customerId, copySourceId.value, operatorName)
      ElMessage.success(t('billingRules.tools.copySuccess', { count: res.copiedCount }))
      emit('copied', res.copiedCount)
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : t('billingRules.tools.copyFailed'))
    } finally {
      copying.value = false
    }
  }

  async function handleApplyTemplate() {
    if (!props.customerId || !selectedTemplateKey.value) return
    const tpl = templates.value.find(
      (item) => String(item.key ?? item.id ?? item.name) === selectedTemplateKey.value
    )
    const rows = (tpl?.rules ?? tpl?.sampleRules ?? []) as Record<string, unknown>[]
    if (!rows.length) {
      ElMessage.warning(t('billingRules.tools.templateEmpty'))
      return
    }
    applyingTemplate.value = true
    try {
      const { confirmRuleImport } = await import('@/api/billing/billingRulesApi')
      const operatorName = userStore.info?.userName ?? userStore.info?.username
      const res = await confirmRuleImport({
        customerId: props.customerId,
        rows,
        operatorName
      })
      ElMessage.success(t('billingRules.tools.templateApplied', { count: res.importedCount }))
      emit('imported', res.importedCount)
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : t('billingRules.tools.templateFailed'))
    } finally {
      applyingTemplate.value = false
    }
  }

  async function loadChangeLog() {
    if (!props.customerId) return
    auditVisible.value = true
    auditLoading.value = true
    try {
      changeLog.value = await listRuleChangeLog(props.customerId)
    } catch {
      changeLog.value = []
    } finally {
      auditLoading.value = false
    }
  }
</script>
