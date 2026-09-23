<template>
  <div class="rule-price-table-editor">
    <div class="rule-price-table-editor__toolbar">
      <span class="rule-price-table-editor__hint">
        {{ hint }}
      </span>
      <div class="flex flex-wrap gap-2">
        <ElButton size="small" @click="fillFromTiers">从阶梯价生成</ElButton>
        <ElButton type="primary" link @click="addRow">+ 添加行</ElButton>
      </div>
    </div>
    <ElTable :data="rows" border size="small" max-height="360" class="rule-price-table-editor__table">
      <ElTableColumn label="件数" width="120">
        <template #default="{ row }">
          <ElInputNumber
            v-model="row.count"
            :min="1"
            :precision="0"
            controls-position="right"
            class="w-full"
            @change="syncToModel"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="总价(元)" min-width="160">
        <template #default="{ row }">
          <ElInputNumber
            v-model="row.price"
            :min="0"
            :precision="2"
            :step="1"
            controls-position="right"
            class="w-full"
            @change="syncToModel"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="72" align="center" fixed="right">
        <template #default="{ $index }">
          <ElButton type="danger" link @click="removeRow($index)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

defineOptions({ name: 'RulePriceTableEditor' })

const props = withDefaults(defineProps<{
  modelValue?: Api.Hospital.LowTempPriceTable
  tierPrices?: Api.Hospital.TierPriceConfig[]
  hint?: string
}>(), {
  hint: '2 件及以上优先按此精确价表匹配；未命中时回退阶梯价 + 余数单价',
})

const emit = defineEmits<{
  'update:modelValue': [value: Api.Hospital.LowTempPriceTable | undefined]
  change: []
}>()

interface PriceRow {
  count: number
  price: number
}

const rows = ref<PriceRow[]>([])

function tableToRows(table?: Api.Hospital.LowTempPriceTable): PriceRow[] {
  if (!table) return []
  return Object.entries(table)
    .map(([count, price]) => ({ count: Number(count), price: Number(price) }))
    .filter((row) => row.count > 0 && Number.isFinite(row.price))
    .sort((a, b) => a.count - b.count)
}

function rowsToTable(items: PriceRow[]): Api.Hospital.LowTempPriceTable | undefined {
  const result: Api.Hospital.LowTempPriceTable = {}
  for (const row of items) {
    if (row.count > 0 && row.price >= 0) {
      result[String(row.count)] = row.price
    }
  }
  return Object.keys(result).length ? result : undefined
}

function syncFromModel() {
  rows.value = tableToRows(props.modelValue)
}

function syncToModel() {
  const table = rowsToTable(rows.value)
  emit('update:modelValue', table)
  emit('change')
}

function addRow() {
  const maxCount = rows.value.reduce((max, row) => Math.max(max, row.count), 0)
  rows.value.push({ count: maxCount > 0 ? maxCount + 1 : 1, price: 0 })
  syncToModel()
}

function removeRow(index: number) {
  rows.value.splice(index, 1)
  syncToModel()
}

function fillFromTiers() {
  const tiers = [...(props.tierPrices ?? [])].sort((a, b) => a.count - b.count)
  if (!tiers.length) return
  const generated: PriceRow[] = []
  for (let count = 1; count <= 40; count += 1) {
    let price = tiers[0]?.price ?? 0
    for (const tier of tiers) {
      if (count >= tier.count) {
        price = tier.price
      }
    }
    generated.push({ count, price })
  }
  rows.value = generated
  syncToModel()
}

watch(() => props.modelValue, syncFromModel, { immediate: true, deep: true })
</script>

<style scoped>
.rule-price-table-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.rule-price-table-editor__hint {
  flex: 1;
  min-width: 200px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
