const UNNAMED_HOSPITAL = '(未命名)'
const UNNAMED_FILE = '(未命名)'
const SEPARATOR = '::'

export function normalizeHospitalName(hospitalName?: string | null): string {
  const trimmed = hospitalName?.trim()
  return trimmed || UNNAMED_HOSPITAL
}

export function normalizeSourceFileName(sourceFileName?: string | null): string {
  const trimmed = sourceFileName?.trim()
  if (!trimmed) return UNNAMED_FILE
  const slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
  return slash >= 0 ? trimmed.slice(slash + 1) : trimmed
}

/** 同一医院 + 同一源文件构成独立版本链。 */
export function buildReconciliationVersionGroupKey(
  hospitalName?: string | null,
  sourceFileName?: string | null
): string {
  return `${normalizeHospitalName(hospitalName)}${SEPARATOR}${normalizeSourceFileName(sourceFileName)}`
}

type VersionLike = {
  createdAt?: string | null
  versionNo?: number | null
}

/** 版本组按最新导入/创建时间倒序，便于历史列表把刚保存的账单置顶。 */
export function compareReconciliationGroupsByLatestActivity<
  T extends { versions: VersionLike[] }
>(a: T, b: T): number {
  const latestA = a.versions[0]
  const latestB = b.versions[0]
  if (!latestA || !latestB) return 0

  const timeDiff =
    new Date(latestB.createdAt ?? 0).getTime() - new Date(latestA.createdAt ?? 0).getTime()
  if (timeDiff !== 0) return timeDiff

  return (latestB.versionNo ?? 0) - (latestA.versionNo ?? 0)
}
