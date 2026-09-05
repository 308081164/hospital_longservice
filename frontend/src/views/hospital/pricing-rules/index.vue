<template>
  <div class="pricing-rules-page p-4">
    <header class="page-header">
      <div class="page-header__text">
        <h1 class="page-title">通用计价规则</h1>
        <p class="page-desc">
          维护全行业默认灭菌价目。各医院特色方案请在
          <RouterLink to="/master-data/customers" class="link-customers">特殊计价客户管理</RouterLink>
          中绑定维护。
        </p>
      </div>
      <div v-if="currentRule" class="page-header__actions">
        <ElTag v-if="currentRule.isActive" type="success" size="small">已激活</ElTag>
        <ElTag v-else type="info" size="small">未激活</ElTag>
        <ElDropdown v-if="selectedRuleId" trigger="click" @command="handleRevisionCommand">
          <ElButton>
            版本历史
            <ElIcon class="ml-1"><ArrowDown /></ElIcon>
          </ElButton>
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem v-if="!revisionList.length" disabled>暂无历史版本</ElDropdownItem>
              <ElDropdownItem v-for="rev in revisionList" :key="rev.id" :command="rev.id">
                v{{ rev.version }} · {{ formatRevisionTime(rev.createdAt) }}
              </ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>
        <ElButton type="primary" :loading="saving" @click="handleSave">
          保存<span v-if="dirty" class="dirty-dot">●</span>
        </ElButton>
      </div>
    </header>

    <ElEmpty v-if="loading" description="正在加载通用规则..." class="py-16" />
    <div v-else-if="loadError" class="state-panel">
      <p class="state-title">无法加载规则数据</p>
      <p class="state-desc">请确认后端服务已启动</p>
      <ElButton type="primary" @click="loadRules">重新加载</ElButton>
    </div>
    <div v-else-if="!currentRule" class="state-panel">
      <p class="state-title">尚未配置通用计价规则</p>
      <p class="state-desc">可从系统默认模板创建全行业通用方案</p>
      <ElButton type="primary" @click="handleCreate">从默认模板创建</ElButton>
    </div>

    <template v-else>
      <ElAlert
          v-if="validationErrors.length"
          title="配置校验警告"
          type="warning"
          :description="validationErrors.join('；')"
          show-icon
          closable
          class="mb-4"
        />

      <ElCard shadow="never" class="meta-card mb-4">
        <ElForm :model="formData" label-width="72px" class="meta-form">
          <ElRow :gutter="16">
            <ElCol :xs="24" :sm="12" :md="8">
              <ElFormItem label="方案名称">
                <ElInput v-model="formData.name" placeholder="标准灭菌计费规则" @input="onRuleNameInput" />
              </ElFormItem>
            </ElCol>
            <ElCol :xs="24" :sm="8" :md="4">
              <ElFormItem label="版本">
                <ElInput v-model="currentRule.rules.version" placeholder="v2.0" @input="markDirty" />
              </ElFormItem>
            </ElCol>
            <ElCol :xs="24" :sm="24" :md="12">
              <ElFormItem label="说明">
                <ElInput v-model="formData.description" placeholder="规则用途说明" @input="markDirty" />
              </ElFormItem>
            </ElCol>
          </ElRow>
        </ElForm>
      </ElCard>

      <ElTabs v-model="activeCategory" class="rule-tabs mb-4">
        <ElTabPane v-for="cat in tabCategories" :key="cat.key" :label="cat.label" :name="cat.key" />
        <ElTabPane label="JSON" name="json" />
      </ElTabs>

      <ElCard v-if="activeCategory === 'json'" shadow="never" class="json-advanced-card mb-4">
        <template #header>
          <div class="json-advanced-card__header">
            <div>
              <span class="json-advanced-card__title">JSON 高级配置</span>
              <p class="json-advanced-card__desc">仅供排查与批量调整，默认只读。误改可能导致规则校验失败或对账异常。</p>
            </div>
            <div class="json-advanced-card__actions">
              <div class="json-advanced-card__switch">
                <span class="json-advanced-card__switch-label">允许编辑</span>
                <ElSwitch
                  v-model="jsonEditEnabled"
                  :before-change="beforeJsonEditToggle"
                />
              </div>
              <ElButton size="small" @click="handleCopyJson">复制</ElButton>
              <ElButton size="small" type="primary" :disabled="!jsonEditEnabled" @click="handleImportJson">
                导入到规则
              </ElButton>
            </div>
          </div>
        </template>
        <ElAlert
          v-if="!jsonEditEnabled"
          title="当前为只读预览模式"
          type="info"
          :closable="false"
          show-icon
          class="json-advanced-card__alert"
        />
        <ElAlert
          v-else
          title="编辑模式已启用"
          description="请确认 JSON 语法与字段结构正确后再点击「导入到规则」。错误配置可能在保存或对账时引发异常。"
          type="warning"
          :closable="false"
          show-icon
          class="json-advanced-card__alert"
        />
        <ElInput
          v-model="jsonText"
          type="textarea"
          :rows="18"
          class="font-mono json-advanced-card__textarea"
          :class="{ 'json-advanced-card__textarea--readonly': !jsonEditEnabled }"
          :readonly="!jsonEditEnabled"
          @input="onJsonTextInput"
        />
      </ElCard>

      <div v-else class="rule-panel">
          <RuleCategoryPanel
            v-if="activeCategory === '高温'"
            category="高温"
            title="高温灭菌计价"
            subtitle="无纺布与纸塑袋的基础灭菌价目"
            theme="heat"
            badge="高温"
            tag="灭菌"
          >
            <RuleSectionBlock
              title="高温无纺布"
              subtitle="无纺布包装材料的基础计价参数"
            >
              <RuleFieldGrid :columns="3">
                <RuleNumberField
                  v-model="htnw.minCharge"
                  label="最低收费(元)"
                  :quick-steps="[0.5, 1, 5, 10]"
                  @change="markDirty"
                />
                <RuleNumberField
                  v-model="htnw.flatPerPackagePrice"
                  label="件单价(元)"
                  :quick-steps="[0.5, 1]"
                  @change="markDirty"
                />
                <RuleNumberField
                  v-model="htnw.flatRateThreshold"
                  label="≥阈值按件计费"
                  kind="integer"
                  :precision="0"
                  :step="1"
                  tooltip="器械数达到该阈值后，按件单价计费而非袋费封顶"
                  @change="markDirty"
                />
              </RuleFieldGrid>
            </RuleSectionBlock>

            <RuleSectionBlock
              title="高温纸塑袋"
              subtitle="纸塑袋灭菌费、封顶价及按尺寸的袋型价目"
            >
              <RuleFieldGrid :columns="2">
                <RuleNumberField
                  v-model="htpp.perPackagePrice"
                  label="每件灭菌费(元)"
                  :quick-steps="[0.5, 1]"
                  @change="markDirty"
                />
                <RuleNumberField
                  v-model="htpp.minCharge"
                  label="封顶收费(元)"
                  :quick-steps="[0.5, 1, 5, 10]"
                  @change="markDirty"
                />
              </RuleFieldGrid>
              <RuleBagSizeTable
                v-model="htpp.bagSizes"
                class="mt-4"
                add-label="添加高温袋型"
                @change="markDirty"
              />
            </RuleSectionBlock>
          </RuleCategoryPanel>

          <RuleCategoryPanel
            v-if="activeCategory === '低温'"
            category="低温"
            title="低温灭菌计价"
            subtitle="阶梯计价、余数单价与袋型单件价"
            theme="cold"
            badge="低温"
            tag="灭菌"
          >
            <RuleSectionBlock title="低温无纺布" subtitle="阶梯计价与余数单价">
              <RuleFieldGrid :columns="2">
                <RuleNumberField
                  v-model="ltnw.minSingleCharge"
                  label="单件最低收费(元)"
                  :quick-steps="[1, 5, 10]"
                  @change="markDirty"
                />
                <RuleNumberField
                  v-model="ltnw.remainderPerPiecePrice"
                  label="阶梯余数单价(元)"
                  :quick-steps="[1, 5, 10]"
                  @change="markDirty"
                />
              </RuleFieldGrid>
              <RuleTierPriceTable
                v-model="ltnw.tierPrices"
                class="mt-4"
                add-label="添加无纺布阶梯"
                @change="markDirty"
              />
            </RuleSectionBlock>

            <RuleSectionBlock title="低温纸塑袋" subtitle="阶梯总价与袋型单件价">
              <RuleTierPriceTable
                v-model="ltpp.tierPrices"
                add-label="添加纸塑袋阶梯"
                @change="markDirty"
              />
              <RuleBagSizeTable
                v-model="ltpp.bagSizes"
                class="mt-4"
                price-label="单件价(元)"
                add-label="添加低温袋型"
                :price-quick-steps="[1, 5, 10]"
                hint="低温纸塑袋按尺寸匹配的单件价格"
                @change="markDirty"
              />
            </RuleSectionBlock>
          </RuleCategoryPanel>

          <RuleCategoryPanel
            v-if="activeCategory === '包装'"
            category="包装"
            title="包装收费规则"
            subtitle="辅料包、纸塑袋等额外包装费用"
            theme="packaging"
            badge="包装"
            tag="加收"
          >
            <RulePackagingTable v-model="pg" @change="markDirty" />
          </RuleCategoryPanel>

          <RuleCategoryPanel
            v-if="activeCategory === '小件识别'"
            category="小件识别"
            title="小件识别规则"
            subtitle="包名命中关键词后按折算比例重算有效器械件数"
            theme="needle"
            badge="小件"
            tag="折算"
          >
            <RuleFieldGrid :columns="2">
              <RuleNumberField
                v-model="nd.threshold"
                label="默认触发件数"
                kind="integer"
                :precision="0"
                :step="1"
                tooltip="器械数不超过该值时触发小件折算逻辑；可被下方关键词独立配置覆盖"
                @change="markDirty"
              />
              <RuleNumberField
                v-model="nd.foldRatio"
                label="默认折算比例"
                kind="integer"
                :precision="0"
                :step="1"
                tooltip="命中小件关键词后，约每 N 件折算为 1 件计费；可被下方关键词独立配置覆盖"
                @change="markDirty"
              />
            </RuleFieldGrid>
            <RuleFieldGrid :columns="1">
              <RuleSelectField
                v-model="needleMatchMode"
                label="默认匹配模式"
                :options="keywordMatchModeOptions"
                tooltip="未标注的关键词统一使用此模式；单个词可加后缀覆盖，如「车针@contains」含词即触发、「车针@exact」严格对齐"
              />
            </RuleFieldGrid>
            <RuleKeywordField
              v-model="nd.keywords"
              class="needle-keywords-field"
              label="识别关键词"
              size="large"
              :rows="5"
              :max-rows="14"
              show-count
              hint="逗号分隔；词后加 @contains 含词即触发、@exact 严格对齐；客户特色关键词扩展请在特殊计价客户管理中配置"
              @change="markDirty"
            />
            <RuleNeedleKeywordConfigTable
              v-model="needleKeywordConfigs"
              class="needle-keyword-configs"
              :default-threshold="nd.threshold"
              :default-fold-ratio="nd.foldRatio"
              :default-match-mode="needleMatchMode"
              @change="markDirty"
            />
            <div v-if="selectedRuleId" class="needle-actions">
              <ElButton size="small" type="primary" :loading="needleBatchSaving" @click="handleBatchSaveNeedleKeywords">
                保存小件识别规则
              </ElButton>
              <ElButton size="small" :loading="needleImpactLoading" @click="handleNeedleImpactPreview">
                影响预览
              </ElButton>
              <span class="needle-actions__hint">保存后立即写入数据库并生成版本快照（含关键词独立配置）</span>
            </div>
          </RuleCategoryPanel>

          <RuleCategoryPanel
            v-if="activeCategory === '数据清洗'"
            category="数据清洗"
            title="数据清洗规则"
            subtitle="上传对账 Excel 时的行级预处理"
            theme="cleaning"
            badge="清洗"
            tag="导入"
          >
            <RuleFieldGrid :columns="2">
              <RuleSwitchField v-model="cl.removeFirstRow" label="删除首行" @change="markDirty" />
              <RuleSwitchField v-model="cl.dropSummaryRows" label="丢弃汇总行" @change="markDirty" />
              <RuleSwitchField v-model="cl.trimPackagingMaterial" label="去除包装材料列噪声" @change="markDirty" />
              <RuleSwitchField
                v-model="cl.recomputeTotalsWhenPriceChanges"
                label="价格变动时重算合计"
                @change="markDirty"
              />
            </RuleFieldGrid>
            <RuleKeywordField
              v-model="cl.summaryKeywords"
              class="mt-4"
              label="汇总行关键词"
              hint="命中这些关键词的行将被识别为汇总行并丢弃"
              @change="markDirty"
            />
          </RuleCategoryPanel>

          <RuleCategoryPanel
            v-if="activeCategory === '物流'"
            category="物流"
            title="物流规则"
            subtitle="按发货日期去重计次收取物流费"
            theme="logistics"
            badge="物流"
            tag="运费"
          >
            <RuleFieldGrid :columns="3">
              <RuleSwitchField v-model="lg.enabled" label="启用物流费" @change="markDirty" />
              <RuleNumberField
                v-model="lg.feePerTrip"
                label="单次费用(元)"
                :quick-steps="[5, 10, 50]"
                @change="markDirty"
              />
              <RuleNumberField
                v-model="lg.defaultLogisticsFee"
                label="默认物流费(元)"
                :quick-steps="[5, 10, 50]"
                @change="markDirty"
              />
              <RuleNumberField
                v-model="lg.dayBoundaryHour"
                label="跨天时间点(时)"
                kind="integer"
                :min="0"
                :max="23"
                :precision="0"
                :step="1"
                @change="markDirty"
              />
              <RuleSwitchField v-model="lg.mergeAdjacentDays" label="合并相邻天数" @change="markDirty" />
              <RuleNumberField
                v-model="lg.mergeWindowDays"
                label="合并窗口(天)"
                kind="integer"
                :precision="0"
                :step="1"
                @change="markDirty"
              />
            </RuleFieldGrid>
          </RuleCategoryPanel>

          <RuleCategoryPanel
            v-if="activeCategory === '结款函'"
            category="结款函"
            title="结款函规则"
            subtitle="导出结款函的模板、格式与费用项"
            theme="settlement"
            badge="结款"
            tag="导出"
          >
            <RuleSectionBlock title="结款函基础设置" subtitle="导出结款函时的全局格式与默认模板">
              <RuleFieldGrid :columns="3">
                <RuleTextField v-model="sl.companyName" label="公司名称" placeholder="公司名称" @change="markDirty" />
                <RuleSelectField
                  v-model="sl.defaultTemplateId"
                  label="默认模板"
                  placeholder="选择默认模板"
                  :options="settlementTemplateOptions"
                  @change="markDirty"
                />
                <RuleNumberField
                  v-model="sl.rowHeight"
                  label="行高"
                  kind="integer"
                  :min="1"
                  :precision="0"
                  :step="1"
                  @change="markDirty"
                />
                <RuleTextField
                  v-model="sl.dateRangeTextTemplate"
                  label="日期范围模板"
                  placeholder="{start} 至 {end}"
                  @change="markDirty"
                />
                <RuleTextField
                  v-model="sl.uppercaseTotalLabel"
                  label="大写金额标签"
                  placeholder="大写金额"
                  @change="markDirty"
                />
              </RuleFieldGrid>
            </RuleSectionBlock>

            <RuleSectionBlock title="模板配置" subtitle="按医院关键词匹配不同结款函样式">
              <RuleSettlementTemplateTable
                v-model="sl.templates"
                :available-templates="availableTemplates"
                @change="onSettlementTemplatesChange"
                @preview="previewSettlementTemplate"
              />
            </RuleSectionBlock>

            <RuleSectionBlock title="费用项配置" subtitle="结款函中列示的费用明细">
              <RuleFeeItemTable v-model="sl.feeItems" @change="markDirty" />
            </RuleSectionBlock>
          </RuleCategoryPanel>

          <RuleCategoryPanel
            v-if="activeCategory === '导出'"
            category="导出"
            title="导出选项"
            subtitle="账单、异常表与结款函的文件命名及页面选项"
            theme="export"
            badge="导出"
            tag="文件"
          >
            <RuleFieldGrid :columns="3">
              <RuleTextField v-model="eo.billFilePrefix" label="账单文件名前缀" placeholder="账单_" @change="markDirty" />
              <RuleTextField v-model="eo.warningFilePrefix" label="异常文件名前缀" placeholder="异常_" @change="markDirty" />
              <RuleTextField v-model="eo.settlementFilePrefix" label="结款函文件名前缀" placeholder="结款函_" @change="markDirty" />
              <RuleTextField v-model="eo.defaultPageMargin" label="默认页边距" placeholder="1cm" @change="markDirty" />
              <RuleSwitchField v-model="eo.includeWarningSheet" label="包含异常表" @change="markDirty" />
            </RuleFieldGrid>
          </RuleCategoryPanel>
      </div>
    </template>

    <ElDialog v-model="previewDialogVisible" title="结款函模板预览" width="800px" top="5vh">
      <div class="max-h-[70vh] overflow-auto rounded border bg-white p-4" v-html="sanitizeHtml(previewTemplateHtml)" />
      <template #footer>
        <ElButton @click="previewDialogVisible = false">关闭</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="needleImpactDialogVisible" title="小件识别影响预览" width="720px">
      <div v-if="needleImpactResult" class="space-y-3">
        <p class="text-sm text-gray-500">
          样本 {{ needleImpactResult.sampleCount }} 条，价格变化 {{ needleImpactResult.changedCount }} 条
        </p>
        <ElTable :data="needleImpactResult.diffs" size="small" max-height="360">
          <ElTableColumn prop="packName" label="包名" min-width="200" show-overflow-tooltip />
          <ElTableColumn label="生产单价" width="90">
            <template #default="{ row }">{{ row.productionUnitPrice ?? '-' }}</template>
          </ElTableColumn>
          <ElTableColumn label="草稿单价" width="90">
            <template #default="{ row }">{{ row.draftUnitPrice ?? '-' }}</template>
          </ElTableColumn>
          <ElTableColumn label="变化" width="70">
            <template #default="{ row }">
              <ElTag :type="row.changed ? 'warning' : 'success'" size="small">{{ row.changed ? '是' : '否' }}</ElTag>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
      <template #footer>
        <ElButton @click="needleImpactDialogVisible = false">关闭</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import {
  listHospitalPricingRules,
  createHospitalPricingRule,
  updateHospitalPricingRule,
  deleteHospitalPricingRule,
  listPricingRuleRevisions,
  rollbackPricingRuleRevision,
  batchUpdateNeedleKeywords,
  shadowComparePricing,
} from '@/api/hospital/pricingRulesApi'
import { validatePricingRules, exportRulesToJson, importRulesFromJson, createRulesFromDefaultTemplate } from '@/api/hospital/pricingRules'
import { isGeneralPricingRule } from '@/utils/pricingRuleScope'
import RuleCategoryPanel from '@/components/business/pricing-rules/RuleCategoryPanel.vue'
import RuleSectionBlock from '@/components/business/pricing-rules/RuleSectionBlock.vue'
import RuleFieldGrid from '@/components/business/pricing-rules/RuleFieldGrid.vue'
import RuleNumberField from '@/components/business/pricing-rules/RuleNumberField.vue'
import RuleKeywordField from '@/components/business/pricing-rules/RuleKeywordField.vue'
import RuleSwitchField from '@/components/business/pricing-rules/RuleSwitchField.vue'
import RuleTextField from '@/components/business/pricing-rules/RuleTextField.vue'
import RuleSelectField from '@/components/business/pricing-rules/RuleSelectField.vue'
import RuleBagSizeTable from '@/components/business/pricing-rules/RuleBagSizeTable.vue'
import RuleTierPriceTable from '@/components/business/pricing-rules/RuleTierPriceTable.vue'
import RulePackagingTable from '@/components/business/pricing-rules/RulePackagingTable.vue'
import RuleSettlementTemplateTable from '@/components/business/pricing-rules/RuleSettlementTemplateTable.vue'
import RuleFeeItemTable from '@/components/business/pricing-rules/RuleFeeItemTable.vue'
import RuleNeedleKeywordConfigTable from '@/components/business/pricing-rules/RuleNeedleKeywordConfigTable.vue'
import {
  listSettlementTemplates,
  type BackendTemplateRef,
} from '@/api/hospital/reconciliationsApi'

