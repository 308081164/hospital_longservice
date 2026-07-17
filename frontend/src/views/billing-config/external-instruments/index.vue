<template>
  <div class="p-6">
    <ElCard shadow="never">
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-lg font-semibold">外来器械维护</h2>
          <p class="text-sm text-gray-500">按包类别号维护价格目录，与常规账单分离</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <ElSelect
            v-model="selectedCustomerId"
            filterable
            placeholder="选择客户"
            style="width: 240px"
            @change="loadCatalog"
          >
            <ElOption
              v-for="c in customers"
              :key="c.id"
              :label="c.canonicalName"
              :value="c.id"
            />
          </ElSelect>
          <ElButton type="primary" :disabled="!selectedCustomerId" @click="openCreate">
            新增目录价
          </ElButton>
        </div>
      </div>

      <ElTable v-loading="loading" :data="catalog" border stripe size="small">
        <ElTableColumn prop="categoryNo" label="包类别号" min-width="120" />
        <ElTableColumn prop="packName" label="包名" min-width="160" show-overflow-tooltip />
        <ElTableColumn prop="department" label="科室" min-width="100" />
        <ElTableColumn prop="unitPrice" label="单价" width="100" align="right">
          <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="packageMaterial" label="包装材料" min-width="100" />
        <ElTableColumn label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <ElButton link type="danger" @click="handleDelete(row)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>

    <ElDialog v-model="dialogVisible" title="新增外来器械目录" width="520px">
      <ElForm label-width="100px">
        <ElFormItem label="包类别号" required>
          <ElInput v-model="form.categoryNo" placeholder="计价主键，需唯一" />
        </ElFormItem>
        <ElFormItem label="包名" required>
          <ElInput v-model="form.packName" />
        </ElFormItem>
        <ElFormItem label="单价" required>
          <ElInputNumber v-model="form.unitPrice" :min="0" :precision="2" class="w-full" />
        </ElFormItem>
        <ElFormItem label="科室">
          <ElInput v-model="form.department" />
        </ElFormItem>
        <ElFormItem label="包装材料">
          <ElInput v-model="form.packageMaterial" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="handleSave">保存</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCustomers } from '@/api/master-data/customersApi'
import {
  createExternalInstrumentCatalog,
  deleteExternalInstrument,
  listExternalInstrumentCatalog,
  type ExternalInstrumentRecord,
} from '@/api/billing-config/externalInstrumentsApi'

defineOptions({ name: 'BillingConfigExternalInstruments' })

const customers = ref<Api.MasterData.CustomerRecord[]>([])
const selectedCustomerId = ref<number | undefined>()
const catalog = ref<ExternalInstrumentRecord[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const form = ref({
  categoryNo: '',
  packName: '',
  unitPrice: 0,
  department: '',
  packageMaterial: '',
})

onMounted(async () => {
  customers.value = await listCustomers()
})

function formatMoney(value?: number) {
  if (value == null) return '—'
  return value.toFixed(2)
}

async function loadCatalog() {
  if (!selectedCustomerId.value) return
  loading.value = true
  try {
    catalog.value = await listExternalInstrumentCatalog(selectedCustomerId.value)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = { categoryNo: '', packName: '', unitPrice: 0, department: '', packageMaterial: '' }
  dialogVisible.value = true
}

async function handleSave() {
  if (!selectedCustomerId.value || !form.value.categoryNo || !form.value.packName) {
    ElMessage.warning('请填写包类别号、包名和单价')
    return
  }
  saving.value = true
  try {
    await createExternalInstrumentCatalog(selectedCustomerId.value, form.value)
    dialogVisible.value = false
    await loadCatalog()
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: ExternalInstrumentRecord) {
  await ElMessageBox.confirm(`删除 ${row.categoryNo}？`, '确认')
  await deleteExternalInstrument(row.id)
  await loadCatalog()
  ElMessage.success('已删除')
}
</script>
