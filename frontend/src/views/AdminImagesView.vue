<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '../api/http'
import { isInvalidSessionError } from '../auth/api'
import { getActiveSession, invalidateSession } from '../auth/session'
import {
  fetchImage, fetchImages, imageId, saveImage, uploadAdminImage,
  type AdminImage, type ImageFilters, type ImagePage, type UploadedImage,
} from '../images/admin'
import { imageDraft, imagePayload } from '../images/form'
import AdminImagePreview from '../components/AdminImagePreview.vue'
import UserTime from '../components/UserTime.vue'

const router = useRouter()
const result = ref<ImagePage | null>(null)
const page = ref(1)
const pageSize = ref(20)
const filters = reactive({ id: '', name: '', category: '', spaceId: '', uploaderId: '', auditStatus: '', vipOnly: '', createdAtStart: '', createdAtEnd: '' })
const appliedFilters = ref<ImageFilters>({})
const busy = ref('')
const listError = ref('')
const formError = ref('')
const uploadError = ref('')
const notice = ref('')
const forbidden = ref(getActiveSession()?.user.platformRole !== 'ADMIN')
const drawer = ref<HTMLDialogElement | null>(null)
const heading = ref<HTMLElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const mode = ref<'new' | 'edit' | 'details'>('new')
const baseline = ref<AdminImage | null>(null)
const targetId = ref<string | null>(null)
const uploaded = ref<UploadedImage | null>(null)
const draft = reactive(imageDraft())
const mustUpload = ref(false)
const saved = ref(false)
const previewGeneration = ref(0)
const showOriginal = ref(false)
let controller: AbortController | undefined
let disposed = false
const lastPage = computed(() => Math.max(1, Math.ceil((result.value?.total ?? 0) / pageSize.value)))
const previewSrc = computed(() => uploaded.value
  ? showOriginal.value ? uploaded.value.imageUrl : uploaded.value.thumbnailUrl
  : showOriginal.value ? baseline.value?.originalUrl : baseline.value?.thumbnailUrl)

// 每次响应核对会话和页面生命周期，防止换号后把旧管理员数据带回页面。
async function run<T>(operation: string, error: typeof listError, action: (token: string, signal: AbortSignal) => Promise<T>, apply: (value: T) => void) {
  if (busy.value || disposed) return false
  const session = getActiveSession()
  if (!session) { await router.replace({ name: 'login' }); return false }
  if (forbidden.value || session.user.platformRole !== 'ADMIN') {
    forbidden.value = true
    return false
  }
  const request = new AbortController()
  controller = request
  busy.value = operation
  error.value = ''
  try {
    const value = await action(session.accessToken, request.signal)
    if (disposed || request.signal.aborted || getActiveSession()?.accessToken !== session.accessToken) return false
    apply(value)
    return true
  } catch (failure) {
    if (disposed || request.signal.aborted || getActiveSession()?.accessToken !== session.accessToken) return false
    if (isInvalidSessionError(failure)) {
      if (invalidateSession(session.accessToken)) await router.replace({ name: 'login' })
    } else {
      if (failure instanceof ApiError && failure.code === 4030) {
        forbidden.value = true
        result.value = null
        drawer.value?.close()
      }
      error.value = failure instanceof Error ? failure.message : '请求失败，请稍后重试。'
    }
    return false
  } finally {
    if (controller === request) { controller = undefined; busy.value = '' }
  }
}

async function loadPage(requestedPage = page.value) {
  await run('list', listError, async (token, signal) => {
    let value = await fetchImages(requestedPage, pageSize.value, appliedFilters.value, token, signal)
    while (value.page > Math.max(1, Math.ceil(value.total / value.pageSize))) {
      const last = Math.max(1, Math.ceil(value.total / value.pageSize))
      value = await fetchImages(last, value.pageSize, appliedFilters.value, token, signal)
    }
    return value
  }, value => { result.value = value; page.value = value.page; pageSize.value = value.pageSize })
}

