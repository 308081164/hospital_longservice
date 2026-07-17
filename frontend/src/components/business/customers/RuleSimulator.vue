<template>
  <div class="rule-simulator">
    <div class="rule-simulator__header">
      <span class="rule-simulator__title">规则试算器</span>
      <ElTag v-if="customerId" size="small" type="info">客户 ID: {{ customerId }}</ElTag>
    </div>
    <p class="rule-simulator__hint">输入样例行，预览规则命中链与计价结果（CFG-04）</p>

    <ElForm label-width="100px" size="small" class="rule-simulator__form">
      <ElFormItem label="医院名称">
        <ElInput v-model="hospitalName" placeholder="与对账导入一致" />
      </ElFormItem>
      <ElFormItem label="包名">
        <ElInput v-model="sampleRow.packName" placeholder="器械包名称" />
      </ElFormItem>
      <ElFormItem label="类型">
        <ElInput v-model="sampleRow.type" placeholder="如 高温灭菌" />
      </ElFormItem>
      <ElFormItem label="包装材料">
        <ElInput v-model="sampleRow.packageMaterial" placeholder="如 纸塑袋" />
      </ElFormItem>
      <ElFormItem label="器械数">
        <ElInputNumber v-model="sampleRow.instrumentCount" :min="0" />
      </ElFormItem>
      <ElFormItem label="包数">
        <ElInputNumber v-model="sampleRow.packCount" :min="1" />
      </ElFormItem>
      <ElFormItem label="单价">
        <ElInputNumber v-model="sampleRow.unitPrice" :min="0" :precision="2" :step="0.5" />
      </ElFormItem>
      <ElFormItem label="总价">
        <ElInputNumber v-model="sampleRow.totalPrice" :min="0" :precision="2" :step="1" />
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" :loading="loading" :disabled="!customerId" @click="runSimulate">
          试算
        </ElButton>
      </ElFormItem>
    </ElForm>

    <div v-if="result" class="rule-simulator__result">
      <ElDescriptions :column="2" border size="small">
        <ElDescriptionsItem label="状态">{{ result.status }}</ElDescriptionsItem>
        <ElDescriptionsItem label="命中规则">{{ result.pricing_rule }}</ElDescriptionsItem>
        <ElDescriptionsItem label="期望单价">{{ result.expected_unit_price }}</ElDescriptionsItem>
        <ElDescriptionsItem label="校正总价">{{ result.corrected_total_price }}</ElDescriptionsItem>
        <ElDescriptionsItem label="差异">{{ result.difference }}</ElDescriptionsItem>
        <ElDescriptionsItem label="规则 ID">{{ result.matched_rule_id ?? '—' }}</ElDescriptionsItem>
      </ElDescriptions>

      <div v-if="result.notes?.length" class="rule-simulator__notes">
        <div class="rule-simulator__section-title">说明</div>
        <ul>
          <li v-for="(note, idx) in result.notes" :key="idx">{{ note }}</li>
        </ul>
      </div>

      <div v-if="result.policy_traces?.length" class="rule-simulator__notes">
        <div class="rule-simulator__section-title">策略轨迹</div>
        <ul>
          <li v-for="(trace, idx) in result.policy_traces" :key="idx">{{ trace }}</li>
        </ul>
      </div>

      <div v-if="result.match_chain?.length" class="rule-simulator__notes">
        <div class="rule-simulator__section-title">命中链</div>
        <ElTimeline>
          <ElTimelineItem
            v-for="(step, idx) in result.match_chain"
            :key="idx"
            :timestamp="String(step.step)"
          >
            {{ JSON.stringify(step) }}
          </ElTimelineItem>
        </ElTimeline>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { simulateBillingRule } from '@/api/billing/billingRulesApi'

const props = defineProps<{
  customerId?: number | null
  defaultHospitalName?: string
}>()

const hospitalName = ref(props.defaultHospitalName ?? '')
const loading = ref(false)
const result = ref<Api.Billing.RuleSimulateResult | null>(null)

const sampleRow = reactive({
  packName: '',
  type: '',
  packageMaterial: '',
  instrumentCount: 1,
  packCount: 1,
  unitPrice: 0,
  totalPrice: 0,
})

async function runSimulate() {
  if (!props.customerId) {
    ElMessage.warning('请先保存客户后再试算')
    return
  }
  if (!hospitalName.value.trim()) {
    ElMessage.warning('请填写医院名称')
    return
  }
  loading.value = true
  try {
    result.value = await simulateBillingRule({
      customerId: props.customerId,
      hospitalName: hospitalName.value.trim(),
      sampleRow: { ...sampleRow },
    })
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '试算失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.rule-simulator {
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  padding: 16px;
  margin-top: 12px;
}

.rule-simulator__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.rule-simulator__title {
  font-weight: 600;
}

.rule-simulator__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 12px;
}

.rule-simulator__result {
  margin-top: 16px;
}

.rule-simulator__section-title {
  font-size: 13px;
  font-weight: 600;
  margin: 12px 0 6px;
}

.rule-simulator__notes ul {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
</style>
