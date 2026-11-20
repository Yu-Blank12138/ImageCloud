import { computed, shallowRef, triggerRef, watch } from 'vue'
import { getActiveSession, invalidateSession } from '../auth/session'
import { isInvalidSessionError } from '../auth/api'
import { fetchAvatar, type AvatarUpload } from './api'

interface AvatarState {
  token: string
  savedUrl: string | null
  draft?: AvatarUpload
  error: string
  readGeneration: number
  read?: Promise<boolean>
}

// 保存值与上传草稿分开，按当前令牌隔离，异步查询和上传均不能覆盖新会话。
const state = shallowRef<AvatarState | null>(null)
export const avatarDraft = computed(() => state.value?.draft)
export const avatarPreviewUrl = computed(() => state.value?.draft?.avatarUrl ?? state.value?.savedUrl ?? undefined)
export const hasAvatarDraft = computed(() => Boolean(state.value?.draft))
export const avatarError = computed(() => state.value?.error ?? '')

watch(() => getActiveSession()?.accessToken, (token) => {
  state.value = token ? { token, savedUrl: null, error: '', readGeneration: 0 } : null
  if (token) void refreshAvatar()
}, { immediate: true, flush: 'sync' })

export function setAvatarPreview(upload: AvatarUpload, token: string) {
  if (getActiveSession()?.accessToken !== token || state.value?.token !== token) return
  // 草稿仅保留头像双地址，保存时不透传上传响应中的其他字段。
  state.value.draft = { avatarUrl: upload.avatarUrl, avatarOriginalUrl: upload.avatarOriginalUrl }
  triggerRef(state)
}

export function discardAvatarDraft() {
  if (!state.value) return
  state.value.draft = undefined
  triggerRef(state)
}

export async function refreshAvatar(clearDraft = false): Promise<boolean> {
  const avatar = state.value
  const session = getActiveSession()
  if (!avatar || !session || avatar.token !== session.accessToken) return false
  // Java Long 以可确认准确的 ID 请求，不使用浏览器已经舍入的数字。
  const id = session.user.id
  if (typeof id === 'number' && !Number.isSafeInteger(id)) {
    avatar.error = '无法确认当前账号的准确 ID，请稍后重试。'
    triggerRef(state)
    return false
  }
  // 保存后必须新发查询；旧响应及旧请求的清理逻辑不能覆盖本轮回读。
  if (clearDraft || !avatar.read) {
    const generation = ++avatar.readGeneration
    const read = readAvatar(avatar, String(id), generation).finally(() => {
      if (avatar.read === read) avatar.read = undefined
    })
    avatar.read = read
  }
  const generation = avatar.readGeneration
  const loaded = await avatar.read
  if (loaded && state.value === avatar && generation === avatar.readGeneration && clearDraft) {
    avatar.draft = undefined
    triggerRef(state)
  }
  return loaded
}

async function readAvatar(avatar: AvatarState, id: string, generation: number) {
  try {
    const url = await fetchAvatar(id, avatar.token)
    if (state.value !== avatar || generation !== avatar.readGeneration || getActiveSession()?.accessToken !== avatar.token) return false
    avatar.savedUrl = url
    avatar.error = ''
    triggerRef(state)
    return true
  } catch (error) {
    if (state.value !== avatar || generation !== avatar.readGeneration) return false
    if (isInvalidSessionError(error)) invalidateSession(avatar.token)
    else {
      avatar.error = error instanceof Error ? error.message : '头像加载失败，请重试。'
      triggerRef(state)
    }
    return false
  }
}
