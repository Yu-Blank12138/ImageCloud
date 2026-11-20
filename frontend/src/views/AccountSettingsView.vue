<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isInvalidSessionError } from '../auth/api'
import { ensureSession, getActiveSession, invalidateSession } from '../auth/session'
import { updatePassword, updateProfile, uploadPreview, type ProfileUpdate } from '../profile/api'
import { avatarDraft, hasAvatarDraft, avatarError, refreshAvatar, setAvatarPreview } from '../profile/avatar'
import AccountAvatar from '../components/AccountAvatar.vue'

import ProfileFieldsForm from '../components/ProfileFieldsForm.vue'
import AvatarCropDialog from '../components/AvatarCropDialog.vue'
import { profileDraft, profileChanges, validateProfile } from '../profile/fields'

const router = useRouter()
const tab = ref<'profile' | 'password'>('profile')
const baseline = ref(getActiveSession()?.user)
const profile = reactive({ displayName: baseline.value?.displayName ?? '', ...profileDraft(baseline.value) })
const passwords = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pending = ref(false)
const needsRefresh = ref(false)
const error = ref('')
const profileMessage = ref('')
const passwordMessage = ref('')
const avatarMessage = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const cropFile = ref<File | null>(null)
const lifetime = new AbortController()
const passwordFields = [
  { key: 'oldPassword', label: '当前密码', autocomplete: 'current-password' },
  { key: 'newPassword', label: '新密码', autocomplete: 'new-password' },
  { key: 'confirmPassword', label: '确认新密码', autocomplete: 'new-password' },
] as const

function selectTab(value: typeof tab.value) {
  tab.value = value
  error.value = ''
}

async function selectImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || pending.value) return
  avatarMessage.value = ''
  error.value = ''
  if (file.size === 0 || file.size > 20 * 1024 * 1024) { error.value = '请选择不超过 20MB 的非空图片。'; return }
  if (!/\.(jpe?g|png|webp|gif|bmp|tiff?)$/i.test(file.name)) { error.value = '图片格式不受支持。'; return }
  // 先弹出裁剪框，由用户确认展示区域后再上传裁剪结果。
  cropFile.value = file
}

// 接收裁剪弹窗输出的 PNG，作为头像预览上传。
async function uploadCropped(blob: Blob) {
  const file = cropFile.value
  cropFile.value = null
  if (!file || pending.value) return
  const name = file.name.replace(/\.[^.]+$/, '') + '.png'
  await run(async (token) => {
    const upload = await uploadPreview(new File([blob], name, { type: 'image/png' }), token, lifetime.signal)
    if (lifetime.signal.aborted) return
    setAvatarPreview(upload, token)
    avatarMessage.value = '头像已上传预览，请保存修改以更新账号头像。'
  })
}

async function run(action: (token: string) => Promise<void>) {
  if (pending.value) return
  const session = getActiveSession()
  if (!session) { await router.replace({ name: 'login' }); return }
  pending.value = true
  error.value = ''
  try {
    await action(session.accessToken)
  } catch (failure) {
    if (lifetime.signal.aborted) return
    if (isInvalidSessionError(failure)) {
      if (invalidateSession(session.accessToken)) await router.replace({ name: 'login' })
    } else {
      error.value = failure instanceof Error ? failure.message : '操作失败，请重试。'
    }
  } finally { pending.value = false }
}

async function readProfile(token: string) {
  // 强制刷新复用现有防回写逻辑，失败时保留已保存状态和令牌。
  const status = await ensureSession(true)
  if (lifetime.signal.aborted) return
  if (status === 'anonymous') { await router.replace({ name: 'login' }); return }
  if (getActiveSession()?.accessToken !== token) return
  if (status !== 'active') throw new Error('资料已保存，暂时无法读取最新资料，请重新加载。')
  baseline.value = getActiveSession()?.user
  Object.assign(profile, profileDraft(baseline.value))
  profile.displayName = baseline.value?.displayName ?? ''
  if (!await refreshAvatar(true)) throw new Error('资料已保存，头像回读失败，请重新加载资料。')
  needsRefresh.value = false
  avatarMessage.value = ''
}

