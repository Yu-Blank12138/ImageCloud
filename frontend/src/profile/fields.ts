import { localCurrentMonth } from '../utils/time'

export interface ProfileFields {
  gender?: 0 | 1 | 2 | null
  bio?: string | null
  email?: string | null
  phoneNumber?: string | null
  birthMonth?: string | null
}

export function profileDraft(value: ProfileFields = {}) {
  return { gender: value.gender ?? '' as const, bio: value.bio ?? '', email: value.email ?? '', phoneNumber: value.phoneNumber ?? '', birthMonth: value.birthMonth ?? '' }
}

// 历史 null/缺省仅用于回填，不把未改动字段误写为空字符串或性别 0。
export function profileChanges(before: ProfileFields, draft: ReturnType<typeof profileDraft>): ProfileFields {
  const result: ProfileFields = {}
  if (draft.gender !== (before.gender ?? '') && draft.gender !== '') result.gender = draft.gender
  for (const key of ['bio', 'email', 'phoneNumber', 'birthMonth'] as const) {
    if (draft[key] !== (before[key] ?? '')) result[key] = draft[key].trim()
  }
  return result
}

// 邮箱须为本地部分@域名且域名带有效后缀，防止任意文本通过校验提交到后端。
const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/
// 手机号与后端 PHONE_NUMBER_PATTERN 一致：6–20 位数字，可带 + 前缀。
const PHONE_PATTERN = /^\+?\d{6,20}$/

/** 校验邮箱格式，空值表示清空或未修改，返回错误提示或空字符串。 */
export function emailError(value: string): string {
  if (!value) return ''
  if (value.length > 254) return '邮箱最多 254 个字符。'
  if (!EMAIL_PATTERN.test(value)) return '请输入有效的邮箱地址，例如 name@example.com。'
  return ''
}

/** 校验手机号格式，空值表示清空或未修改，返回错误提示或空字符串。 */
export function phoneError(value: string): string {
  if (!value) return ''
  if (!PHONE_PATTERN.test(value)) return '手机号须为 6–20 位数字，可使用 + 前缀。'
  return ''
}

export function validateProfile(fields: ProfileFields): string {
  if (fields.gender != null && ![0, 1, 2].includes(fields.gender)) return '请选择有效的性别。'
  if (fields.bio && fields.bio.length > 128) return '个人简介不能超过 128 个字符。'
  // 邮箱和手机号复用单字段校验，保证表单即时提示与提交校验规则一致。
  if (fields.email) { const message = emailError(fields.email); if (message) return message }
  if (fields.phoneNumber) { const message = phoneError(fields.phoneNumber); if (message) return message }
  if (fields.birthMonth && (!/^\d{4}-(0[1-9]|1[0-2])$/.test(fields.birthMonth) || fields.birthMonth > localCurrentMonth())) return '出生年月须为 YYYY-MM，且不能晚于 本地当前年月。'
  return ''
}
