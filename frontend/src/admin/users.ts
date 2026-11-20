import { bearerOptions, requestJson, requireData } from '../api/http'
import type { LoginResult } from '../auth/api'

import type { ProfileFields } from '../profile/fields'

export interface UserFields extends ProfileFields {
  account: string
  displayName: string | null
  platformRole: LoginResult['user']['platformRole']
  status: 'ACTIVE' | 'DISABLED'
  vipExpiresAt: string | null
}

export interface AdminUserDetails extends UserFields {
  /** 用户主键，接口以字符串返回以避免雪花 ID 精度丢失。 */
  id: string
  // 服务端维护的审计时间只用于展示，保留无时区日期时间原文及秒小数。
  createdAt: string
  editTime: string | null
  updateTime: string
  // 头像双地址允许从管理接口读取，不参与现有管理表单。
  avatarUrl?: string | null
  avatarOriginalUrl?: string | null
}

export interface AdminUserPage {
  records: AdminUserDetails[]
  total: number
  page: number
  pageSize: number
}

export interface DeletionTarget {
  id: string
  account: string
}

export interface CreateUserRequest extends Partial<Omit<UserFields, keyof ProfileFields>> {
  account: string
}

const resource = '/api/admin/users'
const errorMessages = {
  4000: '请求格式有误，请检查后重试。',
  4001: '请检查填写的内容是否符合表单要求。',
  4030: '当前账号没有用户管理权限。',
  4040: '未找到该用户。',
  // 新增或修改遇到 4090 时，沿用后端返回的重名提示。
  5000: '服务器暂时无法处理请求，请稍后重试。',
  5030: '服务暂时不可用，请稍后重试。',
}

export function parseUserId(value: string) {
  const text = value.trim()
  if (!/^\d{1,19}$/.test(text)) return null
  const id = BigInt(text)
  return id > 0n && id <= 9223372036854775807n ? id.toString() : null
}

export function actionableUserId(id: AdminUserDetails['id']) {
  // JSON 中的 Java Long 可能已被浏览器舍入，只使用可确认准确的值。
  return typeof id === 'number' && !Number.isSafeInteger(id) ? null : parseUserId(String(id))
}

export function lastUserPage({ total, pageSize }: AdminUserPage) {
  return Math.max(1, Math.ceil(total / pageSize))
}

export type UserPageFilters = {
  account?: string
  displayName?: string
  platformRoles?: string[]
  createdAtStart?: string
  createdAtEnd?: string
}

export async function fetchUserPage(
  page: number,
  pageSize: number,
  token: string,
  signal: AbortSignal,
  filters: UserPageFilters = {},
) {
  const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  // 账号、昵称条件随分页提交，空条件沿用全量列表。
  if (filters.account) query.set('account', filters.account)
  if (filters.displayName) query.set('displayName', filters.displayName)
  // 角色多选以重复查询参数提交，后端按集合命中任一角色匹配。
  for (const role of filters.platformRoles ?? []) query.append('platformRoles', role)
  for (const key of ['createdAtStart', 'createdAtEnd'] as const) {
    if (filters[key]) query.set(key, filters[key])
  }
  return requireData(await requestJson<AdminUserPage>(
    resource + '?' + query, bearerOptions(token, signal), errorMessages,
  ))
}

export async function fetchUser(id: string, token: string, signal: AbortSignal) {
  return requireData(await requestJson<AdminUserDetails>(
    `${resource}/${id}`, bearerOptions(token, signal), errorMessages,
  ))
}

export async function deleteUser(id: string, token: string, signal: AbortSignal) {
  // 软删除没有请求体，也不要求成功响应包含 data。
  await requestJson<void>(
    resource + '/' + id, { ...bearerOptions(token, signal), method: 'DELETE' }, errorMessages,
  )
}

async function writeUser<T>(
  path: string,
  method: 'POST' | 'PATCH',
  body: CreateUserRequest | (Partial<UserFields> & { id: string }),
  token: string,
  signal: AbortSignal,
) {
  const options = bearerOptions(token, signal)
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  return requestJson<T>(
    path, { ...options, headers, method, body: JSON.stringify(body) }, errorMessages,
  )
}

export async function createUser(fields: CreateUserRequest, token: string, signal: AbortSignal): Promise<void> {
  // 创建成功仅返回业务码，用户详情从列表或查询接口获取。
  await writeUser<void>(resource, 'POST', fields, token, signal)
}

export async function updateUser(id: string, fields: Partial<UserFields>, token: string, signal: AbortSignal): Promise<void> {
  // 更新接口从请求体读取目标 ID，十进制字符串保持 Java Long 的精度。
  await writeUser<void>(resource, 'PATCH', { ...fields, id }, token, signal)
}
