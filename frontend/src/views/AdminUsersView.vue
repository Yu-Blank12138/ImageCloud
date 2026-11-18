<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  actionableUserId, createUser, deleteUser, fetchUser, fetchUserPage, lastUserPage, parseUserId, updateUser,
  type AdminUserDetails, type AdminUserPage, type DeletionTarget, type UserFields,
} from '../admin/users'
import { isInvalidSessionError } from '../auth/api'
import { getActiveSession, invalidateSession } from '../auth/session'
import AdminUserList from '../components/AdminUserList.vue'
import UserTime from '../components/UserTime.vue'
import { browserTimeZone, toLocalDateTime, toUtcDateTime } from '../utils/time'

const router = useRouter()
const lookupId = ref('')
const lookupError = ref('')
const accountInput = ref('')
const accountQuery = ref('')
const searchError = ref('')
const saveError = ref('')
const listError = ref('')
const deleteError = ref('')
const feedback = ref('')
const listNotice = ref('')
const deleteTarget = ref<DeletionTarget | null>(null)
const pageQuery = ref({ page: 1, pageSize: 20 })
const userPage = ref<AdminUserPage | null>(null)
const errors = { lookup: lookupError, save: saveError, list: listError, delete: deleteError }
const busy = ref<keyof typeof errors | null>(null)
const baseline = ref<{
  id: string | null
  fields: UserFields
  times: Pick<AdminUserDetails, 'createdAt' | 'editTime' | 'updateTime'>
} | null>(null)
const currentAccount = getActiveSession()?.user
const currentAccountId = currentAccount ? actionableUserId(currentAccount.id) : null
const detailsRestriction = computed(() => baseline.value ? writeRestriction(baseline.value.id, currentAccountId) : '')
const detailsTitle = ref<HTMLElement | null>(null)
const drawer = ref<HTMLDialogElement | null>(null)
const drawerOpen = ref(false)
const detailsNeedRefresh = ref(false)
let request: AbortController | undefined

// 原生抽屉约束键盘焦点，等待写入期间阻止关闭，避免误判提交结果。
watch(drawerOpen, (open) => {
  if (open && !drawer.value?.open) drawer.value?.showModal()
  if (!open && drawer.value?.open) drawer.value.close()
}, { flush: 'post' })

function closeDrawer() {
  if (!busy.value) drawerOpen.value = false
}

function writeRestriction(targetId: string | null, operatorId: string | null) {
  // 无法精确确认操作者 ID 时，仅允许查看已有账号，避免绕过本人保护。
  if (!operatorId) return '无法确认当前账号的准确 ID，仅可查看已有账号。'
  if (targetId === operatorId) return '当前账号，仅可查看。'
  return ''
}

function initialDraft() {
  return {
    account: '',
    displayName: '',
    platformRole: 'USER' as UserFields['platformRole'],
    status: 'ACTIVE' as UserFields['status'],
    vipExpiresAt: '',
  }
}

const draft = reactive(initialDraft())

function newUser() {
  baseline.value = null
  detailsNeedRefresh.value = false
  Object.assign(draft, initialDraft())
  lookupError.value = ''
  saveError.value = ''
  feedback.value = ''
}

function focusDetails() {
  drawerOpen.value = true
  void nextTick(() => detailsTitle.value?.focus())
}

function startNewUser() {
  newUser()
  focusDetails()
}

function applyDetails(details: AdminUserDetails, requestedId?: string) {
  detailsNeedRefresh.value = false
  // 查询和编辑沿用请求中的十进制 ID，避免把 Java Long 经过 JS Number 舍入后写到别的用户。
  const id = requestedId ?? actionableUserId(details.id)
  const fields: UserFields = {
    account: details.account,
    displayName: details.displayName ?? null,
    platformRole: details.platformRole,
    status: details.status,
    vipExpiresAt: details.vipExpiresAt ?? null,
  }
  // 时间独立于可写字段保存，回读详情后更新展示，不加入 PATCH 载荷。
  const times = { createdAt: details.createdAt, editTime: details.editTime, updateTime: details.updateTime }
  baseline.value = { id, fields, times }
  lookupId.value = id ?? ''
  Object.assign(draft, {
    ...fields,
    displayName: fields.displayName ?? '',
    vipExpiresAt: fields.vipExpiresAt ? toLocalDateTime(fields.vipExpiresAt) ?? '' : '',
  })
}

