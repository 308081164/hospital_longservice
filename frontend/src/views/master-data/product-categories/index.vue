<template>
  <div class="product-categories-page p-4">
    <ElCard shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-lg font-semibold">{{ $t('menus.masterData.productCategories') }}</span>
          <ElButton type="primary" @click="openCreate">新增分类</ElButton>
        </div>
      </template>

      <ElTable v-loading="loading" :data="categories" stripe border>
        <ElTableColumn prop="code" label="编码" width="160" />
        <ElTableColumn prop="name" label="名称" min-width="180" />
        <ElTableColumn label="计价路径" width="220">
          <template #default="{ row }">
            {{ getPricingModeLabel(row.pricing_path) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="sort_order" label="排序" width="80" align="center" />
        <ElTableColumn label="产品数" width="90" align="center">
          <template #default="{ row }">{{ row.product_count ?? 0 }}</template>
        </ElTableColumn>
        <ElTableColumn label="子分类" width="90" align="center">
          <template #default="{ row }">{{ row.child_count ?? 0 }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton type="danger" link @click="handleDelete(row)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>

    <ElDialog v-model="dialogVisible" title="新增产品分类" width="520px" destroy-on-close>
      <ElForm ref="formRef" :model="form" :rules="rules" label-width="100px">
        <ElFormItem label="编码" prop="code">
          <ElInput v-model="form.code" placeholder="如 SMALL_ITEM" />
        </ElFormItem>
        <ElFormItem label="名称" prop="name">
          <ElInput v-model="form.name" placeholder="如 小件器械" />
        </ElFormItem>
        <ElFormItem label="计价路径" prop="pricingPath">
          <ElSelect v-model="form.pricingPath" class="w-full" placeholder="选择计价路径">
            <ElOption
              v-for="opt in PRICING_MODE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="排序">
          <ElInputNumber v-model="form.sortOrder" :min="0" class="w-full" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="submitCreate">保存</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createProductCategory, deleteProductCategory, listProductCategories } from '@/api/master-data/productCategoriesApi'
import { PRICING_MODE_OPTIONS, getPricingModeLabel } from '@/constants/pricingModeLabels'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const categories = ref<Api.MasterData.ProductCategoryRecord[]>([])
const formRef = ref<FormInstance>()

const form = reactive<Api.MasterData.SaveProductCategoryPayload>({
  code: '',
  name: '',
  pricingPath: 'standard',
  sortOrder: 0,
  isActive: true,
})

const rules: FormRules = {
  code: [{ required: true, message: '请输入分类编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  pricingPath: [{ required: true, message: '请选择计价路径', trigger: 'change' }],
}

async function loadData() {
  loading.value = true
  try {
    categories.value = await listProductCategories()
  } catch {
    ElMessage.error('加载产品分类失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.code = ''
  form.name = ''
  form.pricingPath = 'standard'
  form.sortOrder = 0
  dialogVisible.value = true
}

async function submitCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await createProductCategory({ ...form })
    ElMessage.success('分类已创建')
    dialogVisible.value = false
    await loadData()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: Api.MasterData.ProductCategoryRecord) {
  const productCount = row.product_count ?? 0
  const childCount = row.child_count ?? 0
  let message = `确定删除分类「${row.name}」？`
  if (productCount > 0) {
    message = `该分类下有 ${productCount} 个产品，无法删除。请先迁移或删除产品。`
    ElMessage.warning(message)
    return
  }
  if (childCount > 0) {
    message = `该分类有 ${childCount} 个子分类，将执行软删除（含子分类）。是否继续？`
  }
  try {
    await ElMessageBox.confirm(message, '删除确认', { type: 'warning' })
    await deleteProductCategory(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch {
    // cancelled or failed
  }
}

onMounted(loadData)
</script>
