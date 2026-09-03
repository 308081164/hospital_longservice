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

export function fetchSystemVersion() {
  return request.get<SystemVersionInfo>({
    url: '/api/v1/base/version',
    showErrorMessage: false
  })
}