function utcTimeKey(value: string) {
  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z$/.exec(value)
  if (!match) return undefined
  const seconds = match[1]
  const date = new Date(seconds + 'Z')
  if (Number.isNaN(date.getTime()) || date.toISOString().slice(0, 19) !== seconds) return undefined
  // 只在比较时去掉秒小数的尾零，保留其他精度及提交时的原文。
  const fraction = match[2]?.replace(/0+$/, '')
  return seconds + (fraction ? '.' + fraction : '') + 'Z'
}

function changedFields(before: UserFields, after: UserFields) {
  const patch: Partial<UserFields> = {}
  if (before.account !== after.account) patch.account = after.account
  if (before.displayName !== after.displayName) patch.displayName = after.displayName
  if (before.platformRole !== after.platformRole) patch.platformRole = after.platformRole
  if (before.status !== after.status) patch.status = after.status
  if (before.vipExpiresAt !== after.vipExpiresAt) {
    const time = after.vipExpiresAt && utcTimeKey(after.vipExpiresAt)
    if (!time || !before.vipExpiresAt || time !== utcTimeKey(before.vipExpiresAt)) {
      patch.vipExpiresAt = after.vipExpiresAt
    }
  }
  return patch
}

async function run<T>(
  operation: keyof typeof errors,
  action: (token: string, signal: AbortSignal) => Promise<T>,
  apply: (result: T) => void,
  targetId?: string,
) {
  if (busy.value) return false
  const session = getActiveSession()
  if (!session) {
    await router.replace({ name: 'login' })
    return false
  }

  if (targetId !== undefined) {
    // 提交和二次确认都用即将发出请求的会话再核验一次，不能只依赖按钮状态。
    const restriction = writeRestriction(targetId, actionableUserId(session.user.id))
    if (restriction) {
      errors[operation].value = restriction
      return false
    }
  }

  const controller = new AbortController()
  request = controller
  busy.value = operation
  errors[operation].value = ''
  try {
    const result = await action(session.accessToken, controller.signal)
    if (controller.signal.aborted) return false
    apply(result)
    return true
  } catch (failure) {
    if (controller.signal.aborted) return false
    if (isInvalidSessionError(failure)) {
      if (invalidateSession(session.accessToken)) await router.replace({ name: 'login' })
    } else {
      errors[operation].value = failure instanceof Error ? failure.message : '请求失败，请稍后重试。'
    }
    return false
  } finally {
    request = undefined
    busy.value = null
  }
}

async function loadPage(page = pageQuery.value.page, pageSize = pageQuery.value.pageSize) {
  if (busy.value || deleteTarget.value) return
  pageQuery.value = { page, pageSize }
  userPage.value = null
  await run('list', async (token, signal) => {
    let result = await fetchUserPage(page, pageSize, token, signal, accountQuery.value)
    // 删除或并发修改可能让当前页越界，按服务端实际总数重新请求合法页。
    while (result.page > lastUserPage(result)) {
      const last = lastUserPage(result)
      pageQuery.value = { page: last, pageSize: result.pageSize }
      result = await fetchUserPage(last, result.pageSize, token, signal, accountQuery.value)
    }
    return result
  }, (result) => {
    userPage.value = result
    pageQuery.value = { page: result.page, pageSize: result.pageSize }
  })
}

async function searchUsers() {
  if (busy.value || deleteTarget.value) return
  const account = accountInput.value.trim()
  searchError.value = ''
  if (account.length > 32) {
    searchError.value = '账号搜索内容不能超过 32 个字符。'
    return
  }
  if (account && !/^[A-Za-z0-9_]+$/.test(account)) {
    searchError.value = '账号搜索仅支持英文字母、数字或下划线。'
    return
  }
  // 使用已提交的条件刷新列表，输入框后续改动不会影响翻页及操作回读。
  listNotice.value = ''
  accountQuery.value = account
  await loadPage(1)
}

async function resetSearch() {
  if (busy.value || deleteTarget.value) return
  accountInput.value = ''
  await searchUsers()
}