async function saveProfile() {
  if (pending.value || needsRefresh.value) return
  profileMessage.value = ''
  error.value = ''
  const fields: ProfileUpdate = profileChanges(baseline.value ?? {}, profile)
  const invalid = validateProfile(fields)
  if (invalid) { error.value = invalid; return }
  const displayName = profile.displayName.trim()
  // 登录账号不可修改，只提交昵称、资料字段和头像草稿。
  if (displayName !== (baseline.value?.displayName ?? '')) fields.displayName = displayName
  // 同一次上传的压缩图与原图成对保存，取消时整个草稿一起丢弃。
  if (avatarDraft.value) Object.assign(fields, avatarDraft.value)
  if (displayName.length > 12) { error.value = '昵称不能超过 12 个字符。'; return }
  if (Object.keys(fields).length === 0) { profileMessage.value = '没有可保存的改动。'; return }
  await run(async (token) => {
    await updateProfile(fields, token, lifetime.signal)
    if (lifetime.signal.aborted) return
    needsRefresh.value = true
    profileMessage.value = '资料已保存。'
    await readProfile(token)
    // 保存成功后回读完成，跳转回个人主页展示最新资料。
    if (!lifetime.signal.aborted && !needsRefresh.value) await router.push({ name: 'profile' })
  })
}

async function changePassword() {
  if (pending.value) return
  passwordMessage.value = ''
  error.value = ''
  const valid = (value: string) => value.length >= 8 && value.length <= 32 && /[^\x00-\x20]/.test(value)
  if (!valid(passwords.oldPassword) || !valid(passwords.newPassword)) {
    error.value = '当前密码和新密码须为 8–32 个字符，不能全为空白。'; return
  }
  if (passwords.newPassword !== passwords.confirmPassword) { error.value = '两次输入的新密码不一致。'; return }
  await run(async (token) => {
    // 密码按原文提交，确认密码只在浏览器比较。
    await updatePassword({ oldPassword: passwords.oldPassword, newPassword: passwords.newPassword }, token, lifetime.signal)
    if (lifetime.signal.aborted) return
    Object.assign(passwords, { oldPassword: '', newPassword: '', confirmPassword: '' })
    passwordMessage.value = '密码已更新。'
  })
}

onBeforeUnmount(() => {
  lifetime.abort()
  Object.assign(passwords, { oldPassword: '', newPassword: '', confirmPassword: '' })
})
</script>

