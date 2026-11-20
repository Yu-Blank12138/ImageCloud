<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { getActiveSession, invalidateSession } from '../auth/session'
import { isInvalidSessionError } from '../auth/api'
import AccountAvatar from '../components/AccountAvatar.vue'
import { hasAvatarDraft, avatarError, refreshAvatar } from '../profile/avatar'
import { fetchPublicProfile, UserNotFoundError, type PublicProfile } from '../profile/api'
import { parseUserId } from '../admin/users'

const route = useRoute()
const router = useRouter()
const ownUser = computed(() => getActiveSession()?.user)
const publicId = computed(() => typeof route.params.id === 'string' ? route.params.id : '')
const other = ref<PublicProfile | null>(null)
const loading = ref(false)
const error = ref('')
const user = computed(() => publicId.value ? other.value : ownUser.value)
const name = computed(() => user.value?.displayName || user.value?.account || (publicId.value ? '用户 ' + publicId.value : '我的账号'))
// 公开身份文案与接口约定一致：ADMIN 管理员、VIP 会员（到期后接口已降级为 USER）、其余为普通用户。
const roleLabel = computed(() => {
  const role = user.value?.platformRole
  if (role === 'ADMIN') return '管理员'
  if (role === 'VIP') return 'VIP 会员'
  return '普通用户'
})
const notFound = ref(false)
const notFoundDialog = ref<HTMLDialogElement | null>(null)
let request: AbortController | undefined
let generation = 0

async function loadPublic() {
  request?.abort()
  const current = ++generation
  other.value = null
  error.value = ''
  loading.value = false
  if (!publicId.value) return
  const id = parseUserId(publicId.value)
  if (!id) { error.value = '请输入有效的用户 ID。'; return }
  const session = getActiveSession()
  if (!session) return
  const controller = new AbortController()
  request = controller
  loading.value = true
  try {
    const value = await fetchPublicProfile(id, session.accessToken, controller.signal)
    if (current === generation && getActiveSession()?.accessToken === session.accessToken) other.value = value
  } catch (failure) {
    if (current !== generation || controller.signal.aborted || getActiveSession()?.accessToken !== session.accessToken) return
    if (isInvalidSessionError(failure)) {
      if (invalidateSession(session.accessToken)) await router.replace({ name: 'login' })
    } else if (failure instanceof UserNotFoundError) {
      // 用户不存在用模态弹窗提示，确认后返回我的主页。
      notFound.value = true
    } else error.value = failure instanceof Error ? failure.message : '资料加载失败，请重试。'
  } finally { if (current === generation) loading.value = false }
}

function confirmNotFound() {
  notFound.value = false
  void router.replace({ name: 'profile' })
}

watch(notFound, (shown) => {
  if (shown && !notFoundDialog.value?.open) notFoundDialog.value?.showModal()
  if (!shown && notFoundDialog.value?.open) notFoundDialog.value.close()
}, { flush: 'post' })

watch([publicId, () => getActiveSession()?.accessToken], loadPublic, { immediate: true })
onBeforeUnmount(() => { ++generation; request?.abort() })
</script>

<template>
  <section class="profile-page">
    <h1>{{ publicId ? '用户主页' : '个人主页' }}</h1>
    <p v-if="loading" role="status">正在加载用户资料…</p>
    <p v-if="error" role="alert">{{ error }} <button type="button" @click="loadPublic">重新加载</button></p>
    <div v-if="user" class="profile-card">
      <div class="profile-head">
        <AccountAvatar v-if="publicId && other" :name="name" size="large" :target-id="publicId" :avatar-url="other.avatarUrl" />
        <AccountAvatar v-else :name="name" size="large" />
        <div class="profile-identity">
          <h2>{{ name }}</h2>
          <p v-if="user.account">@{{ user.account }}</p>
          <p v-if="!publicId && hasAvatarDraft">头像尚未保存，请到账户设置确认保存。</p>
          <RouterLink v-if="!publicId" :to="{ name: 'account-settings' }">编辑资料</RouterLink>
        </div>
      </div>
      <dl class="profile-facts">
        <div><dt>身份</dt><dd>{{ roleLabel }}</dd></div>
        <div><dt>性别</dt><dd>{{ user.gender === 1 ? '男' : user.gender === 2 ? '女' : '未设置' }}</dd></div>
        <div><dt>邮箱</dt><dd>{{ user.email || '未填写' }}</dd></div>
        <div><dt>出生年月</dt><dd>{{ user.birthMonth || '未填写' }}</dd></div>
      </dl>
    </div>
    <section v-if="user" class="profile-bio">
      <h3>个人简介</h3>
      <p>{{ user.bio || '未填写' }}</p>
    </section>
    <p v-if="!publicId && avatarError" role="alert">{{ avatarError }} <button type="button" @click="refreshAvatar()">重新加载头像</button></p>
    <h2 class="works-title">公开作品</h2>
    <div class="works-placeholder"><span aria-hidden="true">▧</span><p>作品展示功能即将上线</p></div>

    <dialog ref="notFoundDialog" aria-labelledby="not-found-title" @close="notFound = false">
      <h2 id="not-found-title">不存在目标用户</h2>
      <p>用户 ID {{ publicId }} 不存在或已被删除。</p>
      <div class="dialog-actions"><button type="button" autofocus @click="confirmNotFound">确定</button></div>
    </dialog>
  </section>
