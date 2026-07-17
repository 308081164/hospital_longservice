<template>
  <div class="logistics-card-page p-4">
    <ElCard shadow="never">
      <template #header>
        <div class="flex items-center justify-between gap-3 flex-wrap">
          <span class="text-lg font-semibold">{{ t('menus.billingConfig.logisticsCard') }}</span>
          <ElButton type="primary" @click="openCreate">{{ t('menus.billingConfig.addCard') }}</ElButton>
        </div>
      </template>

      <ElForm :inline="true" class="mb-4">
        <ElFormItem :label="t('menus.billingConfig.customer')">
          <ElSelect
            v-model="filterCustomerId"
            filterable
            clearable
            class="w-72"
            :placeholder="t('menus.billingConfig.selectCustomer')"
            @change="loadCards"
          >
            <ElOption
              v-for="customer in customers"
              :key="customer.id"
              :label="customer.canonical_name"
              :value="customer.id"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>

      <ElTable v-loading="loading" :data="cards" stripe border>
        <ElTableColumn prop="name" :label="t('menus.billingConfig.cardName')" min-width="140" />
        <ElTableColumn prop="customer_id" :label="t('menus.billingConfig.customer')" min-width="180">
          <template #default="{ row }">{{ customerName(row.customer_id) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="balance" :label="t('menus.billingConfig.balance')" width="120" align="right" />
        <ElTableColumn prop="initial_balance" :label="t('menus.billingConfig.initialBalance')" width="120" align="right" />
        <ElTableColumn prop="is_active" :label="t('menus.masterData.customerFilters.status')" width="90" align="center">
          <template #default="{ row }">
            <ElTag :type="row.is_active ? 'success' : 'info'" size="small">
              {{ row.is_active ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn :label="t('table.actions')" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton type="primary" link @click="openRecharge(row)">{{ t('menus.billingConfig.recharge') }}</ElButton>
            <ElButton type="warning" link @click="openDeduct(row)">{{ t('menus.billingConfig.deduct') }}</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>

    <ElDialog v-model="createVisible" :title="t('menus.billingConfig.addCard')" width="480px">
      <ElForm :model="createForm" label-width="100px">
        <ElFormItem :label="t('menus.billingConfig.customer')" required>
          <ElSelect v-model="createForm.customerId" filterable class="w-full">
            <ElOption
              v-for="customer in customers"
              :key="customer.id"
              :label="customer.canonical_name"
              :value="customer.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.cardName')" required>
          <ElInput v-model="createForm.name" />
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.initialBalance')">
          <ElInputNumber v-model="createForm.initialBalance" :min="0" :precision="2" class="w-full" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="createVisible = false">{{ t('table.cancel') }}</ElButton>
        <ElButton type="primary" :loading="saving" @click="handleCreate">{{ t('table.confirm') }}</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="txVisible" :title="txMode === 'recharge' ? t('menus.billingConfig.recharge') : t('menus.billingConfig.deduct')" width="420px">
      <ElForm :model="txForm" label-width="80px">
        <ElFormItem :label="t('menus.billingConfig.amount')" required>
          <ElInputNumber v-model="txForm.amount" :min="0.01" :precision="2" class="w-full" />
        </ElFormItem>
        <ElFormItem :label="t('menus.billingConfig.notes')">
          <ElInput v-model="txForm.remark" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="txVisible = false">{{ t('table.cancel') }}</ElButton>
        <ElButton type="primary" :loading="saving" @click="handleTransaction">{{ t('table.confirm') }}</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { listCustomers } from '@/api/master-data/customersApi'
import {
  createLogisticsCard,
  deductLogisticsCard,
  listLogisticsCards,
  rechargeLogisticsCard,
  type LogisticsCardRecord,
} from '@/api/billing-config/logisticsApi'

const { t } = useI18n()
const customers = ref<Api.MasterData.CustomerRecord[]>([])
const filterCustomerId = ref<number | null>(null)
const cards = ref<LogisticsCardRecord[]>([])
const loading = ref(false)
const saving = ref(false)
const createVisible = ref(false)
const txVisible = ref(false)
const txMode = ref<'recharge' | 'deduct'>('recharge')
const activeCardId = ref<number | null>(null)
const createForm = ref({ customerId: null as number | null, name: '默认物流卡', initialBalance: 0 })
const txForm = ref({ amount: 0, remark: '' })

onMounted(async () => {
  customers.value = await listCustomers()
  await loadCards()
})

function customerName(id: number) {
  return customers.value.find((c) => c.id === id)?.canonical_name ?? `#${id}`
}

async function loadCards() {
  loading.value = true
  try {
    cards.value = await listLogisticsCards(filterCustomerId.value ?? undefined)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createForm.value = { customerId: filterCustomerId.value, name: '默认物流卡', initialBalance: 0 }
  createVisible.value = true
}

async function handleCreate() {
  if (!createForm.value.customerId || !createForm.value.name) {
    ElMessage.warning('请填写客户与卡名称')
    return
  }
  saving.value = true
  try {
    await createLogisticsCard({
      customerId: createForm.value.customerId,
      name: createForm.value.name,
      initialBalance: createForm.value.initialBalance,
    })
    createVisible.value = false
    await loadCards()
    ElMessage.success('创建成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建失败')
  } finally {
    saving.value = false
  }
}

function openRecharge(row: LogisticsCardRecord) {
  txMode.value = 'recharge'
  activeCardId.value = row.id
  txForm.value = { amount: 0, remark: '' }
  txVisible.value = true
}

function openDeduct(row: LogisticsCardRecord) {
  txMode.value = 'deduct'
  activeCardId.value = row.id
  txForm.value = { amount: 0, remark: '' }
  txVisible.value = true
}

async function handleTransaction() {
  if (!activeCardId.value || !txForm.value.amount) return
  saving.value = true
  try {
    if (txMode.value === 'recharge') {
      await rechargeLogisticsCard(activeCardId.value, txForm.value.amount, txForm.value.remark)
    } else {
      await deductLogisticsCard(activeCardId.value, txForm.value.amount, txForm.value.remark)
    }
    txVisible.value = false
    await loadCards()
    ElMessage.success('操作成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    saving.value = false
  }
}
</script>