interface CategoryItem {
  key: string
  label: string
}

const categories: CategoryItem[] = [
  { key: '高温', label: '高温' },
  { key: '低温', label: '低温' },
  { key: '包装', label: '包装' },
  { key: '小件识别', label: '小件识别' },
  { key: '数据清洗', label: '数据清洗' },
  { key: '物流', label: '物流' },
  { key: '结款函', label: '结款函' },
  { key: '导出', label: '导出' },
]

const keywordMatchModeOptions = [
  { value: 'exact_token', label: '严格对齐（精确 token 边界）' },
  { value: 'contains', label: '含关键词即触发' },
]

const tabCategories = computed(() => categories)

defineOptions({ name: 'HospitalPricingRules' })

const route = useRoute()

const ruleList = ref<Api.Hospital.PricingRuleRecord[]>([])
const selectedRuleId = ref<number | null>(null)
const activeCategory = ref('高温')
const dirty = ref(false)
const jsonText = ref('')
const jsonEditEnabled = ref(false)
const jsonLocalDirty = ref(false)
const availableTemplates = ref<BackendTemplateRef[]>([])
const saving = ref(false)
const loading = ref(true)
const loadError = ref(false)

const defaultEmptyRules = (): Api.Hospital.PricingRules => ({
  version: '',
  updatedAt: undefined,
  highTemperature: {
    nonWoven: {
      minCharge: 16.5,
      flatPerPackagePrice: 5.5,
      flatRateThreshold: 3,
    },
    paperPlastic: {
      bagSizes: [
        { size: 25, price: 10.5, keywords: ['25cm', '25', '特大'] },
        { size: 20, price: 7.5, keywords: ['20cm', '20', '大'] },
        { size: 15, price: 5.5, keywords: ['15cm', '15', '中'] },
        { size: 10, price: 2.5, keywords: ['10cm', '10', '小'] },
      ],
      perPackagePrice: 5.5,
      minCharge: 16.5,
    },
  },
  lowTemperature: {
    nonWoven: {
      tierPrices: [
        { count: 20, price: 300 },
        { count: 10, price: 165 },
        { count: 5, price: 88 },
      ],
      remainderPerPiecePrice: 22,
      minSingleCharge: 35,
    },
    paperPlastic: {
      bagSizes: [
        { size: 30, price: 35, keywords: ['30cm', '30'] },
        { size: 25, price: 30, keywords: ['25cm', '25'] },
        { size: 20, price: 28, keywords: ['20cm', '20'] },
        { size: 15, price: 25, keywords: ['15cm', '15'] },
        { size: 10, price: 22, keywords: ['10cm', '10'] },
      ],
      tierPrices: [
        { count: 20, price: 300 },
        { count: 10, price: 165 },
        { count: 5, price: 88 },
      ],
    },
  },
  packaging: {
    enabled: true,
    selfPackedKeywords: ['仅灭菌', '医院自行打包', '自行打包', '自带包装'],
    items: [
      {
        name: '纱布棉球',
        keywords: ['纱布', '棉球', '辅料包'],
        chargePerPack: true,
        options: [
          { label: '大（20cm*20cm*15cm）', price: 2.5, keywords: ['20cm*20cm*15cm', '20cm×20cm×15cm'] },
          { label: '中（15cm*15cm*10cm）', price: 2, keywords: ['15cm*15cm*10cm', '15cm×15cm×10cm'] },
          { label: '小（10cm*10cm*5cm）', price: 1.5, keywords: ['10cm*10cm*5cm', '10cm×10cm×5cm', '10 cm及以下'] },
          { label: '20cm*20cm纸塑袋', price: 4, keywords: ['20cm*20cm', '20cm×20cm', '20*20'] },
          { label: '15cm*10cm纸塑袋', price: 2.5, keywords: ['15cm*10cm', '15cm×10cm', '15*10'] },
        ],
      },
      { name: 'rigip', keywords: ['rigip'], chargePerPack: true, options: [] },
      { name: '纸塑袋', keywords: ['纸塑袋'], chargePerPack: true, options: [] },
    ],
  },
  needle: { threshold: 5, foldRatio: 5, keywordMatchMode: 'exact_token', keywords: ['小件', '探针', '穿刺针', '缝合针', '车针', '拔髓针', '成型片', '根管针', '根管锉', '支抗钉', '洁牙机尖', '球钻', '挖勺'], keywordConfigs: [] },
  cleaning: {
    removeFirstRow: false,
    dropSummaryRows: true,
    summaryKeywords: ['合计', '小计', '总计'],
    trimPackagingMaterial: true,
    clearInstrumentColumnFormatting: false,
    recomputeTotalsWhenPriceChanges: true,
  },
  logistics: {
    enabled: true,
    feePerTrip: 50,
    defaultLogisticsFee: 50,
    dayBoundaryHour: 20,
    mergeAdjacentDays: false,
    mergeWindowDays: 1,
  },
  settlementLetter: {
    companyName: '',
    rowHeight: 20,
    dateRangeTextTemplate: '{start} 至 {end}',
    uppercaseTotalLabel: '大写金额',
    templates: [
      {
        id: 'default_template',
        name: '默认结款函模板',
        hospitalName: '',
        templateSheetName: '结款函',
        titleText: '货款结算单',
        matchKeywords: [],
        templateRef: 'default',
      },
    ],
    defaultTemplateId: 'default_template',
    feeItems: [
      { key: 'sterilize', label: '灭菌费', remark: '', enabled: true, sortOrder: 1 },
      { key: 'logistics', label: '物流费', remark: '', enabled: true, sortOrder: 2 },
    ],
  },
  exportOptions: {
    billFilePrefix: '账单_',
    warningFilePrefix: '异常_',
    settlementFilePrefix: '结款函_',
    includeWarningSheet: true,
    defaultPageMargin: '1cm',
  },
  specialRules: {
    fixedPrices: [],
    foldRules: [],
    extraFees: [],
  },
  customCategoryRules: undefined,
})

