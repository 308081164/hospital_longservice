/**
 * 系统版本升级管理模块
 *
 * 提供完整的应用版本升级检测和处理功能
 *
 * ## 主要功能
 *
 * - 版本号比较和升级检测
 * - 首次访问识别和处理
 * - 旧版本数据自动清理
 * - 升级日志展示和通知
 * - 强制重新登录控制（根据升级日志配置）
 * - 版本号规范化处理
 * - 旧存储结构迁移和清理
 * - 升级流程延迟执行（确保应用完全加载）
 *
 * ## 使用场景
 *
 * - 应用启动时自动检测版本升级
 * - 版本更新后清理旧数据
 * - 向用户展示版本更新内容
 * - 重大更新时要求用户重新登录
 * - 防止旧版本数据污染新版本
 *
 * ## 工作流程
 *
 * 1. 检查本地存储的版本号
 * 2. 与当前应用版本对比
 * 3. 查找并清理旧版本数据
 * 4. 展示升级通知（包含更新日志）
 * 5. 根据配置决定是否强制重新登录
 * 6. 更新本地版本号
 *
 * @module utils/sys/upgrade
 * @author Art Design Pro Team
 */
import { upgradeLogList } from '@/mock/upgrade/changeLog'
import { ElNotification } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { StorageConfig } from '@/utils/storage/storage-config'

/**
 * 版本管理器
 * 负责处理版本比较、升级检测和数据清理
 */
class VersionManager {
  /**
   * 规范化版本号字符串，移除前缀 'v'
   */
  private normalizeVersion(version: string): string {
    return version.replace(/^v/, '')
  }

  /**
   * 获取存储的版本号
   */
  private getStoredVersion(): string | null {
    return localStorage.getItem(StorageConfig.VERSION_KEY)
  }

  /**
   * 设置版本号到存储
   */
  private setStoredVersion(version: string): void {
    localStorage.setItem(StorageConfig.VERSION_KEY, version)
  }

  /**
   * 检查是否应该跳过升级处理
   */
  private shouldSkipUpgrade(): boolean {
    return StorageConfig.CURRENT_VERSION === StorageConfig.SKIP_UPGRADE_VERSION
  }

  /**
   * 检查是否为首次访问
   */
  private isFirstVisit(storedVersion: string | null): boolean {
    return !storedVersion
  }

  /**
   * 检查版本是否相同
   */
  private isSameVersion(storedVersion: string): boolean {
    return storedVersion === StorageConfig.CURRENT_VERSION
  }

  /**
   * 查找旧的存储结构
   */
  private findLegacyStorage(): { oldSysKey: string | null; oldVersionKeys: string[] } {
    const storageKeys = Object.keys(localStorage)
    const currentVersionPrefix = StorageConfig.generateStorageKey('').slice(0, -1) // 移除末尾的 '-'

    // 查找旧的单一存储结构
    const oldSysKey =
      storageKeys.find(
        (key) =>
          StorageConfig.isVersionedKey(key) && key !== currentVersionPrefix && !key.includes('-')
      ) || null

    // 查找旧版本的分离存储键
    const oldVersionKeys = storageKeys.filter(
      (key) =>
        StorageConfig.isVersionedKey(key) &&
        !StorageConfig.isCurrentVersionKey(key) &&
        key.includes('-')
    )

    return { oldSysKey, oldVersionKeys }
  }

  /**
   * 检查是否需要重新登录
   *
   * 仅对语义化版本号（如 3.0.2）生效；生产部署版本为 git SHA 或
   * docker-时间戳构建号，与 mock 日志的语义化版本做字符串比较没有
   * 意义且可能误判，直接跳过（部署强制刷新由 versionEnforcer 负责）。
   */
  private shouldRequireReLogin(storedVersion: string): boolean {
    const semverLike = /^\d+\.\d+(\.\d+)*$/
    const normalizedCurrent = this.normalizeVersion(StorageConfig.CURRENT_VERSION)
    const normalizedStored = this.normalizeVersion(storedVersion)

    if (!semverLike.test(normalizedCurrent) || !semverLike.test(normalizedStored)) {
      return false
    }

    return upgradeLogList.value.some((item) => {
      const itemVersion = this.normalizeVersion(item.version)
      return (
        item.requireReLogin && itemVersion > normalizedStored && itemVersion <= normalizedCurrent
      )
    })
  }

