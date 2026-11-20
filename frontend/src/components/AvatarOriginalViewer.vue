<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { isInvalidSessionError } from '../auth/api'
import { getActiveSession, invalidateSession } from '../auth/session'
import { fetchAvatarOriginal, type AvatarUpload } from '../profile/api'

const props = defineProps<{ draft?: AvatarUpload; targetId?: string }>()
const emit = defineEmits<{ close: [] }>()
const router = useRouter()
const dialog = ref<HTMLDialogElement | null>(null)
const url = ref('')
const loading = ref(false)
const error = ref('')
const session = getActiveSession()
let generation = 0
let request: AbortController | undefined

function close() {
  ++generation
  request?.abort()
  dialog.value?.close()
  emit('close')
}

async function load() {
  request?.abort()
  const controller = new AbortController()
  request = controller
  const current = ++generation
  error.value = ''
  url.value = ''
  if (!session || getActiveSession()?.accessToken !== session.accessToken) { close(); return }
  loading.value = true
  try {
    // 未保存上传查看本次原图，避免误查账号仍在使用的旧头像。
    if (props.draft) url.value = props.draft.avatarOriginalUrl
    else {
      const id = props.targetId ?? session.user.id
      if (typeof id === 'number' && !Number.isSafeInteger(id)) throw new Error('无法确认当前账号的准确 ID，请稍后重试。')
      const original = await fetchAvatarOriginal(String(id), session.accessToken, controller.signal)
      if (current !== generation || getActiveSession()?.accessToken !== session.accessToken) return
      url.value = original
    }
  } catch (failure) {
    // 关闭、重试或切换会话后，迟到响应不能恢复已失效的查看状态。
    if (current !== generation || controller.signal.aborted) return
    if (isInvalidSessionError(failure)) {
      const invalidated = invalidateSession(session.accessToken)
      close()
      if (invalidated) await router.replace({ name: 'login' })
    } else error.value = failure instanceof Error ? failure.message : '原图加载失败，请重试。'
  } finally {
    if (current === generation) loading.value = false
  }
}

function imageFailed() {
  url.value = ''
  error.value = '原图加载失败，请重试。'
}

watch(() => getActiveSession()?.accessToken, (token) => {
  if (token !== session?.accessToken) close()
}, { flush: 'sync' })

onMounted(() => {
  // 原生模态框限制焦点范围，Escape 关闭后自动恢复触发按钮焦点。
  dialog.value?.showModal()
  void load()
})
onBeforeUnmount(() => {
  ++generation
  request?.abort()
  dialog.value?.close()
})
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialog" class="avatar-viewer" aria-label="头像原图" @cancel.prevent="close" @click="($event.target === dialog) && close()">
      <div class="viewer-content">
        <header>
          <div><h2>{{ draft ? '新头像原图预览' : '头像原图' }}</h2><p v-if="draft">此头像尚未保存到账户。</p></div>
          <button class="close" type="button" aria-label="关闭头像原图" autofocus @click="close">×</button>
        </header>
        <div class="image-area" :aria-busy="loading">
          <p v-if="loading" role="status">正在加载原图…</p>
          <img v-if="url" :key="url" :src="url" :alt="draft ? '尚未保存的新头像原图' : '当前头像原图'" @error="imageFailed" />
          <div v-if="error" class="viewer-error"><p role="alert">{{ error }}</p><button type="button" @click="load">重新加载原图</button></div>
        </div>
      </div>
    </dialog>
  </Teleport>
</template>

<style scoped>
.avatar-viewer {
  width: min(960px, calc(100vw - 32px));
  max-width: none;
  max-height: calc(100svh - 32px);
  padding: 0;
  border: 1px solid #dce5d8;
  border-radius: 14px;
  background: #f9faf7;
  color: #183d31;
  box-shadow: 0 24px 90px #102a3050;
}

.avatar-viewer::backdrop {
  background: #122521b8;
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 24px;
  border-bottom: 1px solid #e1e6db;
  background: white;
}

h2 {
  margin: 0;
  font-size: 1.1rem;
}

header p {
  margin: 8px 0 0;
  color: #738276;
  font-size: 0.85rem;
}

button {
  padding: 10px 16px;
  border: 1px solid #bac8ba;
  border-radius: 8px;
  background: white;
  color: #224c42;
  font: inherit;
  cursor: pointer;
}

.close {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  padding: 0;
  font-size: 26px;
}

button:focus-visible {
  outline: 3px solid #628d76;
  outline-offset: 3px;
}

.image-area {
  display: grid;
  place-items: center;
  min-height: 240px;
  padding: 20px;
}

img {
  display: block;
  max-width: 100%;
  max-height: calc(100svh - 190px);
  object-fit: contain;
}

.viewer-error {
  text-align: center;
  overflow-wrap: anywhere;
}
</style>