</template>

<style scoped>
.profile-page {
  max-width: 1200px;
  margin: auto;
  padding: clamp(24px, 3.3vw, 48px);
}

/* 用户不存在弹窗：居中模态卡片，带遮罩。 */
dialog {
  width: min(360px, 86vw);
  padding: 28px;
  border: 1px solid #e1e6db;
  border-radius: 14px;
  background: white;
  box-shadow: 0 18px 50px #183d3126;
}

dialog::backdrop {
  background: #183d3144;
}

dialog h2 {
  font-size: 1.1rem;
}

dialog p {
  margin: 10px 0 0;
  overflow-wrap: anywhere;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 22px;
}

.dialog-actions button {
  padding: 10px 22px;
  border: 1px solid #b9c2b9;
  border-radius: 8px;
  background: #f6f8f2;
  color: #224c42;
  font: inherit;
  cursor: pointer;
}

h1 {
  margin: 0 0 30px;
  font-size: 1.65rem;
}

/* 主页信息卡：左侧头像与昵称，右侧身份、性别、邮箱和出生年月四列等宽展示。 */
.profile-card {
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  align-items: center;
  gap: clamp(24px, 4vw, 56px);
  padding: 36px;
  border: 1px solid #e1e6db;
  border-radius: 14px;
  background: white;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 0;
}

.profile-identity {
  min-width: 0;
  overflow-wrap: anywhere;
}

.profile-identity a {
  display: inline-block;
  margin-top: 14px;
}

/* 个人简介单独成卡，位于信息卡下方。 */
.profile-bio {
  margin-top: 24px;
  padding: 28px 36px;
  border: 1px solid #e1e6db;
  border-radius: 14px;
  background: white;
}

.profile-bio h3,
.profile-facts dt {
  margin: 0;
  color: #738276;
  font-size: 0.85rem;
  font-weight: 400;
}

.profile-bio p {
  margin: 8px 0 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.profile-facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px 24px;
  margin: 0;
}

.profile-facts > div {
  display: grid;
  gap: 6px;
}

.profile-facts dd {
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 0.875rem;
  color: #1f2b26;
}

h2 {
  margin: 0;
  font-size: 1.3rem;
}

p {
  color: #7b8774;
  font-size: 0.875rem;
}

a {
  padding: 12px 18px;
  border: 1px solid #b9c2b9;
  border-radius: 8px;
  color: #224c42;
  text-decoration: none;
  font-size: 0.85rem;
}

.works-title {
  margin-top: 36px;
  padding-bottom: 18px;
  border-bottom: 1px solid #e1e6db;
  font-size: 1rem;
}

.works-placeholder {
  display: grid;
  justify-items: center;
  align-content: center;
  min-height: 280px;
}

.works-placeholder span {
  color: #7b8774;
  font-size: 36px;
}

@media (max-width: 900px) {

  .profile-card {
    grid-template-columns: minmax(0, 1fr);
    align-items: start;
  }

  .profile-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px 24px;
  }
}

@media (max-width: 600px) {

  .profile-card {
    padding: 24px;
    gap: 24px;
  }

  .profile-facts {
    grid-template-columns: minmax(0, 1fr);
  }

  .profile-bio {
    padding: 20px 24px;
  }
}
</style>