const formData = ref<{ name: string; description: string }>({
  name: '',
  description: '',
})

const currentRecord = computed(() =>
  ruleList.value.find((r) => r.id === selectedRuleId.value) ?? null,
)

const currentRule = computed(() => currentRecord.value)

const validationErrors = computed(() => {
  if (!currentRule.value) return []
  const result = validatePricingRules(currentRule.value.rules)
  return result.errors
})

const htnw = computed(() => currentRule.value!.rules.highTemperature.nonWoven)
const htpp = computed(() => currentRule.value!.rules.highTemperature.paperPlastic)
const ltnw = computed(() => currentRule.value!.rules.lowTemperature.nonWoven)
const ltpp = computed(() => currentRule.value!.rules.lowTemperature.paperPlastic)
const pg = computed(() => currentRule.value!.rules.packaging)
const nd = computed(() => currentRule.value!.rules.needle)
const needleMatchMode = computed({
  get: () => nd.value.keywordMatchMode ?? 'exact_token',
  set: (val: string) => {
    nd.value.keywordMatchMode = val === 'contains' ? 'contains' : 'exact_token'
    markDirty()
  },
})
const needleKeywordConfigs = computed<Api.Hospital.NeedleKeywordConfig[]>({
  get: () => nd.value.keywordConfigs ?? [],
  set: (val) => {
    nd.value.keywordConfigs = val
  },
})
const cl = computed(() => currentRule.value!.rules.cleaning)
const lg = computed(() => currentRule.value!.rules.logistics)
const sl = computed(() => currentRule.value!.rules.settlementLetter)
const eo = computed(() => currentRule.value!.rules.exportOptions)

