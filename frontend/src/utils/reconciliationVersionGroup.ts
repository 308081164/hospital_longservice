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