  /**
   * 解析可读的升级时间
   *
   * 优先级：版本号内嵌时间戳（docker-YYYYMMDDHHmmss 本地构建）→
   * 版本号命中升级日志 → 运行时真实构建时间（/version.json →
   * /api/v1/base/version）。查不到就返回 null，绝不回退到
   * 模板 mock 日志的日期（历史事故：公告长期显示 2026-03-15）。
   */
  private async resolveUpgradeTime(version: string): Promise<string | null> {
    const embeddedTimestamp = version.match(/(\d{14})\b/)
    if (embeddedTimestamp) {
      const stamp = embeddedTimestamp[1]
      return `${stamp.slice(0, 4)}-${stamp.slice(4, 6)}-${stamp.slice(6, 8)} ${stamp.slice(8, 10)}:${stamp.slice(10, 12)}:${stamp.slice(12, 14)}`
    }

    const normalizedCurrent = this.normalizeVersion(version)
    const matchedLog = upgradeLogList.value.find(
      (item) => this.normalizeVersion(item.version) === normalizedCurrent
    )
    if (matchedLog?.date) {
      return matchedLog.date
    }

    return this.fetchRealBuildTime()
  }

  /**
   * 运行时获取真实构建时间：优先前端构建指纹 /version.json（与
   * __APP_VERSION__ 同源、nginx no-cache、未登录可访问），回退后端
   * /api/v1/base/version（permitAll）。均失败返回 null。
   */
  private async fetchRealBuildTime(): Promise<string | null> {
    try {
      const resp = await fetch(`/version.json?_t=${Date.now()}`, { cache: 'no-store' })
      if (resp.ok) {
        const body = await resp.json()
        const formatted = this.formatBuildTime(body?.buildTime)
        if (formatted) return formatted
      }
    } catch {
      // dev 环境无 version.json（vite 回退返回 HTML）→ 尝试后端接口
    }

    try {
      const resp = await fetch('/api/v1/base/version', { cache: 'no-store' })
      if (resp.ok) {
        const body = await resp.json()
        const display = body?.data?.buildTimeDisplay
        if (typeof display === 'string' && display) return display
        const formatted = this.formatBuildTime(body?.data?.buildTime)
        if (formatted) return formatted
      }
    } catch {
      // 后端不可达 → 放弃展示升级时间
    }

    return null
  }

