<template>
  <div class="p-6">
    <ElCard shadow="never">
      <div class="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <div class="mb-1 flex flex-wrap items-center gap-2">
            <RouterLink
              v-if="isFixedCustomer"
              :to="{ name: 'MasterDataCustomers' }"
              class="text-sm text-primary"
            >
              ← {{ t('menus.billingConfig.backToCustomers') }}
            </RouterLink>
            <BillingRoleBadge />
          </div>
          <h2 class="text-lg font-semibold">{{ t('menus.billingConfig.deptPhysician') }}</h2>
          <p v-if="customerLabel" class="text-sm font-medium text-gray-700">
            {{ t('menus.billingConfig.forCustomer', { name: customerLabel }) }}
          </p>
          <p class="text-sm text-gray-500">{{ t('menus.billingConfig.deptPhysicianDesc') }}</p>
        </div>
        <ElSelect
          v-if="!isFixedCustomer"
          v-model="selectedCustomerId"
          filterable
          :placeholder="t('menus.billingConfig.selectCustomer')"
          style="width: 260px"
          @change="handleCustomerChange"
        >
          <ElOption v-for="c in customers" :key="c.id" :label="c.canonical_name" :value="c.id" />
        </ElSelect>
      </div>

      <ElTabs v-model="activeTab">
        <ElTabPane :label="t('menus.billingConfig.departments')" name="departments">
          <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
            <div class="flex flex-wrap items-center gap-2">
              <ElInput
                v-model="deptSearch.keyword"
                clearable
                :placeholder="t('menus.billingConfig.searchKeyword')"
                style="width: 220px"
                @keyup.enter="loadDepartments"
                @clear="loadDepartments"
              />
              <ElSelect
                v-model="deptSearch.status"
                clearable
                :placeholder="t('menus.billingConfig.statusFilter')"
                style="width: 120px"
                @change="loadDepartments"
              >
                <ElOption :label="t('menus.billingConfig.statusAll')" value="" />
                <ElOption
                  :label="t('menus.masterData.customerFilters.statusActive')"
                  value="active"
                />
                <ElOption
                  :label="t('menus.masterData.customerFilters.statusInactive')"
                  value="inactive"
                />
              </ElSelect>
              <ElButton @click="loadDepartments">{{ t('table.searchBar.search') }}</ElButton>
            </div>
            <ElButton
              type="primary"
              :disabled="!selectedCustomerId || isReadOnlyConfig"
              @click="openDeptCreate"
            >
              {{ t('menus.billingConfig.addDepartment') }}
            </ElButton>
          </div>
          <ElTable v-loading="loadingDepartments" :data="departments" border stripe size="small">
            <ElTableColumn
              prop="department_name"
              :label="t('menus.billingConfig.departmentName')"
              min-width="140"
            >
              <template #default="{ row }">{{ deptName(row) }}</template>
            </ElTableColumn>
            <ElTableColumn prop="code" :label="t('menus.billingConfig.entryCode')" width="100" />
            <ElTableColumn
              prop="usage_count"
              :label="t('menus.billingConfig.usageCount')"
              width="90"
              align="center"
            >
              <template #default="{ row }">{{ row.usage_count ?? row.usageCount ?? 0 }}</template>
            </ElTableColumn>
            <ElTableColumn
              prop="notes"
              :label="t('menus.billingConfig.notes')"
              min-width="120"
              show-overflow-tooltip
            />
            <ElTableColumn :label="t('menus.billingConfig.toggleActive')" width="90" align="center">
              <template #default="{ row }">
                <ElSwitch
                  :model-value="isRowActive(row)"
                  :disabled="isReadOnlyConfig"
                  @change="(val: boolean) => handleDeptActiveToggle(row, val)"
                />
              </template>
            </ElTableColumn>
            <ElTableColumn :label="t('table.actions')" width="140" align="center" fixed="right">
              <template #default="{ row }">
                <ElButton
                  link
                  type="primary"
                  :disabled="isReadOnlyConfig"
                  @click="openDeptEdit(row)"
                >
                  {{ t('menus.masterData.customerProductRules.edit') }}
                </ElButton>
                <ElButton
                  link
                  type="danger"
                  :disabled="isReadOnlyConfig"
                  @click="handleDeptDelete(row)"
                >
                  {{ t('menus.masterData.customerProductRules.delete') }}
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <ElTabPane :label="t('menus.billingConfig.physicians')" name="physicians">
          <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
            <div class="flex flex-wrap items-center gap-2">
              <ElInput
                v-model="physicianSearch.keyword"
                clearable
                :placeholder="t('menus.billingConfig.searchKeyword')"
                style="width: 220px"
                @keyup.enter="loadPhysicians"
                @clear="loadPhysicians"
              />
              <ElSelect
                v-model="physicianSearch.status"
                clearable
                :placeholder="t('menus.billingConfig.statusFilter')"
                style="width: 120px"
                @change="loadPhysicians"
              >
                <ElOption :label="t('menus.billingConfig.statusAll')" value="" />
                <ElOption
                  :label="t('menus.masterData.customerFilters.statusActive')"
                  value="active"
                />
                <ElOption
                  :label="t('menus.masterData.customerFilters.statusInactive')"
                  value="inactive"
                />
              </ElSelect>
              <ElButton @click="loadPhysicians">{{ t('table.searchBar.search') }}</ElButton>
            </div>
            <ElButton
              type="primary"
              :disabled="!selectedCustomerId || isReadOnlyConfig"
              @click="openPhysicianCreate"
            >
              {{ t('menus.billingConfig.addPhysician') }}
            </ElButton>
          </div>
          <ElTable v-loading="loadingPhysicians" :data="physicians" border stripe size="small">
            <ElTableColumn
              prop="physician_name"
              :label="t('menus.billingConfig.physicianName')"
              min-width="120"
            >
              <template #default="{ row }">{{ physicianName(row) }}</template>
            </ElTableColumn>
            <ElTableColumn
              prop="department_name"
              :label="t('menus.billingConfig.departmentName')"
              min-width="120"
            >
              <template #default="{ row }">{{
                row.department_name ?? row.departmentName ?? '—'
              }}</template>
            </ElTableColumn>
            <ElTableColumn prop="code" :label="t('menus.billingConfig.entryCode')" width="100" />
            <ElTableColumn
              prop="usage_count"
              :label="t('menus.billingConfig.usageCount')"
              width="90"
              align="center"
            >
              <template #default="{ row }">{{ row.usage_count ?? row.usageCount ?? 0 }}</template>
            </ElTableColumn>
            <ElTableColumn
              prop="notes"
              :label="t('menus.billingConfig.notes')"
              min-width="120"
              show-overflow-tooltip
            />
            <ElTableColumn :label="t('menus.billingConfig.toggleActive')" width="90" align="center">
              <template #default="{ row }">
                <ElSwitch
                  :model-value="isRowActive(row)"
                  :disabled="isReadOnlyConfig"
                  @change="(val: boolean) => handlePhysicianActiveToggle(row, val)"
                />
              </template>
            </ElTableColumn>
            <ElTableColumn :label="t('table.actions')" width="140" align="center" fixed="right">
              <template #default="{ row }">
                <ElButton
                  link
                  type="primary"
                  :disabled="isReadOnlyConfig"
                  @click="openPhysicianEdit(row)"
                >
                  {{ t('menus.masterData.customerProductRules.edit') }}
                </ElButton>
                <ElButton
                  link
                  type="danger"
                  :disabled="isReadOnlyConfig"
                  @click="handlePhysicianDelete(row)"
                >
                  {{ t('menus.masterData.customerProductRules.delete') }}
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>
      </ElTabs>
    </ElCard>

    <ElDialog
      v-model="deptDialogVisible"
      :title="
        deptEditingId
          ? t('menus.billingConfig.editDepartment')
          : t('menus.billingConfig.addDepartment')
      "
      width="480px"
    >
      <ElForm label-width="90px">
        <ElFormItem :label="t('menus.billingConfig.departmentName')" required>
          <ElInput v-model="deptForm.departmentName" />
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.entryCode')">
          <ElInput v-model="deptForm.code" />
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.notes')">
          <ElInput v-model="deptForm.notes" type="textarea" :rows="2" />
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.toggleActive')">
          <ElSwitch v-model="deptForm.isActive" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="deptDialogVisible = false">{{ t('common.cancel') }}</ElButton>
        <ElButton type="primary" :loading="saving" @click="handleDeptSave">{{
          t('common.save')
        }}</ElButton>
      </template>
    </ElDialog>

    <ElDialog
      v-model="physicianDialogVisible"
      :title="
        physicianEditingId
          ? t('menus.billingConfig.editPhysician')
          : t('menus.billingConfig.addPhysician')
      "
      width="480px"
    >
      <ElForm label-width="90px">
        <ElFormItem :label="t('menus.billingConfig.physicianName')" required>
          <ElInput v-model="physicianForm.physicianName" />
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.departmentName')">
          <ElSelect v-model="physicianForm.departmentEntryId" filterable clearable class="w-full">
            <ElOption v-for="d in allDepartments" :key="d.id" :label="deptName(d)" :value="d.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.entryCode')">
          <ElInput v-model="physicianForm.code" />
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.notes')">
          <ElInput v-model="physicianForm.notes" type="textarea" :rows="2" />
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.toggleActive')">
          <ElSwitch v-model="physicianForm.isActive" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="physicianDialogVisible = false">{{ t('common.cancel') }}</ElButton>
        <ElButton type="primary" :loading="saving" @click="handlePhysicianSave">{{
          t('common.save')
        }}</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue'
  import { useRoute } from 'vue-router'
  import { useI18n } from 'vue-i18n'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { getCustomer, listCustomers } from '@/api/master-data/customersApi'
  import {
    createDepartmentEntry,
    createPhysicianEntry,
    deleteDepartmentEntry,
    deletePhysicianEntry,
    deptName,
    listDepartmentEntries,
    listPhysicianEntries,
    physicianName,
    updateDepartmentEntry,
    updatePhysicianEntry,
    type DepartmentEntryRecord,
    type PhysicianEntryRecord
  } from '@/api/billing-config/deptPhysicianApi'
  import BillingRoleBadge from '@/components/business/BillingRoleBadge.vue'
  import { useBillingPermission } from '@/composables/useBillingPermission'

  defineOptions({ name: 'BillingConfigDeptPhysician' })

  const { t } = useI18n()
  const route = useRoute()
  const { isReadOnlyConfig } = useBillingPermission()

  const customers = ref<Api.MasterData.CustomerRecord[]>([])
  const selectedCustomerId = ref<number | undefined>()
  const customerLabel = ref('')
  const activeTab = ref<'departments' | 'physicians'>('departments')
  const departments = ref<DepartmentEntryRecord[]>([])
  const allDepartments = ref<DepartmentEntryRecord[]>([])
  const physicians = ref<PhysicianEntryRecord[]>([])
  const loadingDepartments = ref(false)
  const loadingPhysicians = ref(false)
  const saving = ref(false)

  const deptSearch = reactive({ keyword: '', status: '' as '' | 'active' | 'inactive' })
  const physicianSearch = reactive({ keyword: '', status: '' as '' | 'active' | 'inactive' })

  const deptDialogVisible = ref(false)
  const deptEditingId = ref<number | null>(null)
  const deptForm = ref({ departmentName: '', code: '', notes: '', isActive: true })

  const physicianDialogVisible = ref(false)
  const physicianEditingId = ref<number | null>(null)
  const physicianForm = ref({
    physicianName: '',
    departmentEntryId: undefined as number | undefined,
    code: '',
    notes: '',
    isActive: true
  })

  const isFixedCustomer = computed(() => Boolean(route.params.customerId))

  function resolveCustomerIdFromRoute(): number | undefined {
    const paramId = route.params.customerId
    if (paramId) return Number(paramId)
    const queryId = route.query.customerId
    if (queryId) return Number(queryId)
    return undefined
  }

  function statusToQuery(status: '' | 'active' | 'inactive') {
    if (status === 'active') return true
    if (status === 'inactive') return false
    return undefined
  }

  function isRowActive(row: DepartmentEntryRecord | PhysicianEntryRecord) {
    return row.is_active !== false && row.isActive !== false
  }

  async function loadCustomerLabel(customerId: number) {
    try {
      const customer = await getCustomer(customerId)
      customerLabel.value = customer.canonical_name ?? customer.code ?? String(customerId)
    } catch {
      const cached = customers.value.find((c) => c.id === customerId)
      customerLabel.value = cached?.canonical_name ?? cached?.code ?? String(customerId)
    }
  }

  async function loadAllDepartmentsForSelect() {
    if (!selectedCustomerId.value) {
      allDepartments.value = []
      return
    }
    allDepartments.value = await listDepartmentEntries(selectedCustomerId.value)
  }

  async function loadDepartments() {
    if (!selectedCustomerId.value) return
    loadingDepartments.value = true
    try {
      departments.value = await listDepartmentEntries(selectedCustomerId.value, {
        keyword: deptSearch.keyword.trim() || undefined,
        isActive: statusToQuery(deptSearch.status)
      })
    } finally {
      loadingDepartments.value = false
    }
  }

  async function loadPhysicians() {
    if (!selectedCustomerId.value) return
    loadingPhysicians.value = true
    try {
      physicians.value = await listPhysicianEntries(selectedCustomerId.value, {
        keyword: physicianSearch.keyword.trim() || undefined,
        isActive: statusToQuery(physicianSearch.status)
      })
    } finally {
      loadingPhysicians.value = false
    }
  }

  async function loadAll() {
    if (!selectedCustomerId.value) return
    await Promise.all([loadDepartments(), loadPhysicians(), loadAllDepartmentsForSelect()])
  }

  async function applyCustomerId(customerId: number | undefined) {
    selectedCustomerId.value = customerId
    if (!customerId) {
      customerLabel.value = ''
      departments.value = []
      physicians.value = []
      allDepartments.value = []
      return
    }
    await loadCustomerLabel(customerId)
    await loadAll()
  }

  function handleCustomerChange() {
    void applyCustomerId(selectedCustomerId.value)
  }

  onMounted(async () => {
    customers.value = await listCustomers()
    const initialId = resolveCustomerIdFromRoute()
    if (initialId && !Number.isNaN(initialId)) {
      await applyCustomerId(initialId)
    }
  })

  watch(
    () => [route.params.customerId, route.query.customerId],
    () => {
      const nextId = resolveCustomerIdFromRoute()
      if (nextId && !Number.isNaN(nextId) && nextId !== selectedCustomerId.value) {
        void applyCustomerId(nextId)
      }
    }
  )

  function openDeptCreate() {
    deptEditingId.value = null
    deptForm.value = { departmentName: '', code: '', notes: '', isActive: true }
    deptDialogVisible.value = true
  }

  function openDeptEdit(row: DepartmentEntryRecord) {
    deptEditingId.value = row.id
    deptForm.value = {
      departmentName: deptName(row),
      code: row.code ?? '',
      notes: row.notes ?? '',
      isActive: isRowActive(row)
    }
    deptDialogVisible.value = true
  }

  async function handleDeptSave() {
    if (!selectedCustomerId.value || !deptForm.value.departmentName.trim()) {
      ElMessage.warning(t('menus.billingConfig.deptNameRequired'))
      return
    }
    saving.value = true
    try {
      const payload = {
        departmentName: deptForm.value.departmentName.trim(),
        code: deptForm.value.code || undefined,
        notes: deptForm.value.notes || undefined,
        isActive: deptForm.value.isActive
      }
      if (deptEditingId.value) {
        await updateDepartmentEntry(selectedCustomerId.value, deptEditingId.value, payload)
      } else {
        await createDepartmentEntry(selectedCustomerId.value, payload)
      }
      deptDialogVisible.value = false
      await loadAll()
      ElMessage.success(t('menus.billingConfig.saveSuccess'))
    } catch (e: unknown) {
      ElMessage.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      saving.value = false
    }
  }

  async function handleDeptActiveToggle(row: DepartmentEntryRecord, active: boolean) {
    if (!selectedCustomerId.value || isReadOnlyConfig.value) return
    try {
      await updateDepartmentEntry(selectedCustomerId.value, row.id, {
        departmentName: deptName(row),
        code: row.code ?? undefined,
        notes: row.notes ?? undefined,
        isActive: active
      })
      await loadDepartments()
    } catch (e: unknown) {
      ElMessage.error(e instanceof Error ? e.message : '保存失败')
    }
  }

  async function handleDeptDelete(row: DepartmentEntryRecord) {
    if (!selectedCustomerId.value || isReadOnlyConfig.value) return
    try {
      await ElMessageBox.confirm(t('menus.billingConfig.deleteConfirm'), t('common.tips'), {
        type: 'warning'
      })
      await deleteDepartmentEntry(selectedCustomerId.value, row.id)
      await loadAll()
      ElMessage.success(t('menus.billingConfig.deleteSuccess'))
    } catch {
      // cancelled or failed
    }
  }

  function openPhysicianCreate() {
    physicianEditingId.value = null
    physicianForm.value = {
      physicianName: '',
      departmentEntryId: undefined,
      code: '',
      notes: '',
      isActive: true
    }
    physicianDialogVisible.value = true
  }

  function openPhysicianEdit(row: PhysicianEntryRecord) {
    physicianEditingId.value = row.id
    physicianForm.value = {
      physicianName: physicianName(row),
      departmentEntryId: row.department_entry_id ?? row.departmentEntryId ?? undefined,
      code: row.code ?? '',
      notes: row.notes ?? '',
      isActive: isRowActive(row)
    }
    physicianDialogVisible.value = true
  }

  async function handlePhysicianSave() {
    if (!selectedCustomerId.value || !physicianForm.value.physicianName.trim()) {
      ElMessage.warning(t('menus.billingConfig.physicianNameRequired'))
      return
    }
    saving.value = true
    try {
      const payload = {
        physicianName: physicianForm.value.physicianName.trim(),
        departmentEntryId: physicianForm.value.departmentEntryId,
        code: physicianForm.value.code || undefined,
        notes: physicianForm.value.notes || undefined,
        isActive: physicianForm.value.isActive
      }
      if (physicianEditingId.value) {
        await updatePhysicianEntry(selectedCustomerId.value, physicianEditingId.value, payload)
      } else {
        await createPhysicianEntry(selectedCustomerId.value, payload)
      }
      physicianDialogVisible.value = false
      await loadAll()
      ElMessage.success(t('menus.billingConfig.saveSuccess'))
    } catch (e: unknown) {
      ElMessage.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      saving.value = false
    }
  }

  async function handlePhysicianActiveToggle(row: PhysicianEntryRecord, active: boolean) {
    if (!selectedCustomerId.value || isReadOnlyConfig.value) return
    try {
      await updatePhysicianEntry(selectedCustomerId.value, row.id, {
        physicianName: physicianName(row),
        departmentEntryId: row.department_entry_id ?? row.departmentEntryId ?? undefined,
        code: row.code ?? undefined,
        notes: row.notes ?? undefined,
        isActive: active
      })
      await loadPhysicians()
    } catch (e: unknown) {
      ElMessage.error(e instanceof Error ? e.message : '保存失败')
    }
  }

  async function handlePhysicianDelete(row: PhysicianEntryRecord) {
    if (!selectedCustomerId.value || isReadOnlyConfig.value) return
    try {
      await ElMessageBox.confirm(t('menus.billingConfig.deleteConfirm'), t('common.tips'), {
        type: 'warning'
      })
      await deletePhysicianEntry(selectedCustomerId.value, row.id)
      await loadAll()
      ElMessage.success(t('menus.billingConfig.deleteSuccess'))
    } catch {
      // cancelled or failed
    }
  }
</script>
