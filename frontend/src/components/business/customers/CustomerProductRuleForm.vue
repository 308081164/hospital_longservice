<template>
  <div class="customer-product-rule-form">
    <RuleFieldGrid :columns="2">
      <div class="customer-product-rule-form__field">
        <label class="customer-product-rule-form__label">
          {{ $t('menus.masterData.customerProductRules.ruleType') }}
        </label>
        <ElSelect v-model="draft.ruleType" class="w-full" @change="handleRuleTypeChange">
          <ElOption :label="$t('menus.masterData.customerProductRules.fixedPrice')" value="FIXED_PRICE" />
          <ElOption :label="$t('menus.masterData.customerProductRules.pricePerInstrument')" value="PRICE_PER_INSTRUMENT" />
          <ElOption :label="$t('menus.masterData.customerProductRules.multiplier')" value="MULTIPLIER" />
          <ElOption :label="$t('menus.masterData.customerProductRules.foldRule')" value="FOLD" />
          <ElOption :label="$t('menus.masterData.customerProductRules.extraFee')" value="EXTRA_FEE" />
        </ElSelect>
      </div>
      <div class="customer-product-rule-form__field">
        <label class="customer-product-rule-form__label">
          {{ $t('menus.masterData.customerProductRules.product') }}
          <span v-if="!productRequired" class="customer-product-rule-form__optional">
            {{ $t('menus.masterData.customerProductRules.optional') }}
          </span>
        </label>
        <ElSelect
          v-model="productKeywordValue"
          filterable
          allow-create
          default-first-option
          :clearable="!lockProduct || !productRequired"
          class="w-full"
          :placeholder="$t('menus.masterData.customerProductRules.productPlaceholder')"
          :loading="productsLoading"
          :disabled="lockProduct && productRequired"
          @change="handleProductKeywordChange"
          @clear="handleProductKeywordClear"
        >
          <ElOption v-for="p in displayProducts" :key="p.id" :label="p.name" :value="p.name" />
        </ElSelect>
        <p class="customer-product-rule-form__hint">
          {{ $t('menus.masterData.customerProductRules.productHint') }}
        </p>
      </div>
    </RuleFieldGrid>

    <RuleSectionBlock
      :title="$t('menus.masterData.customerProductRules.pricingParams')"
      :subtitle="$t('menus.masterData.customerProductRules.pricingParamsHint')"
      class="customer-product-rule-form__section"
    >
      <RuleFieldGrid :columns="2">
        <RuleNumberField
          v-if="(draft.ruleType === 'FIXED_PRICE' || draft.ruleType === 'PRICE_PER_INSTRUMENT') && draft.matchMode !== 'any_price'"
          v-model="draft.price"
          :label="$t('menus.masterData.customerProductRules.priceYuan')"
          :quick-steps="[0.5, 1, 5, 10]"
        />
        <div
          v-if="(draft.ruleType === 'FIXED_PRICE' || draft.ruleType === 'PRICE_PER_INSTRUMENT') && draft.matchMode === 'any_price'"
          class="customer-product-rule-form__field customer-product-rule-form__field--full"
        >
          <label class="customer-product-rule-form__label">
            {{ $t('menus.masterData.customerProductRules.acceptedPrices') }}
          </label>
          <div class="accepted-prices">
            <div v-for="(_, idx) in draft.acceptedPrices" :key="idx" class="accepted-prices__row">
              <ElInputNumber
                v-model="draft.acceptedPrices[idx]"
                :min="0.01"
                :step="0.5"
                :precision="2"
                class="accepted-prices__input"
              />
              <ElButton type="danger" link @click="removeAcceptedPrice(idx)">
                {{ $t('menus.masterData.customerProductRules.removePrice') }}
              </ElButton>
            </div>
            <ElButton size="small" @click="addAcceptedPrice">
              {{ $t('menus.masterData.customerProductRules.addPrice') }}
            </ElButton>
          </div>
          <p class="customer-product-rule-form__hint">{{ $t('menus.masterData.customerProductRules.acceptedPricesHint') }}</p>
        </div>
        <div
          v-if="draft.ruleType === 'FIXED_PRICE' || draft.ruleType === 'PRICE_PER_INSTRUMENT'"
          class="customer-product-rule-form__field customer-product-rule-form__field--full"
        >
          <label class="customer-product-rule-form__label">
            {{ $t('menus.masterData.customerProductRules.billingMode') }}
          </label>
          <ElRadioGroup v-model="draft.billingMode" class="billing-mode-group" @change="handleBillingModeChange">
            <ElRadio value="PER_PACK">{{ $t('menus.masterData.customerProductRules.billingModePerPack') }}</ElRadio>
            <ElRadio value="PER_INSTRUMENT">{{ $t('menus.masterData.customerProductRules.billingModePerInstrument') }}</ElRadio>
            <ElRadio value="PACK_NAME_SUFFIX">{{ $t('menus.masterData.customerProductRules.billingModePackNameSuffix') }}</ElRadio>
          </ElRadioGroup>
          <p v-if="draft.billingMode === 'PACK_NAME_SUFFIX'" class="customer-product-rule-form__hint">
            {{ $t('menus.masterData.customerProductRules.billingModePackNameSuffixHint') }}
          </p>
        </div>
        <div
          v-if="draft.ruleType === 'FIXED_PRICE' || draft.ruleType === 'PRICE_PER_INSTRUMENT'"
          class="customer-product-rule-form__field"
        >
          <label class="customer-product-rule-form__label">
            {{ $t('menus.masterData.customerProductRules.matchMode') }}
          </label>
          <ElSelect v-model="draft.matchMode" class="w-full" @change="handleMatchModeChange">
            <ElOption :label="$t('menus.masterData.customerProductRules.matchModeFirst')" value="first" />
            <ElOption :label="$t('menus.masterData.customerProductRules.matchModeAnyPrice')" value="any_price" />
          </ElSelect>
        </div>
        <RuleNumberField
          v-else-if="draft.ruleType === 'MULTIPLIER'"
          v-model="draft.multiplier"
          :label="$t('menus.masterData.customerProductRules.multiplierLabel')"
          kind="decimal"
          :min="0.01"
          :max="99"
          :step="0.1"
          :precision="2"
          :quick-steps="[0.1, 0.5, 1]"
        />
        <template v-else-if="draft.ruleType === 'FOLD'">
          <RuleNumberField
            v-model="draft.threshold"
            :label="$t('menus.masterData.customerProductRules.foldThreshold')"
            kind="integer"
            :min="1"
            :step="1"
            :tooltip="$t('menus.masterData.customerProductRules.foldThresholdHint')"
          />
          <RuleNumberField
            v-model="draft.foldRatio"
            :label="$t('menus.masterData.customerProductRules.foldRatio')"
            kind="decimal"
            :min="1"
            :max="999"
            :step="1"
            :precision="0"
            :tooltip="$t('menus.masterData.customerProductRules.foldRatioHint')"
          />
        </template>
        <RuleNumberField
          v-else-if="draft.ruleType === 'EXTRA_FEE' || draft.ruleType === 'ADD_FEE'"
          v-model="draft.fee"
          :label="$t('menus.masterData.customerProductRules.extraFeeAmount')"
          :quick-steps="[1, 5, 10, 20]"
        />
      </RuleFieldGrid>
    </RuleSectionBlock>

    <RuleSectionBlock
      :title="$t('menus.masterData.customerProductRules.matchConditions')"
      :subtitle="$t('menus.masterData.customerProductRules.matchConditionsHint')"
      class="customer-product-rule-form__section"
    >
      <RuleKeywordField
        v-model="draft.keywords"
        :label="$t('menus.masterData.customerProductRules.keywords')"
        :hint="$t('menus.masterData.customerProductRules.keywordsHint')"
        size="large"
        :rows="2"
        :max-rows="6"
      />
      <RuleKeywordField
        v-model="draft.excludeKeywords"
        class="mt-4"
        :label="$t('menus.masterData.customerProductRules.excludeKeywords')"
        :hint="$t('menus.masterData.customerProductRules.excludeKeywordsHint')"
        size="large"
        :rows="2"
        :max-rows="4"
      />
      <RuleKeywordField
        v-model="draft.materials"
        class="mt-4"
        :label="$t('menus.masterData.customerProductRules.materials')"
        :hint="$t('menus.masterData.customerProductRules.materialsHint')"
        size="large"
        :rows="2"
        :max-rows="4"
      />
      <div class="customer-product-rule-form__field mt-4">
        <label class="customer-product-rule-form__label">
          {{ $t('menus.masterData.customerProductRules.temperature') }}
        </label>
        <ElSelect v-model="draft.temperature" clearable class="w-full" :placeholder="$t('menus.masterData.customerProductRules.temperatureAny')">
          <ElOption :label="$t('menus.masterData.customerProductRules.temperatureAny')" value="" />
          <ElOption :label="$t('menus.masterData.customerProductRules.temperatureHt')" value="HT" />
          <ElOption :label="$t('menus.masterData.customerProductRules.temperatureLt')" value="LT" />
          <ElOption :label="$t('menus.masterData.customerProductRules.temperatureAny')" value="ANY" />
        </ElSelect>
        <p class="customer-product-rule-form__hint">{{ $t('menus.masterData.customerProductRules.temperatureHint') }}</p>
      </div>
      <div class="customer-product-rule-form__bag-size-block mt-4">
        <div class="customer-product-rule-form__bag-size-header">
          <div>
            <label class="customer-product-rule-form__label">
              {{ $t('menus.masterData.customerProductRules.bagSizeConstraint') }}
            </label>
            <p class="customer-product-rule-form__hint">
              {{ $t('menus.masterData.customerProductRules.bagSizeSectionHint') }}
            </p>
          </div>
          <ElSwitch v-model="bagSizeConstraintEnabled" @change="handleBagSizeConstraintChange" />
        </div>
        <RuleFieldGrid v-if="bagSizeConstraintEnabled" :columns="2" class="mt-3">
          <RuleNumberField
            v-model="draft.bagSizeEquals"
            :label="$t('menus.masterData.customerProductRules.bagSizeEquals')"
            :tooltip="$t('menus.masterData.customerProductRules.bagSizeEqualsTooltip')"
            kind="integer"
            optional
            :min="1"
            :step="1"
          />
          <RuleNumberField
            v-model="draft.maxBagSizeExclusive"
            :label="$t('menus.masterData.customerProductRules.bagSizeLess')"
            :tooltip="$t('menus.masterData.customerProductRules.bagSizeLessTooltip')"
            kind="integer"
            optional
            :min="1"
            :step="1"
          />
        </RuleFieldGrid>
      </div>
      <RuleFieldGrid :columns="2" class="mt-4">
        <RuleNumberField
          v-model="draft.minInstrumentCount"
          :label="$t('menus.masterData.customerProductRules.minInstrument')"
          kind="integer"
          optional
          :min="1"
          :step="1"
        />
        <RuleNumberField
          v-model="draft.maxInstrumentCount"
          :label="$t('menus.masterData.customerProductRules.maxInstrument')"
          kind="integer"
          optional
          :min="1"
          :step="1"
        />
        <RuleNumberField
          v-model="draft.originalUnitPrice"
          :label="$t('menus.masterData.customerProductRules.originalUnitPrice')"
          kind="decimal"
          optional
          :min="0.01"
          :step="0.5"
          :precision="2"
        />
        <div class="customer-product-rule-form__field">
          <label class="customer-product-rule-form__label">
            {{ $t('menus.masterData.customerProductRules.department') }}
          </label>
          <ElInput
            v-model="departmentText"
            :placeholder="$t('menus.masterData.customerProductRules.departmentPlaceholder')"
          />
          <p class="customer-product-rule-form__hint">{{ $t('menus.masterData.customerProductRules.departmentHint') }}</p>
        </div>
      </RuleFieldGrid>
      <div v-if="draft.ruleType === 'FOLD' && foldSplitPreview.length" class="customer-product-rule-form__fold-preview mt-4">
        <label class="customer-product-rule-form__label">{{ $t('menus.masterData.customerProductRules.foldSplitPreview') }}</label>
        <p class="customer-product-rule-form__hint">{{ foldSplitPreview.join(' + ') }}</p>
      </div>
    </RuleSectionBlock>

    <RuleSectionBlock
      v-if="showSettlementOptions"
      :title="$t('menus.masterData.customerProductRules.settlementBehavior')"
      :subtitle="$t('menus.masterData.customerProductRules.settlementBehaviorHint')"
      class="customer-product-rule-form__section"
    >
      <RuleFieldGrid :columns="2">
        <RuleSwitchField v-model="draft.skipPackaging" :label="$t('menus.masterData.customerProductRules.skipPackaging')" />
        <RuleSwitchField v-model="draft.skipDiscount" :label="$t('menus.masterData.customerProductRules.skipDiscount')" />
        <RuleSwitchField v-model="draft.isActive" :label="$t('menus.masterData.customerProductRules.isActive')" />
      </RuleFieldGrid>
    </RuleSectionBlock>
    <div v-else class="customer-product-rule-form__active-only">
      <RuleSwitchField v-model="draft.isActive" :label="$t('menus.masterData.customerProductRules.isActive')" />
    </div>

    <div v-if="showActions" class="customer-product-rule-form__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import RuleFieldGrid from '@/components/business/pricing-rules/RuleFieldGrid.vue'
