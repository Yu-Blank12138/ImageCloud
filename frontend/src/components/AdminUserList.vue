<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { actionableUserId, lastUserPage, type AdminUserDetails, type AdminUserPage, type DeletionTarget } from '../admin/users'
import UserTime from './UserTime.vue'

const props = defineProps<{
  result: AdminUserPage | null
  currentAccountId: string | null
  selfUser?: AdminUserDetails | null
  pageSize: number
  loading: boolean
  busy: boolean
  error: string
  notice: string
  deleteTarget: DeletionTarget | null
  deleting: boolean
  deleteError: string
}>()

const emit = defineEmits<{
  paginate: [page: number, pageSize: number]
  retry: []
  edit: [id: string]
  selectDelete: [target: DeletionTarget]
  cancelDelete: []
  confirmDelete: []
}>()

const roles = { USER: '普通用户', VIP: 'VIP', ADMIN: '管理员' }
// 每页条数只提供 10、20、30 三档，最多 30 条。
const PAGE_SIZES = [10, 20, 30]
const dialog = ref<HTMLDialogElement | null>(null)

function toRow(user: AdminUserDetails) {
  return {
    id: actionableUserId(user.id),
    account: user.account,
    displayName: user.displayName,
    role: roles[user.platformRole],
    status: user.status === 'ACTIVE' ? '正常' : '停用',
    createdAt: user.createdAt,
    editTime: user.editTime,
    updateTime: user.updateTime,
  }
}

const rows = computed(() => {
  // 当前登录用户由会话单独查询后固定在第一行，并从分页结果中移除避免重复。
  const pinned = props.selfUser ? toRow(props.selfUser) : null
  const records = (props.result?.records ?? []).map(toRow)
    .filter((row) => !pinned || row.id !== pinned.id)
  if (pinned) return [pinned, ...records]
  // 本人详情尚未返回时，仍把当前页内出现的本人置顶。
  const index = records.findIndex((row) => row.id === props.currentAccountId)
  if (index > 0) records.unshift(...records.splice(index, 1))
  return records
})
const lastPage = computed(() => props.result ? lastUserPage(props.result) : 1)

watch(() => props.deleteTarget, (target) => {
  // 原生模态框限制焦点，取消和关闭均由父组件确认请求状态。
  if (target && !dialog.value?.open) dialog.value?.showModal()
  if (!target && dialog.value?.open) dialog.value.close()
}, { flush: 'post' })

// 切换每页条数后回到第一页，合法值之外的输入不予提交。
function applyPageSize(event: Event) {
  const size = Number((event.target as HTMLSelectElement).value)
  if (PAGE_SIZES.includes(size) && size !== props.pageSize) emit('paginate', 1, size)
}
</script>

<template>
  <section class="user-list" aria-labelledby="user-list-title">
    <header class="list-header">
      <div>
        <h2 id="user-list-title">用户列表</h2>
        <p class="time-zone">时间按记录原值显示</p>
      </div>
      <div class="size-form">
        <label for="user-page-size">每页条数</label>
        <select
          id="user-page-size"
          :value="pageSize"
          :disabled="busy"
          @change="applyPageSize"
        >
          <option v-for="size in PAGE_SIZES" :key="size" :value="size">{{ size }}</option>
        </select>
      </div>
    </header>

    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <div v-if="error" class="list-error">
      <p role="alert">列表加载失败。{{ error }}</p>
      <button type="button" :disabled="busy" @click="emit('retry')">重试列表</button>
    </div>
    <p v-else-if="loading" class="empty" role="status">正在加载用户列表…</p>

    <template v-if="result">
      <div v-if="rows.length" class="table-scroll">
        <table aria-labelledby="user-list-title">
          <thead>
            <tr>
              <th scope="col">ID</th>
              <th scope="col">账号</th>
              <th scope="col">昵称</th>
              <th scope="col">角色</th>
              <th scope="col">状态</th>
              <th scope="col">创建时间</th>
              <th scope="col">自行修改时间</th>
              <th scope="col">更新时间</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.account">
              <td>{{ row.id ?? '—' }}</td>
              <th scope="row">
                <div class="table-user">
                  <span class="table-avatar" aria-hidden="true">{{ row.account.slice(0, 1).toUpperCase() }}</span>
                  <div>{{ row.account }}<span v-if="row.id && row.id === currentAccountId" class="current-account">当前账号</span></div>
                </div>
              </th>
              <td>{{ row.displayName || '—' }}</td>
              <td><span class="role-label" :class="{ 'admin-role': row.role === '管理员' }">{{ row.role }}</span></td>
              <td><span class="status-label" :class="{ disabled: row.status === '停用' }">{{ row.status }}</span></td>
              <td><UserTime :value="row.createdAt" /></td>
              <td><UserTime :value="row.editTime" empty-text="尚未自行修改" /></td>
              <td><UserTime :value="row.updateTime" /></td>
              <td>
                <div v-if="row.id && currentAccountId && row.id !== currentAccountId" class="row-actions">
                  <button
                    type="button"
                    :aria-label="'编辑 ' + row.account"
                    :disabled="busy"
                    @click="emit('edit', row.id)"
                  >编辑</button>
                  <button
                    class="danger"
                    type="button"
                    :aria-label="'删除 ' + row.account"
                    :disabled="busy"
                    @click="emit('selectDelete', { id: row.id, account: row.account })"
                  >删除</button>
                </div>
                <span v-else-if="row.id" class="hint">仅可查看</span>
                <span v-else class="hint">需通过原始 ID 查询</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="empty">暂无用户。</p>
      <footer class="pagination" aria-label="用户列表分页">
        <p>第 {{ result.page }} / {{ lastPage }} 页，共 {{ result.total }} 条</p>
        <div class="row-actions">
          <button
            type="button"
            :disabled="busy || result.page <= 1"
            @click="emit('paginate', result.page - 1, result.pageSize)"
          >上一页</button>
          <button
            type="button"
            :disabled="busy || result.page >= lastPage"
            @click="emit('paginate', result.page + 1, result.pageSize)"
          >下一页</button>
        </div>
      </footer>
    </template>

    <dialog ref="dialog" aria-labelledby="delete-title" aria-describedby="delete-description" @cancel.prevent="emit('cancelDelete')">
      <template v-if="deleteTarget">
        <h2 id="delete-title">删除账号 {{ deleteTarget.account }}？</h2>
        <p id="delete-description">用户 ID {{ deleteTarget.id }}。删除后账号无法登录，原登录名仍保留。</p>
        <p v-if="deleteError" class="delete-error" role="alert">{{ deleteError }}</p>
        <div class="dialog-actions">
          <button type="button" autofocus :disabled="deleting" @click="emit('cancelDelete')">取消</button>
          <button class="delete-confirm" type="button" :disabled="deleting" @click="emit('confirmDelete')">
            {{ deleting ? '正在删除…' : '确认删除' }}
          </button>
        </div>
      </template>
    </dialog>
  </section>
