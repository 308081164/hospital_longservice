<template>
  <div class="rule-special-rules-panel">
    <ElAlert
      type="info"
      :closable="false"
      show-icon
      class="mb-4"
      title="与「小件识别」的区别"
      description="此处为 specialRules 特色规则（固定价/折算/加收），带优先级与 skipPackaging 等；小件识别 Tab 仅维护 needle 全局关键词折算。"
    />

    <ElTabs v-model="activeTab">
      <ElTabPane label="固定单价" name="fixed">
        <div class="panel-toolbar">
          <ElButton type="primary" link @click="addFixed">+ 添加固定价规则</ElButton>
        </div>
        <ElTable :data="modelValue.fixedPrices" border size="small">
          <ElTableColumn label="名称" min-width="140">
            <template #default="{ row }">
              <ElInput v-model="row.name" @input="emitChange" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="关键词" min-width="180">
            <template #default="{ row }">
              <ElInput
                :model-value="keywordsText(row.keywords)"
                placeholder="逗号分隔"
                @update:model-value="(v) => setKeywords(row, v)"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn label="单价" width="110">
            <template #default="{ row }">
              <ElInputNumber v-model="row.price" :min="0" :precision="2" controls-position="right" class="w-full" @change="emitChange" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="优先级" width="90">
            <template #default="{ row }">
              <ElInputNumber v-model="row.priority" :min="0" :precision="0" controls-position="right" class="w-full" @change="emitChange" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="温区" width="90">
            <template #default="{ row }">
              <ElSelect v-model="row.temperature" clearable placeholder="任意" @change="emitChange">
                <ElOption label="HT" value="HT" />
                <ElOption label="LT" value="LT" />
                <ElOption label="ANY" value="ANY" />
              </ElSelect>
            </template>
          </ElTableColumn>
          <ElTableColumn label="免包材" width="72" align="center">
            <template #default="{ row }">
              <ElSwitch v-model="row.skipPackaging" @change="emitChange" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="免折扣" width="72" align="center">
            <template #default="{ row }">
              <ElSwitch v-model="row.skipDiscount" @change="emitChange" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="72" align="center" fixed="right">
            <template #default="{ $index }">
              <ElButton type="danger" link @click="removeFixed($index)">删除</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElTabPane>

      <ElTabPane label="特色折算" name="fold">
        <div class="panel-toolbar">
          <ElButton type="primary" link @click="addFold">+ 添加折算规则</ElButton>
        </div>
        <ElTable :data="modelValue.foldRules" border size="small">
          <ElTableColumn label="名称" min-width="130">
            <template #default="{ row }">
              <ElInput v-model="row.name" @input="emitChange" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="关键词" min-width="160">
            <template #default="{ row }">
              <ElInput
                :model-value="keywordsText(row.keywords)"
                placeholder="逗号分隔"
                @update:model-value="(v) => setKeywords(row, v)"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn label="优先级" width="80">
            <template #default="{ row }">
              <ElInputNumber v-model="row.priority" :min="0" :precision="0" controls-position="right" class="w-full" @change="emitChange" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="触发≤" width="80">
            <template #default="{ row }">
              <ElInputNumber v-model="row.threshold" :min="0" :precision="0" controls-position="right" class="w-full" @change="emitChange" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="折算比" width="80">
            <template #default="{ row }">
              <ElInputNumber v-model="row.foldRatio" :min="1" :precision="0" controls-position="right" class="w-full" @change="emitChange" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="单价" width="90">
            <template #default="{ row }">
              <ElInputNumber v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" class="w-full" @change="emitChange" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="免包材" width="72" align="center">
            <template #default="{ row }">
              <ElSwitch v-model="row.skipPackaging" @change="emitChange" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="72" align="center" fixed="right">
            <template #default="{ $index }">
              <ElButton type="danger" link @click="removeFold($index)">删除</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElTabPane>

      <ElTabPane label="特殊加收" name="extra">
        <div class="panel-toolbar">
          <ElButton type="primary" link @click="addExtra">+ 添加加收规则</ElButton>
        </div>
        <ElTable :data="modelValue.extraFees" border size="small">
          <ElTableColumn label="名称" min-width="140">
            <template #default="{ row }">
              <ElInput v-model="row.name" @input="emitChange" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="关键词" min-width="180">
            <template #default="{ row }">
              <ElInput
                :model-value="keywordsText(row.keywords)"
                placeholder="逗号分隔"
                @update:model-value="(v) => setKeywords(row, v)"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn label="加收(元)" width="110">
            <template #default="{ row }">
              <ElInputNumber v-model="row.fee" :min="0" :precision="2" controls-position="right" class="w-full" @change="emitChange" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="优先级" width="90">
            <template #default="{ row }">
              <ElInputNumber v-model="row.priority" :min="0" :precision="0" controls-position="right" class="w-full" @change="emitChange" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="72" align="center" fixed="right">
            <template #default="{ $index }">
              <ElButton type="danger" link @click="removeExtra($index)">删除</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElTabPane>
    </ElTabs>

    <p
      v-if="(modelValue.priceMultipliers?.length ?? 0) > 0 || (modelValue.zeroPriceOverrides?.length ?? 0) > 0"
      class="mt-4 text-xs text-gray-500"
    >
      另有 priceMultipliers / zeroPriceOverrides 共
      {{ (modelValue.priceMultipliers?.length ?? 0) + (modelValue.zeroPriceOverrides?.length ?? 0) }}
      条（多由客户商品策略编译注入），请在 JSON Tab 查看或编辑。
    </p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineOptions({ name: 'RuleSpecialRulesPanel' })

const props = defineProps<{
  modelValue: Api.Hospital.SpecialRulesConfig
}>()

const emit = defineEmits<{ change: [] }>()

const activeTab = ref('fixed')

function emitChange() {
  emit('change')
}

function keywordsText(keywords?: string[]) {
  return (keywords ?? []).join(', ')
}

function setKeywords(row: { keywords: string[] }, value: string) {
  row.keywords = String(value).split(/[,，]/).map((s) => s.trim()).filter(Boolean)
  emitChange()
}

function addFixed() {
  props.modelValue.fixedPrices.push({
    name: '',
    keywords: [],
    price: 0,
    priority: 50,
    skipPackaging: false,
    skipDiscount: false,
  })
  emitChange()
}

function removeFixed(index: number) {
  props.modelValue.fixedPrices.splice(index, 1)
  emitChange()
}

function addFold() {
  props.modelValue.foldRules.push({
    name: '',
    keywords: [],
    priority: 50,
    threshold: 5,
    foldRatio: 5,
    skipPackaging: false,
  })
  emitChange()
}

function removeFold(index: number) {
  props.modelValue.foldRules.splice(index, 1)
  emitChange()
}

function addExtra() {
  props.modelValue.extraFees.push({
    name: '',
    keywords: [],
    fee: 0,
    priority: 50,
  })
  emitChange()
}

function removeExtra(index: number) {
  props.modelValue.extraFees.splice(index, 1)
  emitChange()
}
</script>

<style scoped>
.panel-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}
</style>
