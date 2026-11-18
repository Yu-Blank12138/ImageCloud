import { ApiError, bearerOptions, requestJson, requireData } from '../api/http'

interface Credentials {
  account: string
  password: string
}

interface Registration extends Credentials {
  displayName?: string
}

interface RegisteredAccount {
  id: number
  account: string
}

export interface LoginResult {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: RegisteredAccount & {
    displayName?: string
    platformRole: 'USER' | 'VIP' | 'ADMIN'
    vipExpiresAt?: string
    vipActive: boolean
    // 保留服务端的 UTC ISO 8601 原文及秒小数，首次自行修改资料前 editTime 为 null。
    editTime: string | null
    updateTime: string
  }
}

interface SessionResult {
  user: LoginResult['user']
  expiresAt: string
}

const errorMessages: Partial<Record<number, string>> = {
  4000: '请求格式有误，请检查后重试。',
  4001: '请检查填写的内容是否符合表单要求。',
  4010: '登录状态已失效，请重新登录。',
  4011: '账号或密码错误。',
  4012: '登录状态已失效，请重新登录。',
  4090: '该账号已被注册。',
  5030: '认证服务暂时不可用，请稍后重试。',
}

async function submit<T>(action: 'login' | 'register', fields: Registration, signal: AbortSignal) {
  const data = await requestJson<T>(
    `/api/auth/${action}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(fields),
      signal,
    },
    errorMessages,
  )
  return requireData(data)
}

export function register(fields: Registration, signal: AbortSignal) {
  return submit<RegisteredAccount>('register', fields, signal)
}

export function login(fields: Credentials, signal: AbortSignal) {
  return submit<LoginResult>('login', fields, signal)
}

export async function fetchSession(accessToken: string) {
  const data = await requestJson<SessionResult>(
    '/api/auth/session', bearerOptions(accessToken), errorMessages,
  )
  return requireData(data)
}

export async function revokeSession(accessToken: string) {
  // 注销成功只要求业务码为 200，请求和成功响应均无须数据字段。
  await requestJson<void>(
    '/api/auth/logout', { ...bearerOptions(accessToken), method: 'POST' }, errorMessages,
  )
}

export function isInvalidSessionError(error: unknown) {
  return error instanceof ApiError && (error.code === 4010 || error.code === 4012)
}