const settlementTemplateOptions = computed(() =>
  sl.value.templates.map((item) => ({
    label: item.name || item.hospitalName || item.id,
    value: item.id,
  })),
)

function markDirty() {
  dirty.value = true
}

function onRuleNameInput() {
  if (!dirty.value) dirty.value = true
}

function onSettlementTemplatesChange() {
  const ids = sl.value.templates.map((t) => t.id)
  if (sl.value.defaultTemplateId && !ids.includes(sl.value.defaultTemplateId)) {
    sl.value.defaultTemplateId = sl.value.templates[0]?.id
  }
  if (!sl.value.defaultTemplateId && sl.value.templates.length) {
    sl.value.defaultTemplateId = sl.value.templates[0].id
  }
  markDirty()
}

function updateBagKeywords(target: Api.Hospital.BagSizeConfig[], index: number, val: string | number) {
  target[index].keywords = String(val).split(',').map((s) => s.trim()).filter(Boolean)
  markDirty()
}

function addBag(target: Api.Hospital.BagSizeConfig[]) {
  target.push({ size: 0, price: 0, keywords: [] })
  markDirty()
}

function removeBag(target: Api.Hospital.BagSizeConfig[], index: number) {
  target.splice(index, 1)
  markDirty()
}

function addTier(target: Api.Hospital.TierPriceConfig[]) {
  target.push({ count: 1, price: 0 })
  markDirty()
}