function selectDelete(target: DeletionTarget) {
  if (busy.value || deleteTarget.value) return
  const id = parseUserId(target.id)
  if (!id || writeRestriction(id, currentAccountId)) return
  deleteError.value = ''
  listNotice.value = ''
  deleteTarget.value = { ...target, id }
}

function cancelDelete() {
  if (!busy.value) deleteTarget.value = null
}

async function confirmDelete() {
  const target = deleteTarget.value
  if (!target) return
  const id = parseUserId(target.id)
  if (!id) return
  const deleted = await run('delete', (token, signal) => deleteUser(id, token, signal), () => {
    deleteTarget.value = null
    if (baseline.value?.id === id) newUser()
    if (lookupId.value === id) lookupId.value = ''
    listNotice.value = '已删除账号 ' + target.account + '。'
  }, id)
  // 删除成功后关闭确认框。后续失败只能重试列表查询，不能重发删除。
  if (deleted) await loadPage()
}

async function loadUser(rowId?: string) {
  lookupError.value = ''
  const id = parseUserId(rowId ?? lookupId.value)
  if (!id) {
    lookupError.value = '请输入有效的正整数用户 ID。'
    return
  }
  await run('lookup', (token, signal) => fetchUser(id, token, signal), (details) => {
    applyDetails(details, id)
    saveError.value = ''
    feedback.value = ''
    focusDetails()
  })
}

async function refreshSavedDetails() {
  const id = baseline.value?.id
  if (!id) return
  // 写入已成功时，回读失败只重试 GET，避免再次发送 PATCH。
  await run('save', (token, signal) => fetchUser(id, token, signal), (details) => applyDetails(details, id))
}

function restoreUnchangedExpiry() {
  // 后端不接受用 null 清除到期时间，空输入恢复已保存值。
  if (!draft.vipExpiresAt.trim() && baseline.value) {
    const saved = baseline.value.fields.vipExpiresAt
    draft.vipExpiresAt = saved ? toLocalDateTime(saved) ?? '' : ''
  }
}

async function saveUser() {
  if (busy.value || detailsRestriction.value || detailsNeedRefresh.value) return
  saveError.value = ''
  feedback.value = ''
  restoreUnchangedExpiry()
  const original = baseline.value
  const before = original?.fields
  const expiryInput = draft.vipExpiresAt.trim()
  const savedExpiry = before?.vipExpiresAt ?? null
  // 未修改的本地时间沿用原始 UTC，尤其避免夏令时重复时刻和秒小数被改写。
  const expiry = savedExpiry && expiryInput === toLocalDateTime(savedExpiry)
    ? savedExpiry : expiryInput ? toUtcDateTime(expiryInput) : null
  if (expiryInput && !expiry) {
    saveError.value = '请输入有效的本地时间（' + browserTimeZone + '），例如 2026-12-01 18:00:00。'
    return
  }
  const fields: UserFields = {
    account: draft.account === before?.account ? before.account : draft.account.trim(),
    // 昵称使用空字符串清除，null 和省略字段均沿用服务端原值。
    displayName: before && draft.displayName === (before.displayName ?? '')
      ? before.displayName : before ? draft.displayName.trim() : draft.displayName.trim() || null,
    platformRole: draft.platformRole,
    status: draft.status,
    vipExpiresAt: expiry,
  }
  const changes = before ? changedFields(before, fields) : fields

  if (changes.account !== undefined && !/^[A-Za-z0-9_]{3,16}$/.test(changes.account)) {
    saveError.value = '账号须为 3–16 个英文字母、数字或下划线。'
    return
  }
  if (changes.displayName && changes.displayName.length > 64) {
    saveError.value = '昵称不能超过 64 个字符。'
    return
  }
  if (fields.platformRole === 'VIP' && !fields.vipExpiresAt) {
    saveError.value = 'VIP 用户必须设置到期时间。'
    return
  }
  // 后端在切换为 VIP 时要求同时提交到期时间。
  if (changes.platformRole === 'VIP') changes.vipExpiresAt = fields.vipExpiresAt
  if (changes.vipExpiresAt && !utcTimeKey(changes.vipExpiresAt)) {
    saveError.value = '请输入有效的 VIP 到期时间。'
    return
  }

  if (original) {
    const id = original.id
    if (!id) {
      saveError.value = '请使用准确的用户 ID 重新加载后再编辑。'
      return
    }
    if (Object.keys(changes).length === 0) {
      feedback.value = '没有可保存的改动。'
      return
    }
    const saved = await run('save', (token, signal) => updateUser(id, changes, token, signal), () => {
      detailsNeedRefresh.value = true
      feedback.value = '用户资料已保存。'
    }, id)
    if (saved) {
      await refreshSavedDetails()
      await loadPage()
    }
    return
  }

  // 初始密码由服务端统一设置，默认角色和状态也由后端补齐。
  const body = {
    account: fields.account,
    displayName: fields.displayName ?? undefined,
    platformRole: fields.platformRole === 'USER' ? undefined : fields.platformRole,
    status: fields.status === 'ACTIVE' ? undefined : fields.status,
    vipExpiresAt: fields.vipExpiresAt ?? undefined,
  }
  const saved = await run('save', (token, signal) => createUser(body, token, signal), () => {
    newUser()
    feedback.value = '用户已创建。'
  })
  if (saved) await loadPage(1)
}

