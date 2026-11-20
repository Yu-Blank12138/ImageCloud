import { bearerOptions, requestJson, requireData } from '../api/http'

// 主键与关联 ID 均由接口以字符串返回，避免雪花 ID 在浏览器中丢失精度。
export type ImageId = string
export interface AdminImage {
  id: ImageId
  spaceId: ImageId | null
  uploaderId: ImageId
  name: string
  description: string | null
  category: string | null
  tags: string[]
  originalFilename: string
  originalUrl: string
  thumbnailUrl: string | null
  format: string
  fileSize: number
  width: number
  height: number
  vipOnly: number
  likeCount: number
  auditStatus: number
  auditMessage: string | null
  auditorId: ImageId | null
  // 业务时间为无时区 LocalDateTime 字符串，直接保留接口值及秒小数。
  auditTime: string | null
  createdAt: string
  editTime: string | null
  updateTime: string
}
export interface ImagePage {
  records: AdminImage[]
  total: number
  page: number
  pageSize: number
}
export interface UploadedImage {
  imageUrl: string
  thumbnailUrl: string
}
export interface ImageFields {
  name?: string
  spaceId?: string
  description?: string
  category?: string
  tags?: string[]
  vipOnly?: number
  imageUrl?: string
}
export type ImageFilters = Partial<Record<
  'id' | 'name' | 'category' | 'spaceId' | 'uploaderId' | 'auditStatus' | 'vipOnly' |
  'createdAtStart' | 'createdAtEnd', string>>

const resource = '/api/images/admin'
const messages = {
  4000: '请求格式有误，请检查后重试。',
  4001: '请检查字段或上传地址是否符合要求。',
  4010: '请先登录。',
  4012: '登录已失效，请重新登录。',
  4030: '当前账号没有图片管理权限。',
  4040: '图片记录或上传对象不存在，请重新查询；保存图片时请重新上传。',
  4130: '单张图片不能超过 20MB。',
  4150: '图片格式不支持，请选择支持的图片文件。',
  5030: '服务暂时不可用，请稍后重试。',
}

export function imageId(value: ImageId): string | null {
  // 拒绝已被 JS Number 舍入的 Long，避免编辑错误记录。
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return null
  const text = String(value).trim()
  if (!/^\d{1,19}$/.test(text)) return null
  const id = BigInt(text)
  return id > 0n && id <= 9223372036854775807n ? id.toString() : null
}

export function validateImageFile(file: File) {
  if (!file.size) throw new Error('请选择非空图片。')
  if (file.size > 20 * 1024 * 1024) throw new Error(messages[4130])
  if (!/\.(jpg|jpeg|png|webp|gif|bmp|tif|tiff)$/i.test(file.name)) throw new Error(messages[4150])
}

export async function uploadAdminImage(file: File, token: string, signal: AbortSignal) {
  validateImageFile(file)
  const body = new FormData()
  body.append('file', file)
  // multipart boundary 由浏览器生成，云上传使用较长超时。
  const options = bearerOptions(token)
  options.signal = AbortSignal.any([signal, AbortSignal.timeout(120_000)])
  return requireData(await requestJson<UploadedImage>(resource + '/uploads', {
    ...options, method: 'POST', body,
  }, messages))
}

export async function fetchImages(page: number, pageSize: number, filters: ImageFilters, token: string, signal: AbortSignal) {
  const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  for (const [key, value] of Object.entries(filters)) if (value !== '') query.set(key, value)
  return requireData(await requestJson<ImagePage>(resource + '?' + query, bearerOptions(token, signal), messages))
}

export async function fetchImage(id: string, token: string, signal: AbortSignal) {
  return requireData(await requestJson<AdminImage>(resource + '/' + id, bearerOptions(token, signal), messages))
}

export async function saveImage(id: string | null, fields: ImageFields, token: string, signal: AbortSignal) {
  const options = bearerOptions(token, signal)
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  // 修改类请求的目标 ID 统一放在请求体中；写入仅要求 code 200，详情由调用方独立回读。
  await requestJson<void>(resource, {
    ...options, headers, method: id ? 'PATCH' : 'POST',
    body: JSON.stringify(id ? { ...fields, id } : fields),
  }, messages)
}
