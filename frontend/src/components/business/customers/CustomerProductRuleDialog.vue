<template>
  <ElDialog
    :model-value="visible"
    :title="dialogTitle"
    width="760px"
    destroy-on-close
    append-to-body
    class="customer-product-rule-dialog"
    @update:model-value="emit('update:visible', $event)"
  >
    <CustomerProductRuleForm
      :draft="draft"
      :products="products"
      :products-loading="productsLoading"
      :lock-product="lockProduct"
    />
    <template #footer>
      <ElButton @click="emit('update:visible', false)">{{ $t('common.cancel') }}</ElButton>
      <ElButton type="primary" :loading="saving" @click="handleSave">
        {{ $t('menus.masterData.customerProductRules.save') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import CustomerProductRuleForm from '@/components/business/customers/CustomerProductRuleForm.vue'
import type { CustomerProductRuleDraft } from '@/utils/customerProductRule'

defineOptions({ name: 'CustomerProductRuleDialog' })

const props = defineProps<{
  visible: boolean
  mode: 'create' | 'edit'
  draft: CustomerProductRuleDraft
  products: Api.MasterData.ProductRecord[]
  productsLoading?: boolean
  lockProduct?: boolean
  saving?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  save: []
}>()

const { t } = useI18n()

const dialogTitle = computed(() =>
  props.mode === 'create'
    ? t('menus.masterData.customerProductRules.dialogCreate')
    : t('menus.masterData.customerProductRules.dialogEdit'),
)

function handleSave() {
  emit('save')
}
</script>
