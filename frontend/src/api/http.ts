interface ApiResponse<T> {
  code: number
  data?: T
  message?: string
}

export class ApiError extends Error {
  constructor(readonly code: number | undefined, message: string) {
    super(message)
  }
}

type ErrorMessages = Partial<Record<number, string>>
const invalidResult = '服务器未返回有效结果，请稍后重试。'

export async function requestJson<T>(path: string, options: RequestInit, errorMessages: ErrorMessages) {
  // 请求使用同源路径，开发环境由 Vite 转发给 Spring Boot。
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  const response = await fetch(path, { ...options, headers }).catch(() => {
    throw new Error('无法连接服务器，请稍后重试。')
  })

  // HTTP 200 也可能包含业务错误，必须先判断响应体的 code。
  const result = (await response.json().catch(() => null)) as ApiResponse<T> | null
  if (!response.ok || result?.code !== 200) {
    const message = result ? errorMessages[result.code] ?? result.message : undefined
    throw new ApiError(result?.code, message || invalidResult)
  }
  return result.data
}

export function requireData<T>(data: T | undefined): T {
  if (data == null) throw new Error(invalidResult)
  return data
}

export function bearerOptions(accessToken: string, signal?: AbortSignal): RequestInit {
  // 调用方可在离开页面时取消等待，同时为认证请求保留超时。
  const timeout = AbortSignal.timeout(10_000)
  return {
    headers: { Authorization: `Bearer ${accessToken}` },
    cache: 'no-store',
    signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
  }
}