import RuleSectionBlock from '@/components/business/pricing-rules/RuleSectionBlock.vue'
import RuleNumberField from '@/components/business/pricing-rules/RuleNumberField.vue'
import RuleKeywordField from '@/components/business/pricing-rules/RuleKeywordField.vue'
import RuleSwitchField from '@/components/business/pricing-rules/RuleSwitchField.vue'
import {
  appendKeywordIfMissing,
  inferBillingModeFromDraft,
  isProductRequired,
  isSettlementRule,
  syncPrimaryKeyword,
  syncRuleTypeFromBillingMode,
  type CustomerProductRuleDraft,
  type CustomerProductRuleType,
} from '@/utils/customerProductRule'

defineOptions({ name: 'CustomerProductRuleForm' })

const props = defineProps<{
  draft: CustomerProductRuleDraft
  products: Api.MasterData.ProductRecord[]
  productsLoading?: boolean
  lockProduct?: boolean
  showActions?: boolean
}>()

const productRequired = computed(() => isProductRequired(props.draft.ruleType))
const showSettlementOptions = computed(() => !isSettlementRule(props.draft.ruleType))

const bagSizeConstraintEnabled = ref(false)

watch(
  () => [props.draft.bagSizeEquals, props.draft.maxBagSizeExclusive],
  ([equals, lessThan]) => {
    bagSizeConstraintEnabled.value = equals != null || lessThan != null
  },
  { immediate: true },
)