onMounted(() => loadPage())
onBeforeUnmount(() => request?.abort())
</script>

<template>
  <main class="admin-users">
    <header class="page-header">
      <div>
        <h1>用户管理</h1>
        <p class="page-description">管理平台账号与访问权限</p>
      </div>
      <button class="primary create-user" type="button" :disabled="busy !== null" @click="startNewUser">
        <span aria-hidden="true">＋</span> 新增用户
      </button>
    </header>

    <div class="lookup-toolbar">
      <form class="account-search" role="search" aria-label="按账号搜索用户" novalidate @submit.prevent="searchUsers">
        <label for="account-search">账号</label>
        <input
          id="account-search" v-model="accountInput" placeholder="输入账号或账号片段"
          autocomplete="off" autocapitalize="none" spellcheck="false"
          :readonly="busy !== null" :aria-invalid="Boolean(searchError) || undefined"
          :aria-describedby="searchError ? 'account-search-error' : undefined"
        />
        <button class="secondary" type="submit" :disabled="busy !== null">搜索</button>
        <button v-if="accountQuery || accountInput" class="secondary" type="button" :disabled="busy !== null" @click="resetSearch">重置</button>
      </form>
      <form class="lookup" novalidate @submit.prevent="loadUser()">
        <label for="user-id">用户 ID</label>
        <input id="user-id" v-model="lookupId" placeholder="输入用户 ID" inputmode="numeric" :readonly="busy !== null" required />
        <button class="secondary" type="submit" :disabled="busy !== null">
          {{ busy === 'lookup' ? '正在查询…' : '查询' }}
        </button>
      </form>
      <p v-if="searchError" id="account-search-error" class="error" role="alert">{{ searchError }}</p>
      <p v-if="lookupError" class="error" role="alert">{{ lookupError }}</p>
    </div>
    <p v-if="accountQuery" class="search-summary">账号包含“{{ accountQuery }}”的搜索结果</p>

    <AdminUserList
      :result="userPage"
      :current-account-id="currentAccountId"
      :page-size="pageQuery.pageSize"
      :loading="busy === 'list'"
      :busy="busy !== null"
      :error="listError"
      :notice="listNotice"
      :delete-target="deleteTarget"
      :deleting="busy === 'delete'"
      :delete-error="deleteError"
      @paginate="loadPage"
      @retry="loadPage()"
      @edit="loadUser"
      @select-delete="selectDelete"
      @cancel-delete="cancelDelete"
      @confirm-delete="confirmDelete"
    />

    <dialog ref="drawer" class="user-drawer" aria-labelledby="details-title" @cancel.prevent="closeDrawer">
      <section class="panel details" aria-labelledby="details-title">
        <button class="close-drawer" type="button" aria-label="关闭用户详情" :disabled="busy !== null" @click="closeDrawer">×</button>
        <header class="details-header">
          <span v-if="baseline" class="avatar" aria-hidden="true">
            {{ baseline.fields.account.slice(0, 1).toUpperCase() }}
          </span>
          <div>
            <h2 id="details-title" ref="detailsTitle" tabindex="-1">
              {{ detailsRestriction ? '查看用户' : baseline ? '编辑用户' : '新增用户' }}
            </h2>
            <p v-if="baseline?.id" class="hint">用户 ID {{ baseline.id }}</p>
            <p v-if="detailsRestriction" class="hint">{{ detailsRestriction }}</p>
          </div>
        </header>

        <form novalidate @submit.prevent="saveUser">
          <fieldset :disabled="busy !== null || Boolean(detailsRestriction) || detailsNeedRefresh">
            <div class="field">
              <label for="managed-account">登录账号</label>
              <input id="managed-account" v-model="draft.account" autocomplete="off" autocapitalize="none" spellcheck="false" required />
              <p class="hint">3–16 个英文字母、数字或下划线，区分大小写。</p>
            </div>
            <p v-if="!baseline" class="hint">新账号的初始密码为 12345678。</p>
            <div class="field">
              <label for="managed-nickname">昵称</label>
              <input id="managed-nickname" v-model="draft.displayName" autocomplete="off" />
              <p class="hint">{{ baseline ? '最多 64 个字符，留空可清除昵称。' : '选填，最多 64 个字符。' }}</p>
            </div>
            <div class="select-row">
              <div class="field">
                <label for="managed-role">平台角色</label>
                <select id="managed-role" v-model="draft.platformRole">
                  <option value="USER">普通用户</option>
                  <option value="VIP">VIP</option>
                  <option value="ADMIN">管理员</option>
                </select>
              </div>
              <div class="field">
                <label for="managed-status">账号状态</label>
                <select id="managed-status" v-model="draft.status">
                  <option value="ACTIVE">正常</option>
                  <option value="DISABLED">停用</option>
                </select>
              </div>
            </div>
            <div class="field">
              <label for="managed-expiry">VIP 到期时间（本地时间）</label>
              <input
                id="managed-expiry"
                v-model="draft.vipExpiresAt"
                placeholder="2026-12-01 18:00:00"
                aria-describedby="expiry-hint"
                autocomplete="off"
                spellcheck="false"
                :required="draft.platformRole === 'VIP'"
                @blur="restoreUnchangedExpiry"
              />
              <p id="expiry-hint" class="hint">时区 {{ browserTimeZone }}，格式为 年-月-日 时:分:秒，可带秒小数。{{ baseline ? '留空保留原到期时间。' : 'VIP 必填，其他角色可留空。' }}</p>
            </div>
          </fieldset>
          <p v-if="baseline" class="hint">以下时间按本地时区 {{ browserTimeZone }} 显示</p>
          <dl v-if="baseline" class="audit-times" aria-label="账号时间（本地时区）">
            <div>
              <dt>创建时间</dt>
              <dd><UserTime :value="baseline.times.createdAt" /></dd>
            </div>
            <div>
              <dt>自行修改时间</dt>
              <dd><UserTime :value="baseline.times.editTime" empty-text="尚未自行修改" /></dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd><UserTime :value="baseline.times.updateTime" /></dd>
            </div>
          </dl>
          <p v-if="saveError" class="error" role="alert">{{ saveError }}</p>
          <p v-if="feedback" class="feedback" role="status">{{ feedback }}</p>
          <p v-if="detailsNeedRefresh && !busy" class="hint">修改已保存，请重新加载详情后继续编辑。</p>
          <div class="form-actions">
            <button class="secondary" type="button" :disabled="busy !== null" @click="closeDrawer">{{ detailsRestriction ? '关闭' : '取消' }}</button>
            <button v-if="detailsNeedRefresh" class="primary" type="button" :disabled="busy !== null" @click="refreshSavedDetails">重新加载详情</button>
            <button v-else-if="!detailsRestriction" class="primary" type="submit" :disabled="busy !== null">
              {{ busy === 'save' ? '正在保存…' : baseline ? '保存修改' : '创建用户' }}
            </button>
          </div>
        </form>
      </section>
    </dialog>
  </main>