function removeTier(target: Api.Hospital.TierPriceConfig[], index: number) {
  target.splice(index, 1)
  markDirty()
}

function updatePackagingSelfPackedKeywords(val: string | number) {
  pg.value.selfPackedKeywords = String(val).split(',').map((s) => s.trim()).filter(Boolean)
  markDirty()
}

function updatePackagingItemKeywords(index: number, val: string | number) {
  pg.value.items[index].keywords = String(val).split(',').map((s) => s.trim()).filter(Boolean)
  markDirty()
}

function updatePackagingOptionKeywords(target: Api.Hospital.PackagingOptionConfig[], index: number, val: string | number) {
  target[index].keywords = String(val).split(',').map((s) => s.trim()).filter(Boolean)
  markDirty()
}

function addPackagingItem() {
  pg.value.items.push({ name: '', keywords: [], chargePerPack: true, options: [] })
  markDirty()
}

function removePackagingItem(index: number) {
  pg.value.items.splice(index, 1)
  markDirty()
}

function addPackagingOption(target: Api.Hospital.PackagingOptionConfig[]) {
  target.push({ label: '', price: 0, keywords: [] })
  markDirty()
}

function removePackagingOption(target: Api.Hospital.PackagingOptionConfig[], index: number) {
  target.splice(index, 1)
  markDirty()
}

