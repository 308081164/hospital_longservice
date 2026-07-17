<template>
  <div class="p-6">
    <ElCard shadow="never">
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-lg font-semibold">花名册管理</h2>
          <p class="text-sm text-gray-500">维护医生姓名 → 科室映射，支持 Excel 导入</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <ElSelect
            v-model="selectedCustomerId"
            filterable
            placeholder="选择客户"
            style="width: 240px"
            @change="loadEntries"
          >
            <ElOption
              v-for="c in customers"
              :key="c.id"
              :label="c.canonicalName"
              :value="c.id"
            />
          </ElSelect>
          <ElUpload
            :auto-upload="false"
            accept=".xlsx,.xls"
            :show-file-list="false"
            :disabled="!selectedCustomerId"
            @change="handleImport"
          >
            <ElButton :disabled="!selectedCustomerId">Excel 导入</ElButton>
          </ElUpload>
          <ElButton type="primary" :disabled="!selectedCustomerId" @click="openCreate">
            新增
          </ElButton>
        </div>
      </div>

      <ElTable v-loading="loading" :data="entries" border stripe size="small">
        <ElTableColumn prop="doctorName" label="医生姓名" min-width="120" />
        <ElTableColumn prop="department" label="科室" min-width="120" />
        <ElTableColumn prop="surgicalRoom" label="手术室" min-width="100" />
        <ElTableColumn prop="notes" label="备注" min-width="140" show-overflow-tooltip />
        <ElTableColumn label="状态" width="80" align="center">
          <template #default="{ row }">
            <ElTag :type="row.isActive !== false ? 'success' : 'info'" size="small">
              {{ row.isActive !== false ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
            <ElButton link type="danger" @click="handleDelete(row)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>

    <ElDialog v-model="dialogVisible" :title="editingId ? '编辑花名册' : '新增花名册'" width="480px">
      <ElForm label-width="90px">
        <ElFormItem label="医生姓名" required>
          <ElInput v-model="form.doctorName" />
        </ElFormItem>
        <ElFormItem label="科室" required>
          <ElInput v-model="form.department" />
        </ElFormItem>
        <ElFormItem label="手术室">
          <ElInput v-model="form.surgicalRoom" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="form.notes" type="textarea" :rows="2" />
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
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { listCustomers } from '@/api/master-data/customersApi'
import {
  createRosterEntry,
  deleteRosterEntry,
  importRosterExcel,
  listRosterEntries,
  updateRosterEntry,
  type RosterEntryRecord,
} from '@/api/billing-config/rosterApi'

defineOptions({ name: 'BillingConfigRoster' })

const customers = ref<Api.MasterData.CustomerRecord[]>([])
const selectedCustomerId = ref<number | undefined>()
const entries = ref<RosterEntryRecord[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = ref({
  doctorName: '',
  department: '',
  surgicalRoom: '',
  notes: '',
})

onMounted(async () => {
  customers.value = await listCustomers()
})

async function loadEntries() {
  if (!selectedCustomerId.value) return
  loading.value = true
  try {
    entries.value = await listRosterEntries(selectedCustomerId.value)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = { doctorName: '', department: '', surgicalRoom: '', notes: '' }
  dialogVisible.value = true
}

function openEdit(row: RosterEntryRecord) {
  editingId.value = row.id
  form.value = {
    doctorName: row.doctorName,
    department: row.department,
    surgicalRoom: row.surgicalRoom ?? '',
    notes: row.notes ?? '',
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!selectedCustomerId.value || !form.value.doctorName || !form.value.department) {
    ElMessage.warning('请填写医生姓名和科室')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateRosterEntry(selectedCustomerId.value, editingId.value, form.value)
    } else {
      await createRosterEntry(selectedCustomerId.value, form.value)
    }
    dialogVisible.value = false
    await loadEntries()
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: RosterEntryRecord) {
  if (!selectedCustomerId.value) return
  await ElMessageBox.confirm(`删除 ${row.doctorName}？`, '确认')
  await deleteRosterEntry(selectedCustomerId.value, row.id)
  await loadEntries()
  ElMessage.success('已删除')
}

async function handleImport(uploadFile: UploadFile) {
  if (!selectedCustomerId.value || !uploadFile.raw) return
  const replace = await ElMessageBox.confirm(
    '是否覆盖现有花名册？选「确定」覆盖，选「取消」追加。',
    '导入模式',
    { distinguishCancelAndClose: true },
  ).then(() => true).catch(() => false)

  const result = await importRosterExcel(selectedCustomerId.value, uploadFile.raw, replace)
  ElMessage.success(`导入 ${result.importedCount} 条，跳过 ${result.skippedCount} 条`)
  await loadEntries()
}
</script>
