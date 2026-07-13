<template>
  <div class="products-page flex h-full gap-4 p-4">
    <aside class="w-56 shrink-0">
      <ElCard shadow="never">
        <template #header>
          <span class="font-medium">产品分类</span>
        </template>
        <div class="space-y-1">
          <div
            class="cursor-pointer rounded px-3 py-2 text-sm transition-colors"
            :class="selectedCategoryId === null ? 'bg-primary/10 text-primary font-medium' : 'hover:bg-gray-50'"
            @click="selectCategory(null)"
          >
            全部
          </div>
          <div
            v-for="cat in categories"
            :key="cat.id"
            class="cursor-pointer rounded px-3 py-2 text-sm transition-colors"
            :class="selectedCategoryId === cat.id ? 'bg-primary/10 text-primary font-medium' : 'hover:bg-gray-50'"
            @click="selectCategory(cat.id)"
          >
            {{ cat.name }}
          </div>
        </div>
      </ElCard>
    </aside>

    <main class="min-w-0 flex-1 space-y-4">
      <ElCard shadow="never">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="text-lg font-semibold">{{ $t('menus.masterData.products') }}</span>
            <ElButton type="primary" @click="openCreate">新增产品</ElButton>
          </div>
        </template>

        <ElForm :inline="true" :model="filterForm" class="mb-4 flex flex-wrap items-center gap-y-2">
          <ElFormItem :label="$t('menus.masterData.productFilters.keyword')">
            <ElInput
              v-model="filterForm.keyword"
              :placeholder="$t('menus.masterData.productFilters.keywordPlaceholder')"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
          </ElFormItem>
          <ElFormItem :label="$t('menus.masterData.productFilters.category')">
            <ElSelect
              v-model="filterForm.categoryId"
              clearable
              :placeholder="$t('menus.masterData.productFilters.categoryAll')"
              style="width: 160px"
              @change="handleSearch"
            >
              <ElOption v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem :label="$t('menus.masterData.productFilters.pricingPath')">
            <ElSelect
              v-model="filterForm.pricingPath"
              clearable
              :placeholder="$t('menus.masterData.productFilters.pricingPathAll')"
              style="width: 200px"
              @change="handleSearch"
            >
              <ElOption :label="$t('menus.masterData.productFilters.pricingPathInherit')" value="inherit" />
              <ElOption
                v-for="opt in PRICING_MODE_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem :label="$t('menus.masterData.productFilters.status')">
            <ElSelect
              v-model="filterForm.status"
              clearable
              :placeholder="$t('menus.masterData.productFilters.statusAll')"
              style="width: 120px"
              @change="handleSearch"
            >
              <ElOption :label="$t('menus.masterData.productFilters.statusActive')" value="active" />
              <ElOption :label="$t('menus.masterData.productFilters.statusInactive')" value="inactive" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem>
            <ElButton type="primary" @click="handleSearch">{{ $t('table.searchBar.search') }}</ElButton>
            <ElButton @click="resetFilters">{{ $t('table.searchBar.reset') }}</ElButton>
          </ElFormItem>
        </ElForm>

        <ElTable v-loading="loading" :data="filteredProducts" stripe border>
        <ElTableColumn prop="name" label="产品名称" min-width="140" />
        <ElTableColumn prop="category_name" label="分类" width="140" />
        <ElTableColumn label="计价路径" width="180">
          <template #default="{ row }">
            {{ formatEffectivePricingPath(row.pricing_path, row.pricing_mode) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="priority" label="优先级" width="80" align="center" />
        <ElTableColumn :label="$t('menus.masterData.product.publicPrice')" width="110" align="right">
          <template #default="{ row }">{{ formatPrice(row.public_price) }}</template>
        </ElTableColumn>
        <ElTableColumn :label="$t('menus.masterData.product.originalPrice')" width="110" align="right">
          <template #default="{ row }">{{ formatPrice(row.original_price) }}</template>
        </ElTableColumn>
        <ElTableColumn label="匹配规则" min-width="200">
          <template #default="{ row }">
            <ElTag v-for="(rule, idx) in (row.match_rules || []).slice(0, 2)" :key="idx" size="small" class="mr-1">
              {{ rule.matchType }}: {{ rule.patternValue || (rule.conditions?.length + ' 条件') }}
            </ElTag>
            <span v-if="(row.match_rules?.length || 0) > 2" class="text-xs text-gray-400">+{{ row.match_rules!.length - 2 }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="别名" width="120">
          <template #default="{ row }">{{ row.aliases?.length || 0 }} 条</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton type="primary" link @click="openVariants(row)">规格</ElButton>
            <ElButton type="primary" link @click="openEdit(row)">编辑</ElButton>
            <ElButton type="danger" link @click="handleDelete(row)">删除</ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <span class="text-gray-400">{{ $t('menus.masterData.productFilters.noResults') }}</span>
        </template>
      </ElTable>
      </ElCard>

      <ElCard shadow="never">
        <template #header>
          <span class="font-medium">匹配预览</span>
        </template>
        <ElForm :inline="true" :model="previewForm" class="mb-3">
          <ElFormItem label="类型">
            <ElInput v-model="previewForm.type" placeholder="额外包(纸塑袋)" style="width: 160px" />
          </ElFormItem>
          <ElFormItem label="包名">
            <ElInput v-model="previewForm.packName" placeholder="洁牙机尖-4/Z7526" style="width: 200px" />
          </ElFormItem>
          <ElFormItem label="包装材料">
            <ElInput v-model="previewForm.packageMaterial" placeholder="高温纸塑袋" style="width: 160px" />
          </ElFormItem>
          <ElFormItem label="器械数">
            <ElInputNumber v-model="previewForm.instrumentCount" :min="0" />
          </ElFormItem>
          <ElFormItem>
            <ElButton type="primary" @click="runPreview">测试匹配</ElButton>
          </ElFormItem>
        </ElForm>
        <ElAlert
          v-if="previewResult"
          :title="previewResult.matched ? `命中: ${previewResult.product_name}` : '未命中任何产品'"
          :type="previewResult.matched ? 'success' : 'info'"
          :description="previewResult.matched
            ? `分类 ${previewResult.category_name} (${previewResult.category_code}) → ${getPricingModeLabel(previewResult.pricing_path)} [${previewResult.source}]${previewResult.variant_id ? ` · variant#${previewResult.variant_id}` : ''}${previewResult.spec_fingerprint ? ` · ${previewResult.spec_fingerprint}` : ''}`
            : '请调整匹配规则或样例数据'"
          show-icon
          :closable="false"
        />
      </ElCard>
    </main>

    <ElDrawer v-model="drawerVisible" :title="editingId ? '编辑产品' : '新增产品'" size="640px" destroy-on-close>
      <ElForm ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <ElFormItem label="产品名称" prop="name">
          <ElInput v-model="form.name" />
        </ElFormItem>
        <ElFormItem label="分类" prop="categoryId">
          <ElSelect v-model="form.categoryId" class="w-full">
            <ElOption v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="SKU">
          <ElInput v-model="form.skuCode" />
        </ElFormItem>
        <ElFormItem label="计价模式">
          <ElSelect
            v-model="form.pricingMode"
            clearable
            class="w-full"
            :placeholder="PRICING_MODE_INHERIT_PLACEHOLDER"
          >
            <ElOption
              v-for="opt in PRICING_MODE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="优先级">
          <ElInputNumber v-model="form.priority" :min="0" class="w-full" />
        </ElFormItem>
        <ElFormItem :label="$t('menus.masterData.product.publicPrice')">
          <ElInputNumber
            v-model="form.publicPrice"
            :min="0"
            :precision="2"
            :step="0.01"
            class="w-full"
            controls-position="right"
          >
            <template #suffix>元</template>
          </ElInputNumber>
        </ElFormItem>
        <ElFormItem :label="$t('menus.masterData.product.originalPrice')">
          <ElInputNumber
            v-model="form.originalPrice"
            :min="0"
            :precision="2"
            :step="0.01"
            class="w-full"
            controls-position="right"
          >
            <template #suffix>元</template>
          </ElInputNumber>
        </ElFormItem>

        <ElDivider>结构化匹配规则</ElDivider>
        <div v-for="(rule, index) in form.matchRules" :key="index" class="mb-4 rounded border p-3">
          <div class="mb-2 flex items-center justify-between">
            <span class="text-sm font-medium">规则 #{{ index + 1 }}</span>
            <ElButton type="danger" link @click="removeRule(index)">移除</ElButton>
          </div>
          <ElFormItem label="匹配类型">
            <ElSelect v-model="rule.matchType" class="w-full" @change="onRuleTypeChange(rule)">
              <ElOption label="精确匹配 (EXACT_NAME)" value="EXACT_NAME" />
              <ElOption label="包含 (CONTAINS)" value="CONTAINS" />
              <ElOption label="正则 (REGEX)" value="REGEX" />
              <ElOption label="复合条件 (COMPOSITE)" value="COMPOSITE" />
            </ElSelect>
          </ElFormItem>
          <template v-if="rule.matchType !== 'COMPOSITE'">
            <ElFormItem label="目标字段">
              <ElSelect v-model="rule.targetField" class="w-full">
                <ElOption v-for="f in fieldOptions" :key="f.value" :label="f.label" :value="f.value" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="匹配值">
              <ElInput v-model="rule.patternValue" placeholder="如 洁牙机尖" />
            </ElFormItem>
          </template>
          <template v-else>
            <div v-for="(cond, ci) in rule.conditions" :key="ci" class="mb-2 flex gap-2">
              <ElSelect v-model="cond.field" placeholder="字段" style="width: 130px">
                <ElOption v-for="f in fieldOptions" :key="f.value" :label="f.label" :value="f.value" />
              </ElSelect>
              <ElSelect v-model="cond.operator" placeholder="运算符" style="width: 110px">
                <ElOption v-for="op in operatorOptions" :key="op.value" :label="op.label" :value="op.value" />
              </ElSelect>
              <ElInput v-model="cond.value" placeholder="值" class="flex-1" />
              <ElButton type="danger" link @click="rule.conditions!.splice(ci, 1)">删</ElButton>
            </div>
            <ElButton size="small" @click="addCondition(rule)">添加条件</ElButton>
          </template>
          <ElFormItem label="规则优先级">
            <ElInputNumber v-model="rule.priority" :min="0" />
          </ElFormItem>
        </div>
        <ElButton class="mb-4" @click="addRule">添加匹配规则</ElButton>

        <ElDivider>产品别名</ElDivider>
        <div v-for="(alias, ai) in form.aliases" :key="ai" class="mb-2 flex gap-2">
          <ElInput v-model="alias.alias" placeholder="别名文本" class="flex-1" />
          <ElSelect v-model="alias.matchType" style="width: 110px">
            <ElOption label="包含" value="CONTAINS" />
            <ElOption label="精确" value="EXACT" />
          </ElSelect>
          <ElButton type="danger" link @click="form.aliases!.splice(ai, 1)">删</ElButton>
        </div>
        <ElButton size="small" @click="addAlias">添加别名</ElButton>
      </ElForm>
      <template #footer>
        <ElButton @click="drawerVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="submitForm">保存</ElButton>
      </template>
    </ElDrawer>

    <ElDrawer v-model="variantDrawerVisible" :title="variantDrawerTitle" size="720px" destroy-on-close>
      <ElTable v-loading="variantLoading" :data="variants" stripe border size="small">
        <ElTableColumn prop="display_name" label="展示名" min-width="220" />
        <ElTableColumn prop="pack_name" label="包名" min-width="160" />
        <ElTableColumn prop="type" label="类型" width="140" />
        <ElTableColumn prop="package_material" label="包装材料" width="140" />
        <ElTableColumn prop="spec_fingerprint" label="指纹" width="160" />
        <ElTableColumn label="参考价" width="90" align="right">
          <template #default="{ row }">{{ formatPrice(row.public_price) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="occurrence_count" label="出现次数" width="90" align="center" />
      </ElTable>
    </ElDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listProductCategories } from '@/api/master-data/productCategoriesApi'
import {
  createProduct,
  deleteProduct,
  listProducts,
  listProductVariants,
  matchPreview,
  updateProduct,
} from '@/api/master-data/productsApi'
import {
  PRICING_MODE_INHERIT_PLACEHOLDER,
  PRICING_MODE_OPTIONS,
  formatEffectivePricingPath,
  getPricingModeLabel,
} from '@/constants/pricingModeLabels'

const loading = ref(false)
const saving = ref(false)
const drawerVisible = ref(false)
const editingId = ref<number | null>(null)
const selectedCategoryId = ref<number | null>(null)
const categories = ref<Api.MasterData.ProductCategoryRecord[]>([])
const products = ref<Api.MasterData.ProductRecord[]>([])
const appliedKeyword = ref('')
const filterForm = reactive({
  keyword: '',
  categoryId: '' as '' | number,
  pricingPath: '' as '' | 'inherit' | typeof PRICING_MODE_OPTIONS[number]['value'],
  status: '' as '' | 'active' | 'inactive',
})
const formRef = ref<FormInstance>()
const previewResult = ref<Api.MasterData.MatchPreviewResult | null>(null)
const variantDrawerVisible = ref(false)
const variantDrawerTitle = ref('规格变体')
const variantLoading = ref(false)
const variants = ref<Api.MasterData.ProductVariantRecord[]>([])

const fieldOptions = [
  { label: '包名', value: 'pack_name' },
  { label: '类型', value: 'type' },
  { label: '包装材料', value: 'package_material' },
  { label: '分类号', value: 'category_no' },
  { label: '器械数', value: 'instrument_count' },
]

const operatorOptions = [
  { label: '等于', value: 'EQ' },
  { label: '不等于', value: 'NE' },
  { label: '包含', value: 'CONTAINS' },
  { label: '不包含', value: 'NOT_CONTAINS' },
  { label: '正则', value: 'REGEX' },
  { label: '>', value: 'GT' },
  { label: '>=', value: 'GTE' },
  { label: '<', value: 'LT' },
  { label: '<=', value: 'LTE' },
]

const form = reactive<Api.MasterData.SaveProductPayload>({
  categoryId: 0,
  name: '',
  skuCode: '',
  pricingMode: '',
  publicPrice: undefined,
  originalPrice: undefined,
  priority: 100,
  isActive: true,
  matchRules: [],
  aliases: [],
})

const previewForm = reactive<Api.MasterData.MatchPreviewPayload>({
  type: '额外包(纸塑袋)',
  packName: '',
  packageMaterial: '',
  instrumentCount: 1,
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
}

function defaultRule(): Api.MasterData.MatchRule {
  return {
    matchType: 'CONTAINS',
    targetField: 'pack_name',
    patternValue: '',
    priority: 100,
    isActive: true,
    conditions: [],
  }
}

function onRuleTypeChange(rule: Api.MasterData.MatchRule) {
  if (rule.matchType === 'COMPOSITE' && (!rule.conditions || rule.conditions.length === 0)) {
    rule.conditions = [{ field: 'pack_name', operator: 'CONTAINS', value: '' }]
  }
}

function addRule() {
  form.matchRules = form.matchRules || []
  form.matchRules.push(defaultRule())
}

function removeRule(index: number) {
  form.matchRules?.splice(index, 1)
}

function addCondition(rule: Api.MasterData.MatchRule) {
  if (!rule.conditions) rule.conditions = []
  rule.conditions.push({ field: 'pack_name', operator: 'CONTAINS', value: '' })
}

function addAlias() {
  form.aliases = form.aliases || []
  form.aliases.push({ alias: '', matchType: 'CONTAINS', priority: 100, isActive: true })
}

async function loadCategories() {
  categories.value = await listProductCategories()
  if (categories.value.length && !form.categoryId) {
    form.categoryId = categories.value[0].id
  }
}

async function loadProducts() {
  loading.value = true
  try {
    products.value = await listProducts(selectedCategoryId.value ?? undefined)
  } catch {
    ElMessage.error('加载产品列表失败')
  } finally {
    loading.value = false
  }
}

function selectCategory(id: number | null) {
  selectedCategoryId.value = id
  loadProducts()
}

function isProductActive(row: Api.MasterData.ProductRecord) {
  return row.is_active !== false
}

function getEffectivePricingPathValue(row: Api.MasterData.ProductRecord) {
  if (row.pricing_mode) return row.pricing_mode
  return row.pricing_path || ''
}

const filteredProducts = computed(() => {
  let data = products.value
  const kw = appliedKeyword.value.trim().toLowerCase()
  if (kw) {
    data = data.filter(
      (p) =>
        p.name?.toLowerCase().includes(kw) ||
        p.sku_code?.toLowerCase().includes(kw),
    )
  }
  if (filterForm.categoryId) {
    data = data.filter((p) => p.category_id === filterForm.categoryId)
  }
  if (filterForm.pricingPath) {
    if (filterForm.pricingPath === 'inherit') {
      data = data.filter((p) => !p.pricing_mode)
    } else {
      data = data.filter((p) => getEffectivePricingPathValue(p) === filterForm.pricingPath)
    }
  }
  if (filterForm.status) {
    data = data.filter((p) =>
      filterForm.status === 'active' ? isProductActive(p) : !isProductActive(p),
    )
  }
  return data
})

function handleSearch() {
  appliedKeyword.value = filterForm.keyword.trim()
}

function resetFilters() {
  filterForm.keyword = ''
  filterForm.categoryId = ''
  filterForm.pricingPath = ''
  filterForm.status = ''
  appliedKeyword.value = ''
}

function formatPrice(value?: number | null) {
  if (value == null) return '—'
  return `${Number(value).toFixed(2)} 元`
}

function resetForm() {
  form.name = ''
  form.skuCode = ''
  form.pricingMode = ''
  form.publicPrice = undefined
  form.originalPrice = undefined
  form.priority = 100
  form.matchRules = [defaultRule()]
  form.aliases = []
  if (categories.value.length) {
    form.categoryId = selectedCategoryId.value ?? categories.value[0].id
  }
}

function openCreate() {
  editingId.value = null
  resetForm()
  drawerVisible.value = true
}

function openEdit(row: Api.MasterData.ProductRecord) {
  editingId.value = row.id
  form.categoryId = row.category_id
  form.name = row.name
  form.skuCode = row.sku_code || ''
  form.pricingMode = row.pricing_mode || ''
  form.publicPrice = row.public_price ?? undefined
  form.originalPrice = row.original_price ?? undefined
  form.priority = row.priority ?? 100
  form.matchRules = JSON.parse(JSON.stringify(row.match_rules || [defaultRule()]))
  form.aliases = JSON.parse(JSON.stringify(row.aliases || []))
  drawerVisible.value = true
}

async function openVariants(row: Api.MasterData.ProductRecord) {
  variantDrawerTitle.value = `${row.name} · 规格变体`
  variantDrawerVisible.value = true
  variantLoading.value = true
  try {
    variants.value = await listProductVariants(row.id)
  } catch {
    variants.value = []
    ElMessage.warning('加载规格变体失败')
  } finally {
    variantLoading.value = false
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = { ...form, aliases: form.aliases?.filter(a => a.alias?.trim()) }
    if (editingId.value) {
      await updateProduct(editingId.value, payload)
      ElMessage.success('产品已更新')
    } else {
      await createProduct(payload)
      ElMessage.success('产品已创建')
    }
    drawerVisible.value = false
    await loadProducts()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: Api.MasterData.ProductRecord) {
  try {
    await ElMessageBox.confirm(`确定删除产品「${row.name}」？`, '删除确认', { type: 'warning' })
    await deleteProduct(row.id)
    ElMessage.success('已删除')
    await loadProducts()
  } catch {
    // cancelled
  }
}

async function runPreview() {
  try {
    previewResult.value = await matchPreview({ ...previewForm })
  } catch {
    ElMessage.error('匹配预览失败')
  }
}

onMounted(async () => {
  await loadCategories()
  await loadProducts()
})
</script>