function updateNeedleKeywords(val: string | number) {
  nd.value.keywords = String(val).replace(/，/g, ',').split(',').map((s) => s.trim()).filter(Boolean)
  markDirty()
}

function updateSummaryKeywords(val: string | number) {
  cl.value.summaryKeywords = String(val).split(',').map((s) => s.trim()).filter(Boolean)
  markDirty()
}

function addFeeItem() {
  const nextKey = `fee_${Date.now()}`
  const maxSort = sl.value.feeItems.reduce((max, f) => Math.max(max, f.sortOrder), 0)
  sl.value.feeItems.push({ key: nextKey, label: '', remark: '', enabled: true, sortOrder: maxSort + 1 })
  markDirty()
}

function removeFeeItem(index: number) {
  sl.value.feeItems.splice(index, 1)
  markDirty()
}

function buildSettlementTemplateId(seed: string): string {
  const normalized = seed.trim().toLowerCase().replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '_').replace(/^_+|_+$/g, '')
  return normalized || `template_${Date.now()}`
}

function addSettlementTemplate() {
  const nextIndex = sl.value.templates.length + 1
  sl.value.templates.push({
    id: buildSettlementTemplateId(`template_${nextIndex}_${Date.now()}`),
    name: `模板${nextIndex}`,
    hospitalName: '',
    templateSheetName: '结款函',
    titleText: '货款结算单',
    matchKeywords: [],
    templateRef: 'default',
  })
  if (!sl.value.defaultTemplateId) sl.value.defaultTemplateId = sl.value.templates[0]?.id
  markDirty()
}

const previewDialogVisible = ref(false)
const previewTemplateHtml = ref('')

interface RuleRevisionItem {
  id: number
  version: string
  createdAt: string
  createdBy?: string
}

const revisionList = ref<RuleRevisionItem[]>([])
const needleBatchSaving = ref(false)
const needleImpactLoading = ref(false)
const needleImpactDialogVisible = ref(false)
const needleImpactResult = ref<{
  sampleCount: number
  changedCount: number
  diffs: Array<Record<string, unknown>>
} | null>(null)

const NEEDLE_IMPACT_SAMPLES = [
  {
    hospitalName: '测试医院',
    type: '额外包(纸塑袋)',
    packName: '洁牙机尖-4/Z7526',
    packageMaterial: '高温纸塑袋75*200',
    instrumentCount: 4,
    packCount: 1,
  },
  {
    hospitalName: '测试医院',
    type: '额外包(纸塑袋)',
    packName: '挖勺-2/z7530',
    packageMaterial: '高温纸塑袋75*300',
    instrumentCount: 8,
    packCount: 4,
  },
  {
    hospitalName: '测试医院',
    type: '额外包(纸塑袋)',
    packName: '机扩针-20/Z7520',
    packageMaterial: '高温纸塑袋75*200',
    instrumentCount: 20,
    packCount: 1,
  },
]

function formatRevisionTime(value: string) {
  if (!value) return '-'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString('zh-CN')
}

async function loadRevisions(ruleId: number) {
  try {
    const rows = await listPricingRuleRevisions(ruleId)
    revisionList.value = (rows as Array<Record<string, unknown>>).map((r) => ({
      id: r.id as number,
      version: String(r.version ?? ''),
      createdAt: String(r.created_at ?? r.createdAt ?? ''),
      createdBy: (r.created_by ?? r.createdBy) as string | undefined,
    }))
  } catch {
    revisionList.value = []
  }
}