function handleBagSizeConstraintChange(enabled: boolean) {
  if (!enabled) {
    props.draft.bagSizeEquals = undefined
    props.draft.maxBagSizeExclusive = undefined
  }
}

const displayProducts = computed(() => {
  if (!props.lockProduct || props.draft.productId == null) {
    return props.products
  }
  const productId = props.draft.productId
  if (props.products.some((p) => p.id === productId)) {
    return props.products
  }
  const name = props.draft.productName ?? props.draft.name ?? `商品 #${productId}`
  return [{ id: productId, name, category_id: 0 }, ...props.products]
})

function resolveProductKeywordDisplay(): string {
  if (props.draft.productId != null) {
    return resolveProductName(props.draft.productId) ?? ''
  }
  return props.draft.keywords.map((k) => k.trim()).find(Boolean) ?? ''
}

const productKeywordValue = computed({
  get: () => resolveProductKeywordDisplay(),
  set: (value: string) => {
    applyProductKeywordValue(value)
  },
})

function findProductByName(name: string): Api.MasterData.ProductRecord | undefined {
  const trimmed = name.trim()
  if (!trimmed) return undefined
  return displayProducts.value.find((p) => p.name.trim() === trimmed)
}

function applyProductKeywordValue(raw: string) {
  const trimmed = raw.trim()
  if (!trimmed) {
    handleProductKeywordClear()
    return
  }
  const product = findProductByName(trimmed)
  if (product) {
    props.draft.productId = product.id
    props.draft.productName = product.name
    appendKeywordIfMissing(props.draft.keywords, product.name)
    return
  }
  props.draft.productId = undefined
  props.draft.productName = undefined
  syncPrimaryKeyword(props.draft.keywords, trimmed)
}