async function search(reset = false) {
  if (busy.value) return
  listError.value = ''
  try {
    if (reset) for (const key of Object.keys(filters) as (keyof typeof filters)[]) filters[key] = ''
    const query: ImageFilters = {}
    for (const key of ['id', 'spaceId', 'uploaderId'] as const) {
      if (!filters[key].trim()) continue
      const id = imageId(filters[key])
      if (!id) throw new Error('图片、空间和上传用户 ID 必须是有效正整数。')
      query[key] = id
    }
    for (const key of ['name', 'category'] as const) {
      const value = filters[key].trim()
      if (value.length > 16) throw new Error('名称和分类搜索最多 16 个字符。')
      if (value) query[key] = value
    }
    for (const key of ['auditStatus', 'vipOnly'] as const) if (filters[key] !== '') query[key] = filters[key]
    for (const key of ['createdAtStart', 'createdAtEnd'] as const) {
      if (!filters[key]) continue
      // 日期选择器只提供年月日，展开为当天起止边界，与后端秒精度时间对齐。
      if (!/^\d{4}-\d{2}-\d{2}$/.test(filters[key])) throw new Error('请输入有效的创建时间。')
      query[key] = key === 'createdAtStart' ? filters[key] + 'T00:00:00' : filters[key] + 'T23:59:59'
    }
    if (query.createdAtStart && query.createdAtEnd &&
      query.createdAtStart.slice(0, 10) > query.createdAtEnd.slice(0, 10)) {
      throw new Error('结束时间不能早于开始时间。')
    }
    appliedFilters.value = query
    result.value = null
    await loadPage(1)
  } catch (failure) { listError.value = (failure as Error).message }
}

function applyDetails(value: AdminImage) {
  baseline.value = value
  Object.assign(draft, imageDraft(value))
  uploaded.value = null
  mustUpload.value = false
  saved.value = false
  showOriginal.value = false
  previewGeneration.value++
}

async function openDrawer() {
  if (!drawer.value?.open) drawer.value?.showModal()
  await nextTick()
  heading.value?.focus()
}

function closeDrawer() { if (!busy.value) drawer.value?.close() }
function newImage() {
  if (busy.value || forbidden.value) return
  mode.value = 'new'
  baseline.value = null
  targetId.value = null
  Object.assign(draft, imageDraft())
  uploaded.value = null
  mustUpload.value = false
  saved.value = false
  showOriginal.value = false
  formError.value = ''; uploadError.value = ''; notice.value = ''
  if (fileInput.value) fileInput.value.value = ''
  void openDrawer()
}

async function openImage(value: AdminImage, edit = false) {
  const id = imageId(value.id)
  if (!id) { listError.value = '图片 ID 无法精确表示，暂无法操作该记录。'; return }
  const loaded = await run('details', listError, (token, signal) => fetchImage(id, token, signal), details => {
    targetId.value = id
    mode.value = edit ? 'edit' : 'details'
    applyDetails(details)
    formError.value = ''; uploadError.value = ''; notice.value = ''
  })
  if (loaded) await openDrawer()
}

async function uploadFile(event: Event) {
  if (busy.value || saved.value || mode.value === 'details' || forbidden.value) return
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploaded.value = null
  uploadError.value = ''
  // 选择替换图片后必须完成本次上传，避免误提交旧图。
  mustUpload.value = true
  await run('upload', uploadError, (token, signal) => uploadAdminImage(file, token, signal), value => {
    uploaded.value = value
    mustUpload.value = false
    previewGeneration.value++
  })
}

async function refreshDetails() {
  if (!targetId.value) return
  await run('refresh', formError, (token, signal) => fetchImage(targetId.value!, token, signal), applyDetails)
}

async function retryPreview() {
  if (uploaded.value) { previewGeneration.value++; return }
  if (!targetId.value) return
  // 重新签发预览地址只更新展示地址，不覆盖正在编辑的元数据。
  await run('preview', formError, (token, signal) => fetchImage(targetId.value!, token, signal), value => {
    if (baseline.value) { baseline.value.originalUrl = value.originalUrl; baseline.value.thumbnailUrl = value.thumbnailUrl }
    previewGeneration.value++
  })
}

async function submit() {
  // 写入和回读分开，已保存后只允许 GET 重试，防止重复创建或修改。
  if (busy.value || saved.value || mode.value === 'details' || forbidden.value) return
  formError.value = ''
  let payload
  try {
    if (mustUpload.value) throw new Error('请重新上传图片后再保存。')
    payload = imagePayload(draft, baseline.value, uploaded.value)
  } catch (failure) { formError.value = (failure as Error).message; return }
  const updated = targetId.value !== null
  const ok = await run('save', formError, (token, signal) => saveImage(targetId.value, payload, token, signal), () => {
    saved.value = true
    uploaded.value = null
    notice.value = updated ? '图片修改已保存。' : '图片已创建，请在列表中查看。'
  })
  if (!ok) {
    // 写入失败后遵循业务约定，要求重新上传，保留已填写的文字。
    uploaded.value = null
    mustUpload.value = true
    return
  }
  // 已确认写入成功，回读失败单独反馈，不能引导用户重复创建。
  if (updated) await refreshDetails()
  else closeDrawer()
  await loadPage(updated ? page.value : 1)
}