</template>

<style scoped>
.admin-users {
  max-width: 1560px;
  margin: 0 auto;
  padding: clamp(24px, 3.3vw, 48px);
}

.page-header, .details-header { display: flex; align-items: center; gap: 16px; }
.page-header { justify-content: space-between; margin-bottom: 30px; }
.page-header h1 { font-size: 1.65rem; letter-spacing: -0.04em; }
.page-description { margin-top: 9px; font-size: 0.8rem; color: #879181; }
.create-user { display: flex; align-items: center; gap: 6px; white-space: nowrap; font-size: 0.85rem; }
.create-user span { font-size: 21px; font-weight: 400; }
.lookup-toolbar { display: flex; flex-wrap: wrap; gap: 18px 32px; margin-bottom: 20px; }
.lookup-toolbar form { display: flex; align-items: center; gap: 12px; margin: 0; flex-wrap: wrap; }
.lookup-toolbar input { width: 248px; max-width: 100%; background: #fff; font-size: 0.85rem; }
.lookup-toolbar .lookup input { width: 150px; }
.lookup-toolbar label { color: #626f5e; font-weight: 500; }
.lookup-toolbar button { min-width: 76px; background: white; font-size: 0.85rem; }
.lookup-toolbar .error { margin-top: 10px; }
.search-summary { margin-bottom: 14px; color: #788572; font-size: 0.8rem; overflow-wrap: anywhere; }
.user-drawer { position: fixed; inset: 0 0 0 auto; margin: 0; width: min(100%, 480px); height: 100%; max-height: 100svh; max-width: 100%; padding: 0; border: 0; background: #fff; color: #182925; box-shadow: -16px 0 60px #183d311a; }
.user-drawer::backdrop { background: #18292566; }
.user-drawer .details { position: relative; min-height: 100%; padding: 36px 32px; }
.user-drawer .details-header { padding-bottom: 24px; border-bottom: 1px solid #e5e9e0; margin-right: 30px; }
.close-drawer { position: absolute; top: 26px; right: 20px; border: 0; font-size: 28px; background: transparent; color: #7b8875; }
.form-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 18px; padding-top: 22px; border-top: 1px solid #e5e9e0; }

h1,
h2,
p {
  margin: 0;
}

h2 {
  font-size: 1.25rem;
}

#details-title:focus { outline: none; }

.audit-times {
  display: grid;
  gap: 16px;
  margin: 12px 0 0;
  padding-top: 24px;
  border-top: 1px solid #e5e9e0;
}

.audit-times > div {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: start;
  gap: 12px;
}

.audit-times dt {
  color: #788572;
  font-size: 0.8rem;
}

.audit-times dd {
  margin: 0;
}

form,
fieldset,
.field {
  display: grid;
}

form {
  gap: 14px;
  margin-top: 20px;
}

fieldset {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
  gap: 18px;
}

.field {
  gap: 7px;
}

.select-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

label {
  font-size: 0.875rem;
  font-weight: 600;
}

input,
select,
button {
  min-width: 0;
  min-height: 44px;
  padding: 8px 12px;
  border: 1px solid #b9c2b9;
  border-radius: 9px;
  color: inherit;
  background: #fdfdf9;
  font: inherit;
}

:is(input, select, button, a):focus-visible {
  outline: 2px solid #2e6154;
  outline-offset: 2px;
}

button {
  font-weight: 600;
  cursor: pointer;
}

button:disabled {
  opacity: 0.7;
  cursor: wait;
}

.primary {
  justify-self: start;
  color: #fffdf3;
  border-color: #365c52;
  background: #365c52;
}

.secondary {
  color: #224c42;
}

.avatar {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  color: #224c42;
  background: #e4eee5;
  font-weight: 700;
}

.hint,
.error,
.feedback {
  font-size: 0.875rem;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.hint {
  color: #525f59;
}

.error,
.feedback {
  padding: 10px 12px;
  border-radius: 8px;
}

.error {
  color: #862a2b;
  background: #fbeceb;
}

.feedback {
  color: #224c42;
  background: #e6eee6;
}

@media (max-width: 680px) {
  .admin-users { padding: 24px 18px; }
  .page-header { gap: 12px; }
  .page-header h1 { font-size: 1.4rem; }
  .page-description { line-height: 1.6; }
  .lookup-toolbar form { width: 100%; }
  .lookup-toolbar input, .lookup-toolbar .lookup input { flex: 1; width: 140px; }
  .lookup-toolbar form { gap: 8px; }
  .user-drawer .details { padding: 32px 24px; }
}
</style>