async function handleRevisionCommand(revisionId: number) {
  if (!selectedRuleId.value) return
  try {
    await ElMessageBox.confirm('回滚将用所选历史版本覆盖当前规则，是否继续？', '确认回滚', {
      confirmButtonText: '回滚',
      cancelButtonText: '取消',
      type: 'warning',
    })
    const operator = useUserStore().userInfo?.userName
    const updated = await rollbackPricingRuleRevision(selectedRuleId.value, revisionId, operator)
    const idx = ruleList.value.findIndex((r) => r.id === updated.id)
    if (idx >= 0) ruleList.value[idx] = updated
    selectRule(updated.id)
    await loadRevisions(updated.id)
    ElMessage.success('已回滚到历史版本')
  } catch (err) {
    if (err !== 'cancel') ElMessage.error('回滚失败')
  }
}

async function handleBatchSaveNeedleKeywords() {
  if (!selectedRuleId.value || !currentRule.value) return
  const configs = (nd.value.keywordConfigs ?? []).filter((c) => c.keyword.trim())
  if (!nd.value.keywords.length && !configs.length) {
    ElMessage.warning('请至少保留一个识别关键词或一条关键词独立配置')
    return
  }
  needleBatchSaving.value = true
  try {
    const operator = useUserStore().userInfo?.userName
    const updated = await batchUpdateNeedleKeywords(
      selectedRuleId.value,
      {
        keywords: [...nd.value.keywords],
        keywordConfigs: configs.map((c) => ({ ...c, keyword: c.keyword.trim() })),
        threshold: nd.value.threshold,
        foldRatio: nd.value.foldRatio,
        keywordMatchMode: needleMatchMode.value,
      },
      operator,
    )
    const idx = ruleList.value.findIndex((r) => r.id === updated.id)
    if (idx >= 0) ruleList.value[idx] = updated
    dirty.value = false
    jsonText.value = exportRulesToJson(updated.rules)
    await loadRevisions(updated.id)
    ElMessage.success('小件识别规则已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    needleBatchSaving.value = false
  }
}

async function handleNeedleImpactPreview() {
  if (!selectedRuleId.value || !currentRule.value) return
  needleImpactLoading.value = true
  try {
    const result = await shadowComparePricing({
      productionRuleId: selectedRuleId.value,
      draftRules: currentRule.value.rules,
      hospitalName: currentRule.value.hospitalName ?? '',
      sampleRows: NEEDLE_IMPACT_SAMPLES,
    }) as Record<string, unknown>
    needleImpactResult.value = {
      sampleCount: Number(result.sampleCount ?? 0),
      changedCount: Number(result.changedCount ?? 0),
      diffs: Array.isArray(result.diffs) ? (result.diffs as Array<Record<string, unknown>>) : [],
    }
    needleImpactDialogVisible.value = true
  } catch {
    ElMessage.error('影响预览失败')
  } finally {
    needleImpactLoading.value = false
  }
}

async function previewSettlementTemplate(index: number) {
  const template = sl.value.templates[index]
  const templateId = template.templateRef || 'default'
  try {
    const token = useUserStore().accessToken
    const response = await fetch(resolveApiRequestUrl(`/api/hospital-reconciliations/templates/settlement/${encodeURIComponent(templateId)}/preview`), {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    previewTemplateHtml.value = await response.text()
    previewDialogVisible.value = true
  } catch {
    previewTemplateHtml.value = `<p style="padding:40px;text-align:center;color:#999">无法加载模板预览，请确认后端模板文件存在。</p>`
    previewDialogVisible.value = true
  }
}

function resolveApiRequestUrl(url: string) {
  const baseURL = (import.meta.env.VITE_API_URL || '').trim()
  if (!baseURL || baseURL === '/') {
    return url
  }
  return new URL(url, `${baseURL.replace(/\/$/, '')}/`).toString()
}

function sanitizeHtml(html: string): string {
  return html
    .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '')
    .replace(/<script\b[^>]*\/>/gi, '')
    .replace(/\bon\w+\s*=\s*"[^"]*"/gi, '')
    .replace(/\bon\w+\s*=\s*'[^']*'/gi, '')
    .replace(/<iframe\b[^>]*>[\s\S]*?<\/iframe>/gi, '')
}

function removeSettlementTemplate(index: number) {
  const removed = sl.value.templates[index]
  sl.value.templates.splice(index, 1)
  if (removed?.id && sl.value.defaultTemplateId === removed.id) {
    sl.value.defaultTemplateId = sl.value.templates[0]?.id
  }
  markDirty()
}

function updateSettlementTemplateKeywords(index: number, val: string | number) {
  sl.value.templates[index].matchKeywords = String(val).split(',').map((s) => s.trim()).filter(Boolean)
  markDirty()
}

async function loadRules() {
  loading.value = true
  loadError.value = false
  try {
    const all = await listHospitalPricingRules()
    ruleList.value = all.filter(isGeneralPricingRule)
    if (ruleList.value.length) {
      const preferred = ruleList.value.find((r) => r.name?.includes('标准')) ?? ruleList.value[0]
      selectRule(preferred.id)
    } else {
      selectedRuleId.value = null
    }
  } catch {
    loadError.value = true
    if (ruleList.value.length === 0) {
      ElMessage.warning('无法连接到后端服务，请确认后端已启动')
    }
  } finally {
    loading.value = false
  }
}

function selectRule(id: number) {
  selectedRuleId.value = id
  dirty.value = false
  const record = ruleList.value.find((r) => r.id === id)
  if (record) {
    formData.value.name = record.name
    formData.value.description = record.description ?? ''
    jsonText.value = exportRulesToJson(record.rules)
  }
  void loadRevisions(id)
}

async function handleSave() {
  if (!currentRule.value) return
  saving.value = true
  try {
    const payload: Api.Hospital.SavePricingRulePayload = {
      name: formData.value.name,
      version: currentRule.value.rules.version,
      description: formData.value.description || undefined,
      rules: currentRule.value.rules,
    }
    const updated = await updateHospitalPricingRule(currentRule.value.id, payload)
    const idx = ruleList.value.findIndex((r) => r.id === updated.id)
    if (idx >= 0) ruleList.value[idx] = updated
    dirty.value = false
    jsonText.value = exportRulesToJson(updated.rules)
    await loadRevisions(updated.id)
    ElMessage.success('保存成功')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function handleCreate() {
  try {
    const rules = await createRulesFromDefaultTemplate()
    rules.version = 'v2.0'
    const payload: Api.Hospital.SavePricingRulePayload = {
      name: '标准灭菌计费规则',
      version: rules.version,
      description: '全行业通用灭菌计价规则',
      rules,
    }
    const created = await createHospitalPricingRule(payload)
    ruleList.value.push(created)
    selectRule(created.id)
    ElMessage.success('已创建通用计价规则')
  } catch {
    ElMessage.error('创建失败')
  }
}

async function handleDelete() {
  if (!currentRule.value) return
  try {
    await ElMessageBox.confirm('确定要删除此方案吗？此操作不可恢复。', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteHospitalPricingRule(currentRule.value.id)
    ruleList.value = ruleList.value.filter((r) => r.id !== currentRule.value!.id)
    if (ruleList.value.length) {
      selectRule(ruleList.value[0].id)
    } else {
      selectedRuleId.value = null
    }
    ElMessage.success('删除成功')
  } catch {
    // cancelled or error
  }
}


function syncJsonFromCurrentRule() {
  if (currentRule.value) {
    jsonText.value = exportRulesToJson(currentRule.value.rules)
  }
  jsonLocalDirty.value = false
}

function onJsonTextInput() {
  if (jsonEditEnabled.value) {
    jsonLocalDirty.value = true
  }
}

async function beforeJsonEditToggle(nextEnabled: boolean) {
  if (nextEnabled) {
    try {
      await ElMessageBox.confirm(
        '直接编辑 JSON 属于危险操作，可能破坏规则结构、导致校验失败或对账结果异常。仅在明确知晓后果时使用。是否启用编辑？',
        '危险操作确认',
        {
          confirmButtonText: '我已知悉，启用编辑',
          cancelButtonText: '取消',
          type: 'warning',
          distinguishCancelAndClose: true,
        },
      )
      return true
    } catch {
      return false
    }
  }

  if (jsonLocalDirty.value) {
    try {
      await ElMessageBox.confirm(
        '关闭编辑将丢弃文本框内未导入的修改，并恢复为当前规则内容。是否继续？',
        '放弃未保存修改',
        {
          confirmButtonText: '放弃修改',
          cancelButtonText: '继续编辑',
          type: 'warning',
        },
      )
      syncJsonFromCurrentRule()
      return true
    } catch {
      return false
    }
  }

  return true
}

function handleCopyJson() {
  navigator.clipboard.writeText(jsonText.value).then(
    () => ElMessage.success('已复制到剪贴板'),
    () => ElMessage.error('复制失败'),
  )
}

function handleImportJson() {
  if (!jsonEditEnabled.value) return
  const result = importRulesFromJson(jsonText.value)
  if (result.success && result.rules && currentRule.value) {
    currentRule.value.rules = result.rules
    jsonText.value = exportRulesToJson(result.rules)
    jsonLocalDirty.value = false
    dirty.value = true
    ElMessage.success('导入成功')
  } else {
    ElMessage.error(result.error ?? '导入失败')
  }
}

watch(activeCategory, (category, prev) => {
  if (category === 'json') {
    jsonEditEnabled.value = false
    syncJsonFromCurrentRule()
  } else if (prev === 'json') {
    jsonEditEnabled.value = false
    jsonLocalDirty.value = false
  }
})

watch(selectedRuleId, (id) => {
  if (id == null) return
  const record = ruleList.value.find((r) => r.id === id)
  if (record) {
    formData.value.name = record.name
    formData.value.description = record.description ?? ''
    jsonText.value = exportRulesToJson(record.rules)
  }
})

watch(() => route.path, async (newPath, oldPath) => {
  if (newPath === '/settings/pricing-rules' || newPath === '/hospital/pricing-rules') {
    await loadRules()
    try {
      availableTemplates.value = await listSettlementTemplates()
    } catch {
      // templates unavailable, selector will be empty
    }
  }
}, { immediate: false })

onMounted(async () => {
  await loadRules()
  try {
    availableTemplates.value = await listSettlementTemplates()
  } catch {
    // templates unavailable, selector will be empty
  }
})
</script>

<style scoped>
.pricing-rules-page {
  min-height: 0;
  max-width: 1280px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.page-desc {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.link-customers {
  color: var(--el-color-primary);
  text-decoration: none;
}

.link-customers:hover {
  text-decoration: underline;
}

.page-header__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.dirty-dot {
  margin-left: 4px;
  color: #f59e0b;
}

.state-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 16px;
  color: var(--el-text-color-secondary);
}

.state-title {
  margin: 0 0 8px;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.state-desc {
  margin: 0 0 16px;
  font-size: 13px;
}

.meta-card :deep(.el-card__body) {
  padding: 16px 20px 4px;
}

.rule-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.rule-panel {
  margin-top: 12px;
}

.needle-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-extra-light);
}

.needle-keywords-field {
  margin-top: 20px;
}

.needle-keyword-configs {
  margin-top: 16px;
}

.needle-actions__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.json-advanced-card__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.json-advanced-card__title {
  display: block;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.json-advanced-card__desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.json-advanced-card__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.json-advanced-card__switch {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 4px;
  padding-right: 12px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.json-advanced-card__switch-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.json-advanced-card__alert {
  margin-bottom: 12px;
}

.json-advanced-card__textarea--readonly :deep(.el-textarea__inner) {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  cursor: default;
}
</style>
