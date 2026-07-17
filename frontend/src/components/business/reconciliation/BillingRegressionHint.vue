<template>
  <ElAlert
    v-if="visible"
    type="warning"
    show-icon
    :closable="true"
    class="mb-3"
    :title="t('reconciliation.regression.title')"
    @close="dismissed = true"
  >
    <template #default>
      <p class="text-xs leading-relaxed">{{ t('reconciliation.regression.desc') }}</p>
      <ul class="mt-2 text-xs list-disc pl-4 space-y-1">
        <li>{{ t('reconciliation.regression.check1') }}</li>
        <li>{{ t('reconciliation.regression.check2') }}</li>
        <li>{{ t('reconciliation.regression.check3') }}</li>
      </ul>
    </template>
  </ElAlert>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue'
  import { useI18n } from 'vue-i18n'

  const props = defineProps<{
    billingEnabled?: boolean
  }>()

  const { t } = useI18n()
  const dismissed = ref(false)

  const visible = computed(() => props.billingEnabled === false && !dismissed.value)
</script>
