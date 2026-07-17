<template>
  <div class="logistics-import-page p-4">
    <ElCard shadow="never">
      <template #header>
        <div class="flex items-center justify-between gap-3 flex-wrap">
          <span class="text-lg font-semibold">{{ t('menus.billingConfig.logisticsImport') }}</span>
          <ElButton type="primary" :disabled="!selectedCustomerId" @click="openCreate">
            {{ t('menus.billingConfig.addImport') }}
          </ElButton>
        </div>
      </template>

      <ElForm :inline="true" class="mb-4 flex flex-wrap gap-y-2">
        <ElFormItem :label="t('menus.billingConfig.customer')">
          <ElSelect
            v-model="selectedCustomerId"
            filterable
            clearable
            class="w-72"
            :placeholder="t('menus.billingConfig.selectCustomer')"
            @change="loadImports"
          >
            <ElOption
              v-for="customer in customers"
              :key="customer.id"
              :label="customer.canonical_name"
              :value="customer.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.billingMonth')">
          <ElInput v-model="billingMonth" placeholder="2026-07" class="w-32" @change="loadImports" />
        </ElFormItem>
      </ElForm>

      <ElTable v-loading="loading" :data="imports" stripe border empty-text="—">
        <ElTableColumn prop="trip_date" :label="t('menus.billingConfig.tripDate')" width="120" />
        <ElTableColumn prop="route" :label="t('menus.billingConfig.route')" min-width="140" />
        <ElTableColumn prop="trip_count" :label="t('menus.billingConfig.tripCount')" width="90" align="center" />
        <ElTableColumn prop="fee_amount" :label="t('menus.billingConfig.feeAmount')" width="110" align="right">
          <template #default="{ row }">{{ row.fee_amount ?? '—' }}</template>
        </ElTableColumn>
        <ElTableColumn prop="notes" :label="t('menus.billingConfig.notes')" min-width="160" show-overflow-tooltip />
        <ElTableColumn :label="t('table.actions')" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton type="primary" link @click="openEdit(row)">{{ t('table.edit') }}</ElButton>
            <ElButton type="danger" link @click="handleDelete(row)">{{ t('table.delete') }}</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>

    <ElDialog v-model="dialogVisible" :title="editingId ? t('table.edit') : t('menus.billingConfig.addImport')" width="520px">
      <ElForm ref="formRef" :model="form" label-width="100px">
        <ElFormItem :label="t('menus.billingConfig.tripDate')" required>
          <ElDatePicker v-model="form.tripDate" type="date" value-format="YYYY-MM-DD" class="w-full" />
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.billingMonth')">
          <ElInput v-model="form.billingMonth" placeholder="2026-07" />
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.route')">
          <ElInput v-model="form.route" />
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.tripCount')">
          <ElInputNumber v-model="form.tripCount" :min="1" class="w-full" />
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.feeAmount')">
          <ElInputNumber v-model="form.feeAmount" :min="0" :precision="2" class="w-full" />
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.notes')">
          <ElInput v-model="form.notes" type="textarea" :rows="2" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">{{ t('table.cancel') }}</ElButton>
        <ElButton type="primary" :loading="saving" @click="handleSave">{{ t('table.confirm') }}</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { listCustomers } from '@/api/master-data/customersApi'
import {
  createLogisticsImport,
  deleteLogisticsImport,
  listLogisticsImports,
  updateLogisticsImport,
  type LogisticsImportRecord,
} from '@/api/billing-config/logisticsApi'

const { t } = useI18n()
const customers = ref<Api.MasterData.CustomerRecord[]>([])
const selectedCustomerId = ref<number | null>(null)
const billingMonth = ref('')
const imports = ref<LogisticsImportRecord[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = ref({
  tripDate: '',
  billingMonth: '',
  route: '',
  tripCount: 1,
  feeAmount: undefined as number | undefined,
  notes: '',
})

onMounted(async () => {
  customers.value = await listCustomers()
})

async function loadImports() {
  if (!selectedCustomerId.value) {
    imports.value = []
    return
  }
  loading.value = true
  try {
    imports.value = await listLogisticsImports(
      selectedCustomerId.value,
      billingMonth.value || undefined,
    )
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = {
    tripDate: '',
    billingMonth: billingMonth.value,
    route: '',
    tripCount: 1,
    feeAmount: undefined,
    notes: '',
  }
  dialogVisible.value = true
}

function openEdit(row: LogisticsImportRecord) {
  editingId.value = row.id
  form.value = {
    tripDate: row.trip_date,
    billingMonth: row.billing_month ?? '',
    route: row.route ?? '',
    tripCount: row.trip_count,
    feeAmount: row.fee_amount ?? undefined,
    notes: row.notes ?? '',
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!selectedCustomerId.value || !form.value.tripDate) {
    ElMessage.warning('请选择客户并填写发货日期')
    return
  }
  saving.value = true
  try {
    const payload = {
      tripDate: form.value.tripDate,
      billingMonth: form.value.billingMonth || null,
      route: form.value.route || null,
      tripCount: form.value.tripCount,
      feeAmount: form.value.feeAmount ?? null,
      notes: form.value.notes || null,
    }
    if (editingId.value) {
      await updateLogisticsImport(selectedCustomerId.value, editingId.value, payload)
    } else {
      await createLogisticsImport(selectedCustomerId.value, payload)
    }
    dialogVisible.value = false
    await loadImports()
    ElMessage.success('保存成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: LogisticsImportRecord) {
  if (!selectedCustomerId.value) return
  await ElMessageBox.confirm('确认删除该物流导入记录？', '提示', { type: 'warning' })
  await deleteLogisticsImport(selectedCustomerId.value, row.id)
  await loadImports()
  ElMessage.success('已删除')
}
</script>
