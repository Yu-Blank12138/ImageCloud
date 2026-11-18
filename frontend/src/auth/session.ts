import { fetchSession, isInvalidSessionError, revokeSession, type LoginResult } from './api'

type LoginUser = LoginResult['user']
type ProfileTimeFields = 'editTime' | 'updateTime'

interface Session extends Omit<LoginResult, 'user'> {
  // 旧版会话可能缺少资料时间，恢复时保留缺失状态。
  user: Omit<LoginUser, ProfileTimeFields> & Partial<Pick<LoginUser, ProfileTimeFields>>
  expiresAt: number
}

type SessionStatus = 'active' | 'anonymous' | 'unavailable'

interface LocalSession {
  data: Session
  verified: boolean
  verification?: Promise<SessionStatus>
  logout?: Promise<boolean>
}

const storageKey = 'imagecloud.auth.session'
const stored = restoreSession()
let currentSession: LocalSession | null = stored ? { data: stored, verified: false } : null

function restoreSession(): Session | null {
  try {
    const saved = JSON.parse(sessionStorage.getItem(storageKey) ?? 'null') as Session | null
    return typeof saved?.accessToken === 'string' && saved.accessToken && Number.isFinite(saved.expiresAt)
      ? saved
      : null
  } catch {
    return null
  }
}

function persistSession(data: Session) {
  try {
    sessionStorage.setItem(storageKey, JSON.stringify(data))
  } catch {
    // 浏览器可能禁用会话存储，当前页面仍可使用内存会话。
  }
}

function clearSession() {
  currentSession = null
  try {
    sessionStorage.removeItem(storageKey)
  } catch {
    // 存储不可用时，仍立即清除内存会话。
  }
}

export function startSession(result: LoginResult) {
  if (
    !result.accessToken ||
    result.tokenType !== 'Bearer' ||
    !Number.isFinite(result.expiresIn) ||
    result.expiresIn <= 0
  ) {
    throw new Error('服务器未返回有效的登录凭证，请重试。')
  }

  // 刚登录的结果可以直接用于当前页面，刷新后再向服务端确认。
  const data = { ...result, expiresAt: Date.now() + result.expiresIn * 1000 }
  currentSession = { data, verified: true }
  persistSession(data)
}

export function getActiveSession() {
  if (!currentSession?.verified) return null
  if (currentSession.data.expiresAt <= Date.now()) {
    clearSession()
    return null
  }
  return currentSession.data
}

export function invalidateSession(accessToken: string) {
  // 受保护接口的失效响应只清理该请求使用的会话。
  if (currentSession?.data.accessToken !== accessToken) return false
  clearSession()
  return true
}

export async function ensureSession(): Promise<SessionStatus> {
  const session = currentSession
  if (!session) return 'anonymous'
  if (session.verified) return getActiveSession() ? 'active' : 'anonymous'

  // 同一令牌的并发导航共用验证请求，失败后允许重新发起。
  session.verification ??= verifyStoredSession(session).finally(() => {
    session.verification = undefined
  })
  return session.verification
}

async function verifyStoredSession(session: LocalSession): Promise<SessionStatus> {
  try {
    const result = await fetchSession(session.data.accessToken)
    // 注销或重新登录后，旧请求不能把已替换的会话写回存储。
    if (currentSession !== session) return ensureSession()

    const expiresAt = Date.parse(result.expiresAt)
    if (!Number.isFinite(expiresAt) || !result.user) return 'unavailable'
    if (expiresAt <= Date.now()) {
      clearSession()
      return 'anonymous'
    }

    session.data = { ...session.data, user: result.user, expiresAt }
    session.verified = true
    persistSession(session.data)
    return 'active'
  } catch (error) {
    if (currentSession !== session) return ensureSession()
    if (!isInvalidSessionError(error)) return 'unavailable'
    clearSession()
    return 'anonymous'
  }
}

export async function logoutSession(): Promise<boolean> {
  const session = currentSession
  if (!session) return true

  // 暂时性失败保留会话，结束本次请求后用户可再次退出。
  session.logout ??= logoutCurrentSession(session).finally(() => {
    session.logout = undefined
  })
  return session.logout
}

async function logoutCurrentSession(session: LocalSession): Promise<boolean> {
  try {
    await revokeSession(session.data.accessToken)
  } catch (error) {
    if (currentSession === session && !isInvalidSessionError(error)) throw error
  }

  if (currentSession === session) clearSession()
  return currentSession === null
}