onMounted(() => { if (!forbidden.value) void loadPage() })
onBeforeUnmount(() => { disposed = true; controller?.abort(); drawer.value?.close() })
</script>

<template>
  <main class="admin-images">
    <header class="page-header">
      <div><h1>图片管理</h1><p class="hint">管理图片信息、空间归属与访问限制</p></div>
      <button class="primary" :disabled="Boolean(busy) || forbidden" @click="newImage">＋ 新增图片</button>
    </header>
    <p v-if="forbidden" class="error" role="alert">当前账号没有图片管理权限，请使用管理员账号访问。</p>
    <template v-else>
      <form class="filters" role="search" aria-label="筛选图片" @submit.prevent="search()">
        <fieldset :disabled="Boolean(busy)">
          <label>名称<input v-model="filters.name" maxlength="16" placeholder="名称片段" /></label>
          <label>分类<input v-model="filters.category" maxlength="16" placeholder="分类片段" /></label>
          <label>图片 ID<input v-model="filters.id" inputmode="numeric" /></label>
          <label>空间 ID<input v-model="filters.spaceId" inputmode="numeric" /></label>
          <label>上传用户 ID<input v-model="filters.uploaderId" inputmode="numeric" /></label>
          <label>审核状态<select v-model="filters.auditStatus"><option value="">全部</option><option value="0">未审核</option><option value="1">通过</option><option value="2">不通过</option></select></label>
          <label>查看限制<select v-model="filters.vipOnly"><option value="">全部</option><option value="0">无限制</option><option value="1">仅 VIP</option></select></label>
          <label>创建时间起<input v-model="filters.createdAtStart" type="date" /></label>
          <label>创建时间止<input v-model="filters.createdAtEnd" type="date" /></label>
        </fieldset>
        <div class="actions"><button type="submit" :disabled="Boolean(busy)">搜索</button><button type="button" :disabled="Boolean(busy)" @click="search(true)">重置</button></div>
      </form>
      <p v-if="notice" class="success" role="status">{{ notice }}</p>
      <p v-if="listError" class="error" role="alert">{{ listError }} <button :disabled="Boolean(busy)" @click="loadPage()">重新加载列表</button></p>
      <p v-if="busy === 'list'" role="status">正在加载图片…</p>
      <p v-else-if="result && !result.records.length" class="empty">暂无符合条件的图片</p>
      <section v-if="result?.records.length" class="image-grid" aria-label="图片列表" :aria-busy="busy === 'list'">
        <article v-for="item in result.records" :key="String(item.id)" class="image-card">
          <AdminImagePreview :src="item.thumbnailUrl" :alt="item.name" @retry="loadPage()" />
          <div class="card-body"><h2>{{ item.name }}</h2><p class="hint">ID {{ item.id }} · {{ item.width }} × {{ item.height }} · {{ item.format }}</p>
            <p class="hint">{{ item.category || '未分类' }} · {{ item.vipOnly ? '仅 VIP' : '无限制' }} · {{ ['未审核', '通过', '不通过'][item.auditStatus] ?? '未知状态' }}</p>
            <div class="actions"><button :disabled="Boolean(busy)" @click="openImage(item)">详情</button><button :disabled="Boolean(busy)" @click="openImage(item, true)">编辑</button></div>
          </div>
        </article>
      </section>
      <footer class="pagination">
        <span>{{ result?.total ?? '—' }} 张 · 第 {{ page }} / {{ lastPage }} 页</span>
        <label>每页<select v-model.number="pageSize" :disabled="Boolean(busy)" @change="loadPage(1)"><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option></select></label>
        <button :disabled="Boolean(busy) || page <= 1" @click="loadPage(page - 1)">上一页</button><button :disabled="Boolean(busy) || !result || page >= lastPage" @click="loadPage(page + 1)">下一页</button>
      </footer>
    </template>

    <dialog ref="drawer" class="image-drawer" aria-labelledby="image-title" @cancel.prevent="closeDrawer">
      <section class="drawer-content">
        <button class="close" aria-label="关闭图片详情" :disabled="Boolean(busy)" @click="closeDrawer">×</button>
        <h2 id="image-title" ref="heading" tabindex="-1">{{ mode === 'new' ? '新增图片' : mode === 'edit' ? '编辑图片' : '图片详情' }}</h2>
        <p v-if="targetId" class="hint">图片 ID {{ targetId }}</p>
        <AdminImagePreview :key="previewGeneration" :src="previewSrc" :alt="draft.name || '上传预览'" @retry="retryPreview" />
        <div v-if="baseline || uploaded" class="actions"><button :disabled="Boolean(busy)" @click="showOriginal = !showOriginal">{{ showOriginal ? '查看压缩图' : '查看原图' }}</button></div>
        <form @submit.prevent="submit">
          <fieldset :disabled="Boolean(busy) || mode === 'details' || saved">
            <label>{{ baseline ? '替换图片（选填）' : '上传图片' }}<input ref="fileInput" type="file" accept=".jpg,.jpeg,.png,.webp,.gif,.bmp,.tif,.tiff" @change="uploadFile" /></label>
            <p class="hint">单张最多 20MB，支持 JPG、PNG、WebP、GIF、BMP、TIFF。先上传，再填写信息并保存。</p>
            <p v-if="busy === 'upload'" role="status">正在上传图片…</p>
            <p v-if="uploaded" class="success" role="status">上传成功，尚未保存图片信息。预览地址有效一小时，预览失败仍可保存。</p>
            <p v-if="uploadError" class="error" role="alert">{{ uploadError }}</p>
            <p v-if="mustUpload" class="hint">请重新选择图片上传后再保存，已填写的信息会保留。</p>
            <label>名称<input v-model="draft.name" maxlength="16" required /></label>
            <label>说明<textarea v-model="draft.description" maxlength="128" rows="3" /></label>
            <label>分类<input v-model="draft.category" maxlength="16" /></label>
            <label>标签<textarea v-model="draft.tags" rows="3" placeholder="一行一个标签，每项最多 20 个字符" /></label>
            <label>空间 ID<input v-model="draft.spaceId" inputmode="numeric" /></label>
            <p class="hint">{{ baseline?.spaceId != null ? '已有空间归属可保留或修改为其他空间 ID。' : '留空表示不属于任何空间。' }}</p>
            <label class="checkbox"><input v-model="draft.vipOnly" type="checkbox" />仅有效 VIP 可查看</label>
          </fieldset>
          <p v-if="formError" class="error" role="alert">{{ formError }}</p>
          <p v-if="saved" class="success" role="status">图片已保存。详情尚未回读成功，请重新加载后继续编辑。</p>
          <div class="actions form-actions">
            <button type="button" :disabled="Boolean(busy)" @click="closeDrawer">关闭</button>
            <button v-if="saved && targetId" type="button" :disabled="Boolean(busy)" @click="refreshDetails">重新加载详情</button>
            <button v-else-if="mode !== 'details'" class="primary" type="submit" :disabled="Boolean(busy) || mustUpload || (!baseline && !uploaded)">{{ busy === 'save' ? '正在保存…' : '保存图片' }}</button>
            <button v-else type="button" class="primary" @click="mode = 'edit'">编辑信息</button>
          </div>
        </form>
        <dl v-if="baseline" class="metadata">
          <div><dt>原文件名</dt><dd>{{ baseline.originalFilename }}</dd></div>
          <div><dt>图片属性</dt><dd>{{ baseline.width }} × {{ baseline.height }} · {{ baseline.format }} · {{ baseline.fileSize }} 字节</dd></div>
          <div><dt>上传用户</dt><dd>{{ baseline.uploaderId }}</dd></div>
          <div><dt>审核状态</dt><dd>{{ ['未审核', '通过', '不通过'][baseline.auditStatus] ?? '未知状态' }}</dd></div>
          <div><dt>审核信息</dt><dd>{{ baseline.auditMessage || '—' }}</dd></div>
          <div><dt>审核人</dt><dd>{{ baseline.auditorId ?? '—' }}</dd></div>
          <div><dt>点赞数</dt><dd>{{ baseline.likeCount }}</dd></div>
          <div><dt>审核时间</dt><dd><UserTime :value="baseline.auditTime" /></dd></div>
          <div><dt>创建时间</dt><dd><UserTime :value="baseline.createdAt" /></dd></div>
          <div><dt>编辑时间</dt><dd><UserTime :value="baseline.editTime" /></dd></div>
          <div><dt>更新时间</dt><dd><UserTime :value="baseline.updateTime" /></dd></div>
        </dl>
      </section>
    </dialog>
  </main>
