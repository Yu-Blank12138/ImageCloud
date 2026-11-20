import { imageId, type AdminImage, type ImageFields, type UploadedImage } from './admin'

export function imageDraft(image?: AdminImage) {
  return {
    name: image?.name ?? '',
    spaceId: image?.spaceId == null ? '' : String(image.spaceId),
    description: image?.description ?? '',
    category: image?.category ?? '',
    // 一行一个标签，保留标签内的逗号。
    tags: image?.tags.join('\n') ?? '',
    vipOnly: image?.vipOnly === 1,
  }
}

export function imagePayload(draft: ReturnType<typeof imageDraft>, baseline: AdminImage | null, upload: UploadedImage | null): ImageFields {
  const name = draft.name.trim()
  if (!name || name.length > 16) throw new Error('图片名称必填，最多 16 个字符。')
  if (draft.description.length > 128) throw new Error('图片说明最多 128 个字符。')
  if (draft.category.length > 16) throw new Error('图片分类最多 16 个字符。')
  const tags = [...new Set(draft.tags.split(/\r?\n/).map(value => value.trim()).filter(Boolean))]
  if (tags.some(tag => tag.length > 20)) throw new Error('每个标签最多 20 个字符。')
  const unchangedSpace = baseline?.spaceId != null && draft.spaceId === String(baseline.spaceId)
  const spaceId = !unchangedSpace && draft.spaceId.trim() ? imageId(draft.spaceId) : undefined
  if (spaceId === null) throw new Error('空间 ID 必须是有效正整数。')
  if (baseline?.spaceId != null && !unchangedSpace && !spaceId) throw new Error('当前接口不支持清空已有空间，请保留或填写新的空间 ID。')
  if (baseline?.spaceId != null && typeof baseline.spaceId === 'number' && !Number.isSafeInteger(baseline.spaceId)) {
    if (draft.spaceId !== String(baseline.spaceId)) throw new Error('原空间 ID 无法精确表示，暂无法修改归属。')
  }
  if (!baseline && !upload) throw new Error('请先上传图片，再保存图片信息。')
  const fields: ImageFields = {}
  // 只复制改动字段；原图签名 URL 使用上传响应原文，绝不规整或重新编码。
  if (upload) fields.imageUrl = upload.imageUrl
  if (!baseline || name !== baseline.name) fields.name = name
  if (spaceId && (!baseline || draft.spaceId !== String(baseline.spaceId ?? ''))) fields.spaceId = spaceId
  if (!baseline || draft.description !== (baseline.description ?? '')) fields.description = draft.description
  if (!baseline || draft.category !== (baseline.category ?? '')) fields.category = draft.category
  if (!baseline || JSON.stringify(tags) !== JSON.stringify(baseline.tags)) fields.tags = tags
  const vipOnly = draft.vipOnly ? 1 : 0
  if (!baseline || vipOnly !== baseline.vipOnly) fields.vipOnly = vipOnly
  if (!Object.keys(fields).length) throw new Error('请先修改图片信息。')
  return fields
}