  /** ISO 时间统一格式化为 Asia/Shanghai 的 YYYY-MM-DD HH:mm */
  private formatBuildTime(raw: unknown): string | null {
    if (typeof raw !== 'string' || !raw) return null
    const date = new Date(raw)
    if (Number.isNaN(date.getTime())) return null
    return new Intl.DateTimeFormat('zh-CN', {
      timeZone: 'Asia/Shanghai',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    })
      .format(date)
      .replace(/\//g, '-')
  }

  /** 版本号展示：git SHA 取短 sha，其余原样 */
  private displayVersion(version: string): string {
    return /^[0-9a-f]{40}$/i.test(version) ? version.slice(0, 8) : version
  }

  /**
   * 构建升级通知消息
   */
  private async buildUpgradeMessage(requireReLogin: boolean): Promise<string> {
    const normalizedCurrent = this.normalizeVersion(StorageConfig.CURRENT_VERSION)
    const matchedLog = upgradeLogList.value.find(
      (item) => this.normalizeVersion(item.version) === normalizedCurrent
    )
    // 仅在版本号命中升级日志时引用其文案；git SHA 部署永远命中不了
    // 模板 mock 日志，使用准确的中性描述，不再展示与实际变更无关的文案
    const content =
      matchedLog?.title ?? '本次更新包含功能改进与问题修复，如有疑问请联系系统管理员。'
    const upgradeTime = await this.resolveUpgradeTime(StorageConfig.CURRENT_VERSION)

    const messageParts = [
      `<p style="color: var(--art-gray-800) !important; padding-bottom: 5px;">`,
      `系统已升级到 ${this.displayVersion(StorageConfig.CURRENT_VERSION)} 版本，此次更新带来了以下改进：`,
      `</p>`
    ]

    if (upgradeTime) {
      messageParts.push(
        `<p style="color: var(--art-gray-600) !important; padding-bottom: 5px; font-size: 13px;">`,
        `升级时间：${upgradeTime}`,
        `</p>`
      )
    }

    messageParts.push(content)

    if (requireReLogin) {
      messageParts.push(
        `<p style="color: var(--theme-color); padding-top: 5px;">升级完成，请重新登录后继续使用。</p>`
      )
    }

    return messageParts.join('')
  }

  /**
   * 显示升级通知
   */
  private showUpgradeNotification(message: string): void {
    ElNotification({
      title: '系统升级公告',
      message,
      duration: 0,
      type: 'success',
      dangerouslyUseHTMLString: true
    })
  }

  /**
   * 清理旧版本数据
   */
  private cleanupLegacyData(oldSysKey: string | null, oldVersionKeys: string[]): void {
    // 清理旧的单一存储结构
    if (oldSysKey) {
      localStorage.removeItem(oldSysKey)
      console.info(`[Upgrade] 已清理旧存储: ${oldSysKey}`)
    }

    // 清理旧版本的分离存储
    oldVersionKeys.forEach((key) => {
      localStorage.removeItem(key)
      console.info(`[Upgrade] 已清理旧存储: ${key}`)
    })
  }

  /**
   * 执行升级后的登出操作
   */
  private performLogout(): void {
    try {
      useUserStore().logOut()
      console.info('[Upgrade] 已执行升级后登出')
    } catch (error) {
      console.error('[Upgrade] 升级后登出失败:', error)
    }
  }

  /**
   * 执行升级流程
   */
  private async executeUpgrade(
    storedVersion: string,
    legacyStorage: ReturnType<typeof this.findLegacyStorage>
  ): Promise<void> {
    try {
      if (!upgradeLogList.value.length) {
        console.warn('[Upgrade] 升级日志列表为空')
        return
      }

      const requireReLogin = this.shouldRequireReLogin(storedVersion)
      const message = await this.buildUpgradeMessage(requireReLogin)

      // 显示升级通知
      this.showUpgradeNotification(message)

      // 更新版本号
      this.setStoredVersion(StorageConfig.CURRENT_VERSION)

      // 清理旧数据
      this.cleanupLegacyData(legacyStorage.oldSysKey, legacyStorage.oldVersionKeys)

      // 执行登出（如果需要）
      if (requireReLogin) {
        this.performLogout()
      }

      console.info(`[Upgrade] 升级完成: ${storedVersion} → ${StorageConfig.CURRENT_VERSION}`)
    } catch (error) {
      console.error('[Upgrade] 系统升级处理失败:', error)
    }
  }

  /**
   * 系统升级处理主流程
   */
  async processUpgrade(): Promise<void> {
    // 跳过特定版本
    if (this.shouldSkipUpgrade()) {
      console.debug('[Upgrade] 跳过版本升级检查')
      return
    }

    const storedVersion = this.getStoredVersion()

    // 首次访问处理
    if (this.isFirstVisit(storedVersion)) {
      this.setStoredVersion(StorageConfig.CURRENT_VERSION)
      // console.info('[Upgrade] 首次访问，已设置当前版本')
      return
    }

    // 版本相同，无需升级
    if (this.isSameVersion(storedVersion!)) {
      // console.debug('[Upgrade] 版本相同，无需升级')
      return
    }

    // 检查是否有需要升级的旧数据
    const legacyStorage = this.findLegacyStorage()
    if (!legacyStorage.oldSysKey && legacyStorage.oldVersionKeys.length === 0) {
      this.setStoredVersion(StorageConfig.CURRENT_VERSION)
      console.info('[Upgrade] 无旧数据，已更新版本号')
      return
    }

    // 延迟执行升级流程，确保应用已完全加载
    setTimeout(() => {
      this.executeUpgrade(storedVersion!, legacyStorage)
    }, StorageConfig.UPGRADE_DELAY)
  }
}

// 创建版本管理器实例
const versionManager = new VersionManager()

/**
 * 系统升级处理入口函数
 */
export async function systemUpgrade(): Promise<void> {
  await versionManager.processUpgrade()
}