</template>

<style scoped>
.admin-images { max-width: 1560px; margin: 0 auto; padding: clamp(20px, 3.3vw, 48px); }
h1, h2, p { margin: 0; }
h1 { font-size: 1.65rem; letter-spacing: -.04em; }
h2 { font-size: 1.1rem; overflow-wrap: anywhere; }
p { line-height: 1.6; }
.page-header { display: flex; justify-content: space-between; gap: 16px; align-items: center; margin-bottom: 28px; }
.page-header .hint { margin-top: 8px; }
.filters { margin-bottom: 24px; padding: 20px; background: #fff; border: 1px solid #e5e9e0; border-radius: 12px; }
fieldset { min-width: 0; border: 0; padding: 0; margin: 0; display: grid; gap: 16px; }
.filters fieldset { grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); }
label { display: grid; gap: 7px; min-width: 0; font-size: .85rem; font-weight: 600; }
input, select, textarea, button { min-width: 0; max-width: 100%; border: 1px solid #b9c2b9; border-radius: 8px; padding: 9px 12px; min-height: 42px; background: #fdfdf9; color: inherit; font: inherit; }
input, select, textarea { width: 100%; }
/* 去掉原生贴边箭头，改用自定义 SVG 箭头，定位在离右边框 14px 处。 */
select { appearance: none; padding-right: 34px; background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='8' viewBox='0 0 12 8'%3E%3Cpath d='M1 1.5l5 5 5-5' fill='none' stroke='%23626f5e' stroke-width='1.6' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E"); background-repeat: no-repeat; background-position: right 14px center; }
textarea { resize: vertical; }
button { cursor: pointer; font-size: .85rem; font-weight: 600; }
button:disabled { cursor: default; opacity: .55; }
:focus-visible { outline: 2px solid #2e6154; outline-offset: 2px; }
.primary { color: #fffdf3; background: #365c52; border-color: #365c52; }
.actions { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin-top: 14px; }
.hint { color: #626f5e; font-size: .8rem; overflow-wrap: anywhere; }
.error { color: #a0372e; font-size: .875rem; margin: 12px 0; overflow-wrap: anywhere; }
.success { color: #28614e; font-size: .875rem; margin: 12px 0; }
.empty { padding: 60px 16px; text-align: center; color: #626f5e; }
.image-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 20px; }
.image-card { padding: 12px; background: white; border: 1px solid #e5e9e0; border-radius: 12px; min-width: 0; }
.card-body { padding: 14px 4px 4px; display: grid; gap: 7px; }
.pagination { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 24px; font-size: .85rem; }
.pagination label { display: flex; align-items: center; gap: 8px; }
.pagination select { width: 74px; }
/* 详情与编辑使用屏幕居中的独立卡片，替代原先贴右侧的抽屉。 */
.image-drawer { position: fixed; inset: 0; margin: auto; width: min(calc(100% - 32px), 620px); max-width: 100%; height: auto; max-height: min(88svh, 920px); overflow: auto; overscroll-behavior: contain; border: 0; border-radius: 18px; padding: 0; color: #182925; background: white; box-shadow: 0 30px 80px #1829252e; }
.image-drawer::backdrop { background: #18292559; backdrop-filter: blur(2px); }
.drawer-content { position: relative; display: grid; gap: 18px; padding: 32px; }
.drawer-content h2 { padding-right: 36px; }
.close { position: absolute; top: 18px; right: 14px; border: 0; font-size: 28px; background: transparent; }
.drawer-content form { display: grid; gap: 16px; }
.checkbox { display: flex; align-items: center; }
.checkbox input { width: 18px; min-height: 18px; }
.form-actions { justify-content: flex-end; border-top: 1px solid #e5e9e0; padding-top: 20px; }
.metadata { display: grid; gap: 16px; border-top: 1px solid #e5e9e0; padding-top: 24px; margin: 0; font-size: .85rem; }
.metadata div { display: grid; grid-template-columns: 90px minmax(0, 1fr); gap: 12px; }
dt { color: #626f5e; } dd { margin: 0; overflow-wrap: anywhere; }
@media (max-width: 540px) { .page-header { align-items: start; flex-direction: column; } .drawer-content { padding: 26px 20px; } .filters { padding: 16px; } .filters fieldset { grid-template-columns: minmax(0, 1fr); } .image-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
