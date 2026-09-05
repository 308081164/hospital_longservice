<template>
  <ElDialog
    :model-value="visible"
    title="规则试算"
    width="720px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="rule-simulator-dialog__selector">
      <span class="rule-simulator-dialog__label">选择客户</span>
      <ElSelect
        v-model="selectedCustomerId"
        filterable
        clearable
        placeholder="请选择要试算的客户"
        class="rule-simulator-dialog__customer-select"
      >
        <ElOption
          v-for="c in customerOptions"
          :key="c.id"
          :label="`${c.canonical_name}（${c.code}）`"
          :value="c.id"
        />
      </ElSelect>
    </div>

    <RuleSimulator
      v-if="selectedCustomerId"
      :key="selectedCustomerId"
      :customer-id="selectedCustomerId"
      :default-hospital-name="selectedCustomerName"
      :show-header="false"
    />
    <ElEmpty v-else description="请先选择客户" :image-size="80" />

    <template #footer>
      <ElButton @click="emit('update:visible', false)">关闭</ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue'
  import RuleSimulator from './RuleSimulator.vue'

  const props = defineProps<{
    visible: boolean
    customers: Api.MasterData.CustomerRecord[]
  }>()

  const emit = defineEmits<{
    (e: 'update:visible', value: boolean): void
  }>()

  const selectedCustomerId = ref<number | null>(null)

  const customerOptions = computed(() =>
    props.customers.filter((c): c is Api.MasterData.CustomerRecord & { id: number } => c.id != null)
  )

  const selectedCustomerName = computed(
    () => customerOptions.value.find((c) => c.id === selectedCustomerId.value)?.canonical_name ?? ''
  )
</script>

<style scoped>
  .rule-simulator-dialog__selector {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
  }

  .rule-simulator-dialog__label {
    font-size: 14px;
    color: var(--el-text-color-regular);
    white-space: nowrap;
  }

  .rule-simulator-dialog__customer-select {
    flex: 1;
  }
</style>
