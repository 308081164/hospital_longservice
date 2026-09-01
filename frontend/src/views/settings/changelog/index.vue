<template>
  <div class="changelog-page p-6">
    <div class="changelog-page__header">
      <h2 class="changelog-page__title">{{ t('menus.settings.changelog') }}</h2>
      <p class="changelog-page__subtitle">{{ t('settings.changelog.subtitle') }}</p>
    </div>

    <ElTimeline>
      <ElTimelineItem
        v-for="item in upgradeLogList"
        :key="`${item.version}-${item.date}`"
        :timestamp="item.date"
        placement="top"
      >
        <ElCard shadow="never" class="changelog-card">
          <div class="changelog-card__head">
            <span class="changelog-card__version">{{ item.version }}</span>
            <span class="changelog-card__title">{{ item.title }}</span>
            <ElTag v-if="item.requireReLogin" size="small" type="warning" effect="plain">
              {{ t('settings.changelog.requireReLogin') }}
            </ElTag>
          </div>
          <ul v-if="item.detail?.length" class="changelog-card__list">
            <li v-for="(line, index) in item.detail" :key="index">{{ line }}</li>
          </ul>
          <p v-if="item.remark" class="changelog-card__remark">{{ item.remark }}</p>
        </ElCard>
      </ElTimelineItem>
    </ElTimeline>
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'
  import { upgradeLogList } from '@/mock/upgrade/changeLog'

  defineOptions({ name: 'SettingsChangelog' })

  const { t } = useI18n()
</script>

<style scoped>
  .changelog-page__header {
    margin-bottom: 20px;
  }

  .changelog-page__title {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: var(--el-text-color-primary, #303133);
  }

  .changelog-page__subtitle {
    margin: 4px 0 0;
    font-size: 13px;
    color: var(--el-text-color-secondary, #909399);
  }

  .changelog-card {
    border: 1px solid var(--el-border-color-lighter, #ebeef5);
  }

  .changelog-card__head {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    margin-bottom: 8px;
  }

  .changelog-card__version {
    font-size: 12px;
    font-weight: 600;
    color: var(--el-color-primary);
  }

  .changelog-card__title {
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary, #303133);
  }

  .changelog-card__list {
    margin: 0;
    padding-left: 18px;
    font-size: 13px;
    line-height: 1.6;
    color: var(--el-text-color-regular, #606266);
  }

  .changelog-card__remark {
    margin: 8px 0 0;
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-color-warning);
  }
</style>
