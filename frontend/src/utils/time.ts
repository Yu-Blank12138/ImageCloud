// 业务时间是无时区的日历值，不通过 Date 解析，避免偏移和夏令时改变历史值。
export function normalizeBusinessDateTime(value: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(\.\d{1,9})?)?$/.exec(value.trim())
  if (!match) return null
  const [, year, month, day, hour, minute, second = '00', fraction = ''] = match
  const y = Number(year), m = Number(month), d = Number(day)
  const leap = y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0)
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (m < 1 || m > 12 || d < 1 || d > days[m - 1]! || Number(hour) > 23 || Number(minute) > 59 || Number(second) > 59) return null
  return `${year}-${month}-${day}T${hour}:${minute}:${second}${fraction}`
}

// 比较时仅忽略小数尾零，提交仍保留原始精度。
export function businessTimeKey(value: string): string | null {
  const normalized = normalizeBusinessDateTime(value)
  if (!normalized) return null
  return normalized.replace(/\.(\d*?)0+$/, (_, fraction: string) => fraction ? '.' + fraction : '')
}

// VIP 到期日期只到日，校验 YYYY-MM-DD 的日历有效性后按原格式返回。
export function normalizeBusinessDate(value: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value.trim())
  if (!match) return null
  const [, year, month, day] = match
  const y = Number(year), m = Number(month), d = Number(day)
  const leap = y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0)
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (m < 1 || m > 12 || d < 1 || d > days[m - 1]!) return null
  return `${year}-${month}-${day}`
}

export function localCurrentMonth(): string {
  const now = new Date()
  return String(now.getFullYear()).padStart(4, '0') + '-' + String(now.getMonth() + 1).padStart(2, '0')
}
