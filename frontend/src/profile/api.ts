import { bearerOptions, requestJson, requireData } from '../api/http'

import type { ProfileFields } from './fields'

export interface AvatarUpload {
  avatarUrl: string
  avatarOriginalUrl: string
}

// 登录账号不可修改，更新载荷不含 account。
export interface ProfileUpdate extends ProfileFields {
  displayName?: string
  avatarUrl?: string
  avatarOriginalUrl?: string
}

const errors = {
  4000: '请求格式有误，请重试。',
  4001: '请检查填写的内容是否符合要求。',
  4011: '原密码错误，请重新输入。',
  4030: '当前账号无法执行此操作。',
  4130: '图片不能超过 20MB。',
  4150: '图片格式不受支持，请更换文件。',
  5000: '服务暂时无法处理请求，请稍后重试。',
  5030: '服务暂时不可用，请稍后重试。',
}

async function update(path: string, fields: object, token: string, signal: AbortSignal) {
  const options = bearerOptions(token, signal)
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  // 本人身份由会话确定，成功响应只要求业务码，无需 data。
  await requestJson<void>(path, { ...options, headers, method: 'PATCH', body: JSON.stringify(fields) }, errors)
}

export function updateProfile(fields: ProfileUpdate, token: string, signal: AbortSignal) {
  return update('/api/users/me', fields, token, signal)
}

export function updatePassword(fields: { oldPassword: string; newPassword: string }, token: string, signal: AbortSignal) {
  return update('/api/users/me/password', fields, token, signal)
}

export async function uploadPreview(file: File, token: string, signal: AbortSignal) {
  const form = new FormData()
  form.append('file', file)
  // multipart boundary 交给浏览器生成，上传返回公开 URL，后续确认保存时再关联账号。
  const result = requireData(await requestJson<AvatarUpload>('/api/images/avatars', {
    ...bearerOptions(token, signal), method: 'POST', body: form,
  }, errors))
  requireImageUrl(result.avatarUrl)
  requireImageUrl(result.avatarOriginalUrl)
  return result
}

function requireImageUrl(url: string) {
  try {
    if (new URL(url).protocol === 'https:') return url
  } catch { /* 统一处理缺少或格式无效的图片地址。 */ }
  throw new Error('未返回可用的头像地址，请重试。')
}

export async function fetchAvatarOriginal(id: string, token: string, signal: AbortSignal) {
  const url = requireData(await requestJson<string>(
    `/api/images/avatars/${id}/original`, bearerOptions(token, signal),
    { ...errors, 4040: '该头像暂无可查看的原图，可能是历史头像。' },
  ))
  return requireImageUrl(url)
}

export async function fetchAvatar(id: string, token: string) {
  const data = requireData(await requestJson<{ avatarUrl: string | null }>(
    '/api/users/' + id, bearerOptions(token), errors,
  ))
  return data.avatarUrl || null
}

export interface PublicProfile extends Omit<ProfileFields, 'phoneNumber'> {
  /** 登录账号，用于在昵称下方展示并区分同名用户。 */
  account: string
  /** 公开身份：USER 普通用户、VIP 会员、ADMIN 平台管理员；VIP 到期后为 USER。 */
  platformRole: 'USER' | 'VIP' | 'ADMIN'
  displayName?: string | null
  avatarUrl?: string | null
}

// 昵称搜索候选项，账号用于区分同名用户。
export interface PublicUserSummary {
  /** 用户主键，接口以字符串返回以避免雪花 ID 精度丢失。 */
  id: string
  account: string
  displayName?: string | null
  avatarUrl?: string | null
  /** 最近三张审核通过图片的压缩图地址，没有可用图片时为空数组。 */
  previewImageUrls: string[]
}

export interface PublicUserSearchPage {
  records: PublicUserSummary[]
  total: number
  page: number
  pageSize: number
}

// 按昵称分页搜索正常账号，昵称与分页条件通过 JSON 请求体提交。
export async function searchPublicUsers(
  query: { displayName: string; page: number; pageSize: number },
  token: string,
  signal: AbortSignal,
) {
  const options = bearerOptions(token, signal)
  return requireData(await requestJson<PublicUserSearchPage>('/api/users/search', {
    ...options,
    method: 'POST',
    headers: { ...(options.headers as Record<string, string>), 'Content-Type': 'application/json' },
    body: JSON.stringify(query),
  }, errors))
}

// 标记目标用户不存在的错误类型，便于调用方弹出对应提示。
export class UserNotFoundError extends Error {
  constructor() { super('未找到该用户。') }
}

export async function fetchPublicProfile(id: string, token: string, signal: AbortSignal): Promise<PublicProfile> {
  let data: PublicProfile
  try {
    data = requireData(await requestJson<PublicProfile>(`/api/users/${id}`, bearerOptions(token, signal), { ...errors, 4040: '未找到该用户。' }))
  } catch (failure) {
    if (failure instanceof Error && failure.message === '未找到该用户。') throw new UserNotFoundError()
    throw failure
  }
  // 公开资料只取允许展示的字段，即使服务端意外夹带手机号也不保留。
  return { account: data.account, platformRole: data.platformRole, displayName: data.displayName, avatarUrl: data.avatarUrl, gender: data.gender, bio: data.bio, email: data.email, birthMonth: data.birthMonth }
}
