export const browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone

export function toLocalDateTime(value: string): string | null {
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return null

  // 日期和偏移按浏览器时区计算，秒小数沿用接口原文，避免丢失精度。
  const fraction = /\.(\d+)(?:Z|[+-]\d{2}:\d{2})$/.exec(value)?.[1]
  const year = String(date.getFullYear()).padStart(4, '0')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return [year, month, day].join('-') + 'T' + [hours, minutes, seconds].join(':') +
    (fraction ? '.' + fraction : '')
}

export function toUtcDateTime(value: string): string | null {
  const normalized = value.replace(' ', 'T')
  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?$/.exec(normalized)
  if (!match) return null
  const localSeconds = match[1]!
  const date = new Date(localSeconds)
  if (!Number.isFinite(date.getTime())) return null

  // 回转检查拒绝不存在的日期及夏令时跳过的本地时间。
  const utcSeconds = date.toISOString().slice(0, 19)
  if (toLocalDateTime(utcSeconds + 'Z') !== localSeconds) return null
  return utcSeconds + (match[2] ? '.' + match[2] : '') + 'Z'
}
