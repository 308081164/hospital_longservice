<template>
  <ElTag v-if="showBadge" :type="tagType" size="small" effect="plain" class="billing-role-badge">
    {{ label }}
  </ElTag>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { useBillingPermission } from '@/composables/useBillingPermission'

  withDefaults(
    defineProps<{
      showBadge?: boolean
    }>(),
    { showBadge: true }
  )

  const { t } = useI18n()
  const { persona } = useBillingPermission()

  const label = computed(() => t(`billing.permission.persona.${persona.value}`))

  const tagType = computed(() => {
    if (persona.value === 'configurator') return 'success'
    if (persona.value === 'auditor') return 'warning'
    return 'info'
  })
</script>