</template>

<style scoped>
.user-list {
  margin-bottom: 20px;
  padding: clamp(18px, 3vw, 28px);
  border: 1px solid #e1e6db;
  border-radius: 12px;
  background: #fff;
}

.list-header,
.size-form,
.pagination,
.row-actions,
.dialog-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.list-header,
.pagination {
  justify-content: space-between;
}

h2 {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
}

p {
  margin: 0;
  line-height: 1.5;
}

.size-form,
.pagination,
.hint {
  color: #525f59;
  font-size: 0.875rem;
}

.current-account {
  display: inline-block;
  margin-inline-start: 6px;
  color: #365c52;
  font-size: 0.75rem;
}

.table-user { display: flex; align-items: center; gap: 12px; }
.table-avatar { display: grid; place-items: center; flex-shrink: 0; width: 34px; height: 34px; border-radius: 50%; background: #edf1e7; color: #637d58; font-weight: 500; }
.table-user > div { white-space: nowrap; }
.role-label { color: #64735c; font-size: 0.8rem; }
.admin-role { padding: 4px 9px; border-radius: 5px; background: #eef3e8; color: #55753f; }
.status-label { display: inline-flex; align-items: center; gap: 6px; color: #4d6a4c; white-space: nowrap; font-size: 0.8rem; }
.status-label::before { content: ''; width: 6px; height: 6px; border-radius: 50%; background: #7ea064; }
.status-label.disabled { color: #8a9184; }
.status-label.disabled::before { background: #bac0b3; }

button,
input {
  min-height: 40px;
  padding: 6px 10px;
  border: 1px solid #b9c2b9;
  border-radius: 8px;
  color: #224c42;
  background: #fdfdf9;
  font: inherit;
}

input {
  width: 68px;
}

button {
  font-weight: 600;
  cursor: pointer;
}

button:disabled {
  opacity: 0.55;
  cursor: default;
}

:is(button, input):focus-visible {
  outline: 2px solid #2e6154;
  outline-offset: 2px;
}

.table-scroll {
  overflow-x: auto;
  margin-top: 15px;
}

table {
  width: 100%;
  min-width: 1080px;
  border-collapse: collapse;
  font-size: 0.82rem;
}

.time-zone { margin-top: 7px; color: #7b8774; font-size: 0.75rem; font-weight: 400; }

th,
td {
  padding: 16px 14px;
  border-bottom: 1px solid #eef0e8;
  text-align: left;
  overflow-wrap: anywhere;
}

thead {
  background: #f7f8f4;
  color: #8a9481;
}

thead th { padding-block: 13px; font-size: 0.75rem; font-weight: 500; }
tbody th { font-weight: 500; }
tbody tr:hover { background: #fcfdf9; }
tbody .row-actions button { min-height: 30px; padding: 4px 7px; border: none; background: transparent; font-size: 0.8rem; font-weight: 500; }
.pagination { padding-top: 12px; }

td:nth-child(3) {
  max-width: 160px;
}

td:last-child .row-actions {
  flex-wrap: nowrap;
}

.pagination,
.empty,
.notice,
.list-error {
  margin-top: 14px;
}

.notice,
.list-error,
.delete-error {
  padding: 10px 12px;
  border-radius: 8px;
}

.notice {
  color: #224c42;
  background: #e6eee6;
}

.list-error,
.delete-error {
  color: #862a2b;
  background: #fbeceb;
}

.list-error button {
  margin-top: 8px;
}

.danger,
.delete-confirm {
  color: #862a2b;
}

dialog {
  width: min(100% - 48px, 440px);
  padding: 24px;
  border: 1px solid #d2d7cb;
  border-radius: 16px;
  color: #182925;
  background: #fafaf5;
}

dialog::backdrop {
  background: rgb(24 41 37 / 55%);
}

dialog p,
.dialog-actions {
  margin-top: 12px;
}

.dialog-actions {
  justify-content: flex-end;
}

.delete-confirm {
  border-color: #862a2b;
  color: #fffdf3;
  background: #862a2b;
}
</style>