<template>
  <section class="settings-page">
    <h1>账户设置</h1>
    <nav class="settings-tabs" aria-label="账户设置分类">
      <button type="button" :class="{ active: tab === 'profile' }" :aria-current="tab === 'profile' ? 'page' : undefined" :disabled="pending" @click="selectTab('profile')">基本资料</button>
      <button type="button" :class="{ active: tab === 'password' }" :aria-current="tab === 'password' ? 'page' : undefined" :disabled="pending" @click="selectTab('password')">修改密码</button>
    </nav>

    <form v-if="tab === 'profile'" class="settings-card" novalidate :aria-busy="pending" @submit.prevent="saveProfile">
      <fieldset :disabled="pending || needsRefresh">
        <div class="avatar-field">
          <AccountAvatar :name="baseline?.displayName || baseline?.account || '我'" size="medium" />
          <div>
            <input ref="fileInput" class="file-input" type="file" accept=".jpg,.jpeg,.png,.webp,.gif,.bmp,.tif,.tiff" aria-label="选择头像预览图片" @change="selectImage" />
            <button type="button" @click="fileInput?.click()">上传头像预览</button>
            <p class="hint">图片不超过 20MB。上传后预览，保存修改后生效。</p>
          </div>
        </div>
        <div class="field"><label for="self-account">登录账号</label><input id="self-account" :value="baseline?.account" disabled autocomplete="username" /><p class="hint">登录账号不可修改。</p></div>
        <div class="field"><label for="self-nickname">昵称</label><input id="self-nickname" v-model="profile.displayName" autocomplete="nickname" maxlength="12" /><p class="hint">最多 12 个字符，留空可清除。</p></div>
        <ProfileFieldsForm :draft="profile" prefix="self" />
      </fieldset>
      <p v-if="avatarMessage" class="success" role="status">{{ avatarMessage }}</p>
      <p v-else-if="hasAvatarDraft" class="hint">头像尚未保存，请保存修改以生效。</p>
      <p v-if="avatarError && !needsRefresh" class="error" role="alert">{{ avatarError }} <button type="button" :disabled="pending" @click="refreshAvatar()">重新加载头像</button></p>
      <p v-if="profileMessage" class="success" role="status">{{ profileMessage }}</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div class="form-actions">
        <button v-if="needsRefresh" class="primary" type="button" :disabled="pending" @click="run(readProfile)">{{ pending ? '正在加载…' : '重新加载资料' }}</button>
        <button v-else class="primary" type="submit" :disabled="pending">{{ pending ? '正在保存…' : '保存修改' }}</button>
      </div>
    </form>

    <form v-else class="settings-card" novalidate :aria-busy="pending" @submit.prevent="changePassword">
      <fieldset :disabled="pending">
        <div v-for="field in passwordFields" :key="field.key" class="field">
          <label :for="field.key">{{ field.label }}</label>
          <input :id="field.key" v-model="passwords[field.key]" type="password" :autocomplete="field.autocomplete" required />
        </div>
        <p class="hint">密码须为 8–32 个字符，不能全为空白。</p>
      </fieldset>
      <p v-if="passwordMessage" class="success" role="status">{{ passwordMessage }}</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div class="form-actions"><button class="primary" type="submit" :disabled="pending">{{ pending ? '正在更新…' : '更新密码' }}</button></div>
    </form>

    <AvatarCropDialog v-if="cropFile" :file="cropFile" @confirm="uploadCropped" @cancel="cropFile = null" />
  </section>
</template>

<style scoped>
.settings-page {
  max-width: 1000px;
  margin: auto;
  padding: clamp(24px, 3.3vw, 48px);
}

h1 {
  margin: 0 0 28px;
  font-size: 1.65rem;
}

.settings-tabs {
  display: flex;
  gap: 28px;
  margin-bottom: 28px;
  border-bottom: 1px solid #e1e6db;
}

.settings-tabs button {
  padding: 10px 2px 15px;
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  background: none;
  color: #7b8774;
}

.settings-tabs .active {
  color: #224c42;
  border-bottom-color: #224c42;
  font-weight: 600;
}

.settings-card {
  max-width: 640px;
  margin: auto;
  padding: 32px;
  border: 1px solid #e1e6db;
  border-radius: 12px;
  background: white;
}

fieldset {
  display: grid;
  gap: 24px;
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

.field {
  display: grid;
  gap: 8px;
}

label {
  font-size: 0.875rem;
  font-weight: 600;
}

input, button {
  min-width: 0;
  min-height: 44px;
  padding: 10px 14px;
  border: 1px solid #b9c2b9;
  border-radius: 8px;
  color: inherit;
  background: #fdfdf9;
  font: inherit;
  font-size: 0.875rem;
}

input {
  width: 100%;
}

button {
  cursor: pointer;
}

button:disabled {
  cursor: default;
  opacity: 0.6;
}

:is(input, button):focus-visible {
  outline: 2px solid #3d7255;
  outline-offset: 3px;
}

.avatar-field {
  display: flex;
  align-items: center;
  gap: 20px;
}

.file-input {
  display: none;
}

.hint {
  margin: 5px 0 0;
  color: #7b8774;
  font-size: 0.8rem;
  line-height: 1.6;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px solid #e1e6db;
}

.primary {
  color: #fffdf3;
  background: #365c52;
  border-color: #365c52;
  font-weight: 600;
}

.success, .error {
  margin: 20px 0 0;
  padding: 12px;
  border-radius: 8px;
  font-size: 0.85rem;
  line-height: 1.6;
}

.success {
  color: #224c42;
  background: #e6eee6;
}

.error {
  color: #862a2b;
  background: #fbeceb;
}

@media (max-width: 600px) {

  .settings-card {
    padding: 24px;
  }
}
</style>
