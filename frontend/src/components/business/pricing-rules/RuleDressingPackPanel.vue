<template>
  <div class="dressing-pack-panel">
    <RuleSectionBlock
      title="棉球 / 纱布纸塑袋"
      subtitle="类型为敷料包(纸塑袋)时，按规格尺寸取固定单价（skipPackaging，不计纸塑袋加收）"
    >
      <ElAlert
        type="info"
        :closable="false"
        show-icon
        class="mb-4"
        title="触发条件"
        description="产品类型含「敷料包」且包装材料为纸塑袋；尺寸由包名或规格字段解析（如 15cm、20cm）。未命中特色规则时回退到此标准价。"
      />
      <ElTable :data="cottonRows" size="small" class="mb-3">
        <ElTableColumn label="规格 (cm)" width="140">
          <template #default="{ row }">
            <ElInputNumber
              v-model="row.size"
              :min="1"
              :precision="0"
              :step="1"
              controls-position="right"
              class="w-full"
              @change="syncCottonFromRows"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn label="单价 (元/包)" min-width="160">
          <template #default="{ row }">
            <ElInputNumber
              v-model="row.price"
              :min="0"
              :precision="2"
              :step="0.5"
              controls-position="right"
              class="w-full"
              @change="syncCottonFromRows"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="80" align="center">
          <template #default="{ $index }">
            <ElButton type="danger" link :disabled="cottonRows.length <= 1" @click="removeCottonRow($index)">
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <ElButton size="small" @click="addCottonRow">添加规格</ElButton>
    </RuleSectionBlock>

    <RuleSectionBlock
      class="mt-6"
      title="敷料包无纺布 (W码)"
      subtitle="类型为敷料包且包装材料为无纺布时，按 W 码尺寸分档计价"
    >
      <RuleFieldGrid :columns="3">
        <RuleNumberField
          v-model="nonWoven.below90"
          label="W &lt; 90"
          :quick-steps="[5, 10]"
          tooltip="宽度小于 90cm 的敷料包单价"
          @change="emitChange"
        />
        <RuleNumberField
          v-model="nonWoven.equals90"
          label="W = 90"
          :quick-steps="[5, 10]"
          @change="emitChange"
        />
        <RuleNumberField
          v-model="nonWoven.range12to15"
          label="W 120–150"
          :quick-steps="[5, 10]"
          tooltip="宽度在 120cm 至 150cm 区间"
          @change="emitChange"
        />
      </RuleFieldGrid>
    </RuleSectionBlock>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import RuleSectionBlock from './RuleSectionBlock.vue'
import RuleFieldGrid from './RuleFieldGrid.vue'
import RuleNumberField from './RuleNumberField.vue'

defineOptions({ name: 'RuleDressingPackPanel' })

const props = defineProps<{
  modelValue: Api.Hospital.DressingPackConfig
}>()

const emit = defineEmits<{
  change: []
}>()

interface CottonRow {
  size: number
  price: number
}

const cottonRows = ref<CottonRow[]>([])
const nonWoven = ref<Api.Hospital.DressingPackNonWovenConfig>({
  below90: 25,
  equals90: 30,
  range12to15: 35,
})

function cottonObjectToRows(obj: Record<string, number>): CottonRow[] {
  const entries = Object.entries(obj ?? {})
    .map(([k, v]) => ({ size: Number(k), price: Number(v) }))
    .filter((r) => r.size > 0 && Number.isFinite(r.price))
    .sort((a, b) => a.size - b.size)
  if (!entries.length) {
    return [{ size: 15, price: 2.5 }, { size: 20, price: 4 }]
  }
  return entries
}

function rowsToCottonObject(rows: CottonRow[]): Record<string, number> {
  const result: Record<string, number> = {}
  for (const row of rows) {
    if (row.size > 0 && row.price >= 0) {
      result[String(row.size)] = row.price
    }
  }
  return result
}

function syncFromModel() {
  cottonRows.value = cottonObjectToRows(props.modelValue.cottonPaperPlastic ?? {})
  nonWoven.value = {
    below90: props.modelValue.nonWoven?.below90 ?? 25,
    equals90: props.modelValue.nonWoven?.equals90 ?? 30,
    range12to15: props.modelValue.nonWoven?.range12to15 ?? 35,
  }
}

function syncCottonFromRows() {
  props.modelValue.cottonPaperPlastic = rowsToCottonObject(cottonRows.value)
  emitChange()
}

function addCottonRow() {
  const maxSize = cottonRows.value.reduce((m, r) => Math.max(m, r.size), 0)
  cottonRows.value.push({ size: maxSize > 0 ? maxSize + 5 : 15, price: 0 })
  syncCottonFromRows()
}

function removeCottonRow(index: number) {
  cottonRows.value.splice(index, 1)
  syncCottonFromRows()
}

function emitChange() {
  props.modelValue.nonWoven = { ...nonWoven.value }
  emit('change')
}

watch(() => props.modelValue, syncFromModel, { immediate: true, deep: true })
</script>
