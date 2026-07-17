<template>
  <ElDialog
    :model-value="visible"
    :title="$t('menus.masterData.customerBillingPolicy.allocationDialogTitle')"
    width="720px"
    destroy-on-close
    append-to-body
    class="logistics-allocation-dialog"
    @update:model-value="emit('update:visible', $event)"
  >
    <p class="logistics-allocation-dialog__desc">
      {{ $t('menus.masterData.customerBillingPolicy.allocationDialogDesc') }}
    </p>

    <div class="logistics-allocation-dialog__grid">
      <div class="logistics-allocation-dialog__field logistics-allocation-dialog__field--full">
        <label>{{ $t('menus.masterData.customerBillingPolicy.allocationMode') }}</label>
        <ElSelect v-model="draft.mode" class="w-full" :disabled="readOnly">
          <ElOption
            v-for="mode in modeOptions"
            :key="mode"
            :label="allocationModeLabel(mode, t)"
            :value="mode"
          />
        </ElSelect>
      </div>

      <template v-if="showCrossHospitalFields">
        <div class="logistics-allocation-dialog__field logistics-allocation-dialog__field--full">
          <label>{{ $t('menus.masterData.customerBillingPolicy.allocationGroupName') }}</label>
          <ElInput
            v-model="draft.groupName"
            :placeholder="$t('menus.masterData.customerBillingPolicy.allocationGroupNamePlaceholder')"
            :disabled="readOnly"
          />
        </div>

        <div class="logistics-allocation-dialog__field logistics-allocation-dialog__field--full">
          <label>{{ $t('menus.masterData.customerBillingPolicy.allocationMembers') }}</label>
          <ElSelect
            v-model="draft.memberCustomerIds"
            multiple
            filterable
            class="w-full"
            :disabled="readOnly"
            :placeholder="$t('menus.masterData.customerBillingPolicy.allocationMembersPlaceholder')"
          >
            <ElOption
              v-for="customer in customers"
              :key="customer.id"
              :label="customerLabel(customer)"
              :value="customer.id"
            />
          </ElSelect>
        </div>

        <div v-if="draft.mode === 'single_owner'" class="logistics-allocation-dialog__field">
          <label>{{ $t('menus.masterData.customerBillingPolicy.allocationOwnerHospital') }}</label>
          <ElSelect
            v-model="draft.singleOwnerCustomerId"
            filterable
            clearable
            class="w-full"
            :disabled="readOnly"
          >
            <ElOption
              v-for="id in draft.memberCustomerIds"
              :key="id"
              :label="customerName(id)"
              :value="id"
            />
          </ElSelect>
        </div>

        <div
          v-if="showShareRatios"
          class="logistics-allocation-dialog__field logistics-allocation-dialog__field--full"
        >
          <label>{{ $t('menus.masterData.customerBillingPolicy.splitRatio') }}</label>
          <div class="logistics-allocation-dialog__share-list">
            <div
              v-for="memberId in draft.memberCustomerIds"
              :key="memberId"
              class="logistics-allocation-dialog__share-row"
            >
              <span>{{ customerName(memberId) }}</span>
              <ElInputNumber
                :model-value="draft.shareRatios[memberId]"
                :min="0"
                :max="1"
                :step="0.05"
                :precision="4"
                :disabled="readOnly"
                @update:model-value="(val: number | undefined) => setShareRatio(memberId, val)"
              />
            </div>
          </div>
          <span class="logistics-allocation-dialog__hint">
            {{ $t('menus.masterData.customerBillingPolicy.splitRatioHint') }}
          </span>
        </div>

        <div class="logistics-allocation-dialog__field">
          <ElSwitch
            v-model="draft.mergeSameDay"
            :disabled="readOnly"
            :active-text="$t('menus.masterData.customerBillingPolicy.mergeSameDay')"
          />
        </div>
      </template>

      <template v-if="showDeptFields">
        <div class="logistics-allocation-dialog__field logistics-allocation-dialog__field--full">
          <label>{{ $t('menus.masterData.customerBillingPolicy.billingWeekdays') }}</label>
          <ElSelect v-model="draft.billingWeekdays" multiple class="w-full" :disabled="readOnly">
            <ElOption
              v-for="day in weekdayOptions"
              :key="day.value"
              :label="day.label"
              :value="day.value"
            />
          </ElSelect>
        </div>

        <div class="logistics-allocation-dialog__field logistics-allocation-dialog__field--full">
          <label>{{ $t('menus.masterData.customerBillingPolicy.excludeDepartments') }}</label>
          <ElInput
            v-model="draft.excludeDepartments"
            :placeholder="
              $t('menus.masterData.customerBillingPolicy.excludeDepartmentsPlaceholder')
            "
            :disabled="readOnly"
          />
        </div>
      </template>

      <div v-if="showCrossHospitalFields" class="logistics-allocation-dialog__field">
        <ElSwitch
          v-model="draft.syncToMembers"
          :disabled="readOnly"
          :active-text="$t('menus.masterData.customerBillingPolicy.allocationSyncToMembers')"
        />
      </div>

      <div class="logistics-allocation-dialog__field logistics-allocation-dialog__field--full">
        <label>{{ $t('menus.masterData.customerBillingPolicy.allocationPreview') }}</label>
        <ElAlert type="info" :closable="false" show-icon>
          {{ previewText }}
        </ElAlert>
      </div>
    </div>

    <template #footer>
      <ElButton @click="emit('update:visible', false)">{{ $t('common.cancel') }}</ElButton>
      <ElButton type="primary" :loading="saving" :disabled="readOnly" @click="handleConfirm">
        {{ $t('common.confirm') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
  import { computed, reactive, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import {
    allocationModeLabel,
    buildLogisticsAllocationSummary,
    createDefaultLogisticsAllocationConfig,
    isCrossHospitalMode,
    type LogisticsAllocationConfig,
    type LogisticsAllocationMode
  } from '@/utils/logisticsAllocationConfig'

  defineOptions({ name: 'LogisticsAllocationConfigDialog' })

  const props = defineProps<{
    visible: boolean
    config: LogisticsAllocationConfig
    customers: Api.MasterData.CustomerRecord[]
    customerNameMap: Record<number, string>
    readOnly?: boolean
    saving?: boolean
    currentCustomerId?: number | null
  }>()

  const emit = defineEmits<{
    'update:visible': [value: boolean]
    confirm: [config: LogisticsAllocationConfig]
  }>()

  const { t } = useI18n()
  const draft = reactive<LogisticsAllocationConfig>(createDefaultLogisticsAllocationConfig())

  const modeOptions: LogisticsAllocationMode[] = [
    'none',
    'dept_ratio',
    'equal',
    'proportional',
    'single_owner',
    'cross_hospital_merge'
  ]

  const weekdayOptions = [
    { value: 1, label: '周一' },
    { value: 2, label: '周二' },
    { value: 3, label: '周三' },
    { value: 4, label: '周四' },
    { value: 5, label: '周五' },
    { value: 6, label: '周六' },
    { value: 7, label: '周日' }
  ]

  const readOnly = computed(() => props.readOnly === true)

  const showCrossHospitalFields = computed(() => isCrossHospitalMode(draft.mode))

  const showDeptFields = computed(
    () => draft.mode === 'dept_ratio' || isCrossHospitalMode(draft.mode)
  )

  const showShareRatios = computed(
    () =>
      draft.mode === 'proportional' ||
      draft.mode === 'equal' ||
      draft.mode === 'cross_hospital_merge'
  )

  const previewText = computed(() =>
    buildLogisticsAllocationSummary(draft, props.customerNameMap, t)
  )

  watch(
    () => props.visible,
    (open) => {
      if (!open) return
      Object.assign(draft, JSON.parse(JSON.stringify(props.config)))
      if (
        props.currentCustomerId &&
        isCrossHospitalMode(draft.mode) &&
        !draft.memberCustomerIds.includes(props.currentCustomerId)
      ) {
        draft.memberCustomerIds.push(props.currentCustomerId)
      }
      if (!draft.groupName && draft.memberCustomerIds.length > 0) {
        draft.groupName = t('menus.masterData.customerBillingPolicy.allocationDefaultGroupName', {
          count: draft.memberCustomerIds.length
        })
      }
    }
  )

  watch(
    () => draft.mode,
    (mode) => {
      if (isCrossHospitalMode(mode) && props.currentCustomerId) {
        if (!draft.memberCustomerIds.includes(props.currentCustomerId)) {
          draft.memberCustomerIds.push(props.currentCustomerId)
        }
      }
      if (mode === 'single_owner' && !draft.singleOwnerCustomerId && draft.memberCustomerIds.length) {
        draft.singleOwnerCustomerId = draft.memberCustomerIds[0]
      }
    }
  )

  function customerLabel(customer: Api.MasterData.CustomerRecord) {
    return customer.canonical_name ?? customer.code ?? `#${customer.id}`
  }

  function customerName(id: number) {
    return props.customerNameMap[id] ?? `#${id}`
  }

  function setShareRatio(id: number, val?: number) {
    if (val == null) {
      delete draft.shareRatios[id]
    } else {
      draft.shareRatios[id] = val
    }
  }

  function validate(): boolean {
    if (isCrossHospitalMode(draft.mode) && draft.memberCustomerIds.length < 2) {
      ElMessage.warning(t('menus.masterData.customerBillingPolicy.allocationMembersMin'))
      return false
    }
    if (draft.mode === 'single_owner' && !draft.singleOwnerCustomerId) {
      ElMessage.warning(t('menus.masterData.customerBillingPolicy.allocationOwnerRequired'))
      return false
    }
    return true
  }

  function handleConfirm() {
    if (!validate()) return
    emit('confirm', JSON.parse(JSON.stringify(draft)) as LogisticsAllocationConfig)
  }
</script>

<style scoped>
  .logistics-allocation-dialog__desc {
    margin: 0 0 16px;
    font-size: 13px;
    line-height: 1.6;
    color: var(--el-text-color-secondary);
  }

  .logistics-allocation-dialog__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px 16px;
  }

  .logistics-allocation-dialog__field {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .logistics-allocation-dialog__field--full {
    grid-column: 1 / -1;
  }

  .logistics-allocation-dialog__field label {
    font-size: 13px;
    color: var(--el-text-color-regular);
  }

  .logistics-allocation-dialog__hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .logistics-allocation-dialog__share-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .logistics-allocation-dialog__share-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    font-size: 13px;
  }

  @media (max-width: 640px) {
    .logistics-allocation-dialog__grid {
      grid-template-columns: 1fr;
    }
  }
</style>