function handleProductKeywordChange(value: string) {
  applyProductKeywordValue(value)
}

function handleProductKeywordClear() {
  props.draft.productId = undefined
  props.draft.productName = undefined
  syncPrimaryKeyword(props.draft.keywords, '')
}

function resolveProductName(productId: number): string | undefined {
  const fromList = displayProducts.value.find((p) => p.id === productId)
  if (fromList?.name) return fromList.name
  if (props.draft.productId === productId) {
    return props.draft.productName ?? props.draft.name
  }
  return undefined
}

function handleRuleTypeChange(ruleType: CustomerProductRuleType) {
  if (isSettlementRule(ruleType)) {
    props.draft.skipPackaging = false
    props.draft.skipDiscount = false
    return
  }
  if (ruleType === 'FIXED_PRICE') {
    props.draft.billingMode = 'PER_PACK'
  } else if (ruleType === 'PRICE_PER_INSTRUMENT' && !props.draft.billingMode) {
    props.draft.billingMode = 'PER_INSTRUMENT'
  }
  syncRuleTypeFromBillingMode(props.draft)
}

function handleBillingModeChange() {
  syncRuleTypeFromBillingMode(props.draft)
}

watch(
  () => props.draft.ruleType,
  () => {
    if (!props.draft.billingMode) {
      props.draft.billingMode = inferBillingModeFromDraft(props.draft)
    }
  },
  { immediate: true },
)

