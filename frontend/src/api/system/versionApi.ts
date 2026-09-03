import request from '@/utils/http'

export interface SystemVersionInfo {
  gitSha: string
  gitShaShort: string
  buildTime: string
  buildTimeDisplay: string
  rulesManifestHash: string
  rulesManifestHashShort: string
  rulesGeneratedAt: string
  rulesGeneratedAtDisplay: string
  rulesReconciledAt: string
  rulesReconciledAtDisplay: string
  version: string
}

/** 新版 /version 必含 gitShaShort；旧版仅 { version: "1.0.0", ... } */
export function isSystemVersionInfoComplete(data: unknown): data is SystemVersionInfo {
  if (!data || typeof data !== 'object') return false
  const d = data as Record<string, unknown>
  return typeof d.gitShaShort === 'string' && d.gitShaShort.length > 0
}

export function fetchSystemVersion() {
  return request.get<SystemVersionInfo>({
    url: '/api/v1/base/version',
    showErrorMessage: false
  })
}