function handleMatchModeChange(mode: 'first' | 'any_price') {
  if (mode === 'any_price' && props.draft.acceptedPrices.length < 2) {
    const seed = props.draft.price && props.draft.price > 0 ? props.draft.price : 1
    props.draft.acceptedPrices = [seed, seed]
  }
}

function addAcceptedPrice() {
  props.draft.acceptedPrices.push(1)
}

function removeAcceptedPrice(idx: number) {
  props.draft.acceptedPrices.splice(idx, 1)
}

const departmentText = computed({
  get: () => (props.draft.departments ?? []).join('、'),
  set: (value: string) => {
    props.draft.departments = value
      .split(/[,，、]/)
      .map((s) => s.trim())
      .filter(Boolean)
  },
})

const foldSplitPreview = computed(() => {
  if (props.draft.ruleType !== 'FOLD') return [] as string[]
  const ratio = Math.max(1, Math.round(props.draft.foldRatio ?? 5))
  const sampleCount = 82
  const segments: string[] = []
  let remaining = sampleCount
  while (remaining > 0) {
    if (remaining % ratio === 0 || remaining <= (props.draft.threshold ?? 5)) {
      segments.push(String(remaining))
      break
    }
    if (remaining > ratio) {
      const chunk = Math.floor(remaining / ratio) * ratio
      segments.push(String(chunk))
      remaining -= chunk
    } else {
      segments.push(String(remaining))
      break
    }
  }
  return segments
})
</script>

<style scoped>
.customer-product-rule-form__field--full {
  grid-column: 1 / -1;
}

.customer-product-rule-form__hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.accepted-prices {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.accepted-prices__row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.accepted-prices__input {
  width: 160px;
}
.customer-product-rule-form__bag-size-block {
  padding: 12px 14px;
  border: 1px dashed var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.customer-product-rule-form__bag-size-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.customer-product-rule-form__bag-size-header .customer-product-rule-form__hint {
  margin-top: 4px;
  line-height: 1.5;
}

.customer-product-rule-form__field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.customer-product-rule-form__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.customer-product-rule-form__optional {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.customer-product-rule-form__section {
  margin-top: 16px;
  margin-bottom: 0;
  padding: 14px 16px;
}

.customer-product-rule-form__active-only {
  margin-top: 16px;
  padding: 0 4px;
}

.customer-product-rule-form__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
</style>
