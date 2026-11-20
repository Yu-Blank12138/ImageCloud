<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { getActiveSession, logoutSession } from '../auth/session'
import AccountAvatar from '../components/AccountAvatar.vue'
import { searchPublicUsers, type PublicUserSummary } from '../profile/api'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const mobileOpen = ref(false)
const pending = ref(false)
const error = ref('')
const accountMenu = ref<HTMLDetailsElement | null>(null)
const profileQuery = ref('')
const profileResults = ref<PublicUserSummary[]>([])
const profileResultsOpen = ref(false)
const profileSearching = ref(false)
const profileLoadingMore = ref(false)
const profileSearchError = ref('')
const profilePage = ref(0)
const profileTotal = ref(0)
let profileRequest: AbortController | undefined
// 搜索结果按三列卡片展示，一次加载一屏可见的数量，滚动到底再取下一页。
const PROFILE_PAGE_SIZE = 9
const profileHasMore = computed(() => profileResults.value.length < profileTotal.value)
// 个人主页相关页面在顶栏提供昵称搜索入口，与公共图库搜索同一位置。
const showProfileLookup = computed(() => ['profile', 'public-profile'].includes(String(route.name)))
// 所有登录后页面共用布局，导航权限取自守卫已验证的会话。
const user = computed(() => getActiveSession()?.user)
const displayName = computed(() => user.value?.displayName || user.value?.account || '我的账号')
const groups = computed(() => [
  { title: '发现', links: [{ name: 'explore', label: '公共图库', icon: 'picture' }] },
  { title: '我的空间', links: [{ name: 'workspace', label: '我的图库', icon: 'collection' }] },
  { title: '团队空间', links: [{ name: 'teams', label: '团队空间', icon: 'team' }] },
  ...(user.value?.platformRole === 'ADMIN'
    ? [{ title: '管理', links: [{ name: 'admin-users', label: '用户管理', icon: 'users' }, { name: 'admin-images', label: '图片管理', icon: 'picture' }] }]
    : []),
])

watch(() => route.fullPath, () => {
  mobileOpen.value = false
  if (accountMenu.value) accountMenu.value.open = false
  closeProfileResults()
})

// 初始化头像查询可能在布局挂载前已确认失效，首次挂载也要处理空会话。
watch(() => user.value, (value) => {
  if (!value && route.meta.requiresAuth) void router.replace({ name: 'login' })
}, { immediate: true })

async function logout() {
  if (pending.value) return
  pending.value = true
  error.value = ''
  try {
    if (await logoutSession()) await router.replace({ name: 'login' })
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : '退出失败，请重试。'
  } finally {
    pending.value = false
  }
}

// 关闭昵称搜索结果面板，并中止尚未返回的搜索请求。
function closeProfileResults() {
  profileRequest?.abort()
  profileRequest = undefined
  profileSearching.value = false
  profileLoadingMore.value = false
  profileResultsOpen.value = false
}

// 加载指定页的昵称搜索结果；追加模式用于滚动到底后翻页。
async function loadProfilePage(page: number, append: boolean) {
  const keyword = profileQuery.value.trim()
  const session = getActiveSession()
  if (!keyword || !session) return
  profileRequest?.abort()
  const controller = new AbortController()
  profileRequest = controller
  profileSearchError.value = ''
  if (append) {
    profileLoadingMore.value = true
  } else {
    profileResultsOpen.value = true
    profileSearching.value = true
  }
  try {
    const data = await searchPublicUsers(
      { displayName: keyword, page, pageSize: PROFILE_PAGE_SIZE },
      session.accessToken,
      controller.signal,
    )
    if (controller.signal.aborted) return
    profilePage.value = data.page
    profileTotal.value = data.total
    profileResults.value = append
      ? [...profileResults.value, ...data.records.filter((item) => !profileResults.value.some((loaded) => loaded.id === item.id))]
      : data.records
    // 只有唯一匹配时直接进入该用户主页，避免多余的一次点击。
    if (!append && data.total === 1 && data.records.length === 1) {
      closeProfileResults()
      await router.push({ name: 'public-profile', params: { id: data.records[0].id } })
    }
  } catch (failure) {
    if (controller.signal.aborted) return
    profileSearchError.value = failure instanceof Error ? failure.message : '搜索失败，请重试。'
  } finally {
    if (profileRequest === controller) {
      profileRequest = undefined
      profileSearching.value = false
      profileLoadingMore.value = false
    }
  }
}

// 提交新关键词时从第一页重新搜索。
function searchProfiles() {
  profileResults.value = []
  profileTotal.value = 0
  profilePage.value = 0
  void loadProfilePage(1, false)
}

// 面板滚动接近底部时加载下一页，正在加载或已加载完时跳过。
function onProfileResultsScroll(event: Event) {
  const element = event.target as HTMLElement
  if (element.scrollTop + element.clientHeight < element.scrollHeight - 48) return
  if (profileSearching.value || profileLoadingMore.value || !profileHasMore.value) return
  void loadProfilePage(profilePage.value + 1, true)
}
</script>

<template>
  <div class="app-layout" :class="{ collapsed, 'mobile-open': mobileOpen }" @keydown.esc="mobileOpen = false">
    <a class="skip-link" href="#workspace-content">跳到页面内容</a>
    <header class="topbar">
      <div class="brand-area">
        <button class="mobile-toggle icon-button" type="button" :aria-expanded="mobileOpen" aria-controls="app-sidebar" aria-label="切换导航" @click="mobileOpen = !mobileOpen">☰</button>
        <RouterLink class="brand" :to="{ name: 'workspace' }" aria-label="ImageCloud 我的图库">
          <img class="brand-mark" src="/icon.png" alt="" aria-hidden="true"><span>ImageCloud</span>
        </RouterLink>
      </div>
      <div class="topbar-context">
        <span class="breadcrumb">{{ route.meta.section }} <span aria-hidden="true">/</span> <strong>{{ route.meta.title }}</strong></span>
        <div v-if="['workspace', 'explore', 'teams'].includes(String(route.name))" class="search-placeholder">
          <span aria-hidden="true">⌕</span>
          <input :placeholder="'搜索' + String(route.meta.title) + '（即将上线）'" aria-label="图库搜索即将上线" disabled />
        </div>
        <div v-else-if="showProfileLookup" class="profile-search" @keydown.esc="closeProfileResults">
          <form class="search-placeholder" @submit.prevent="searchProfiles">
            <span aria-hidden="true">⌕</span>
            <input
              v-model="profileQuery" placeholder="按昵称搜索用户" aria-label="按昵称搜索用户"
              autocomplete="off" maxlength="12" @input="closeProfileResults"
            />
          </form>
          <div v-if="profileResultsOpen" class="profile-results" aria-label="用户搜索结果" @scroll="onProfileResultsScroll">
            <p v-if="profileSearching" class="results-status" role="status">正在搜索用户…</p>
            <p v-else-if="profileSearchError && !profileResults.length" class="results-status is-error" role="alert">{{ profileSearchError }}</p>
            <p v-else-if="!profileResults.length" class="results-status">没有找到昵称匹配的用户</p>
            <ul v-else>
              <li v-for="item in profileResults" :key="item.id">
                <div class="result-head">
                  <span class="result-avatar" aria-hidden="true">
                    {{ (item.displayName || item.account).slice(0, 1).toUpperCase() }}
                    <img v-if="item.avatarUrl" :src="item.avatarUrl" alt="" @error="($event.target as HTMLImageElement).hidden = true" />
                  </span>
                  <div class="result-identity">
                    <p class="result-name">{{ item.displayName || item.account }}</p>
                    <p class="result-account">@{{ item.account }}</p>
                  </div>
                </div>
                <div v-if="item.previewImageUrls.length" class="result-gallery">
                  <img v-for="url in item.previewImageUrls" :key="url" :src="url" alt="" loading="lazy" />
                  <span v-for="slot in 3 - item.previewImageUrls.length" :key="'empty-' + slot" class="result-gallery-empty" aria-hidden="true"></span>
                </div>
                <RouterLink class="result-link" :to="{ name: 'public-profile', params: { id: item.id } }">查看个人资料</RouterLink>
              </li>
            </ul>
            <p v-if="profileLoadingMore" class="results-status" role="status">正在加载更多用户…</p>
          </div>
        </div>
      </div>
      <div class="account-controls">
        <AccountAvatar :name="displayName" />
        <details ref="accountMenu" class="account-menu">
          <summary aria-label="账号菜单">
            <span class="account-name">{{ displayName }}</span><span class="chevron" aria-hidden="true">⌄</span>
          </summary>
          <div class="account-popover">
            <div class="popover-head">
              <AccountAvatar :name="displayName" size="medium" />
              <p class="popover-name">{{ displayName }}</p>
              <RouterLink class="popover-profile" :to="{ name: 'profile' }">查看个人资料</RouterLink>
            </div>
            <RouterLink class="account-link" :to="{ name: 'account-settings' }">账户设置</RouterLink>
            <button type="button" :disabled="pending" @click="logout">{{ pending ? '正在退出…' : `退出登录 @${user?.account ?? ''}` }}</button>
            <p v-if="error" class="logout-error" role="alert">{{ error }}</p>
          </div>
        </details>
      </div>
    </header>

    <button v-if="mobileOpen" class="sidebar-backdrop" type="button" aria-label="关闭导航" @click="mobileOpen = false"></button>
    <aside id="app-sidebar" class="sidebar">
      <nav aria-label="主导航">
        <div v-for="group in groups" :key="group.title" class="nav-group">
          <p class="nav-heading">{{ group.title }}</p>
          <RouterLink
            v-for="link in group.links" :key="link.name"
            :to="{ name: link.name }" class="nav-link" :title="link.label" :aria-label="link.label"
          >
            <span class="nav-icon" :class="link.icon" aria-hidden="true"></span>
            <span class="nav-label">{{ link.label }}</span>
          </RouterLink>
        </div>
      </nav>
      <button class="collapse-button" type="button" :aria-expanded="!collapsed" aria-controls="app-sidebar" :aria-label="collapsed ? '展开导航' : '收起导航'" @click="collapsed = !collapsed">
        <span aria-hidden="true">{{ collapsed ? '»' : '«' }}</span><span class="nav-label">收起导航</span>
      </button>
    </aside>
    <main id="workspace-content" class="workspace-content" tabindex="-1" :inert="mobileOpen"><RouterView /></main>
  </div>
</template>

<style scoped>
.app-layout {
  --sidebar-width: 220px;
  --topbar-height: 76px;
  min-height: 100svh;
  background: #f9faf7;
}

.app-layout.collapsed {
  --sidebar-width: 80px;
}

.skip-link {
  position: fixed;
  z-index: 50;
  top: -60px;
  left: 16px;
  padding: 12px;
  background: white;
  color: #224c42;
}

.skip-link:focus {
  top: 12px;
}

.topbar {
  position: fixed;
  inset: 0 0 auto;
  z-index: 20;
  display: flex;
  align-items: center;
  height: var(--topbar-height);
  border-bottom: 1px solid #e4e8df;
  background: #fff;
}

.brand-area {
  flex: 0 0 220px;
  display: flex;
  align-items: center;
  padding-left: 25px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: #183d31;
  font-size: 1.14rem;
  font-weight: 750;
  letter-spacing: -0.05em;
}

.brand-mark {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  object-fit: cover;
}

.topbar-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  flex: 1;
  min-width: 0;
  padding: 0 32px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 14px;
  white-space: nowrap;
  font-size: 0.8rem;
  color: #7c877f;
}

.breadcrumb strong {
  color: #46564b;
  font-weight: 500;
}

.breadcrumb > span {
  color: #c9cfc6;
}

.search-placeholder {
  display: flex;
  align-items: center;
  gap: 10px;
  width: min(45vw, 440px);
  padding: 9px 16px;
  border-radius: 9px;
  background: #f4f6f1;
  color: #738276;
}

.search-placeholder > span {
  font-size: 24px;
  line-height: 1;
}

.search-placeholder input {
  width: 100%;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  font: inherit;
  font-size: 0.8rem;
}

.search-placeholder input::placeholder {
  color: #89958a;
  opacity: 1;
}

.profile-search {
  position: relative;
}

/* 昵称搜索结果：展开为顶栏下方、导航右侧的整块内容区，滚动到底加载下一页。 */
.profile-results {
  position: fixed;
  z-index: 15;
  top: var(--topbar-height);
  right: 0;
  bottom: 0;
  left: var(--sidebar-width);
  overflow: auto;
  padding: 28px 32px 40px;
  border-top: 1px solid #e4e8df;
  background: #f9faf7;
  overscroll-behavior: contain;
}

.profile-results ul {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 320px), 1fr));
  gap: 16px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.profile-results li {
  display: grid;
  gap: 12px;
  align-content: start;
  padding: 16px;
  border: 1px solid #e7eae4;
  border-radius: 12px;
  background: #fff;
}

.result-head {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.result-avatar {
  position: relative;
  display: grid;
  place-items: center;
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  overflow: hidden;
  border: 1px solid #dce5d8;
  border-radius: 50%;
  background: #eaf0e4;
  color: #315642;
  font-size: 1rem;
  font-weight: 600;
}

.result-avatar img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.result-identity {
  min-width: 0;
}

.result-name,
.result-account {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-name {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
}

.result-account {
  margin: 2px 0 0;
  color: #7a857b;
  font-size: 0.78rem;
}

/* 有审核通过图片时展示三格预览，不足三张用浅色占位补齐。 */
.result-gallery {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.result-gallery img,
.result-gallery-empty {
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: 6px;
  background: #f1f3ef;
}

.result-gallery img {
  object-fit: cover;
}

.result-link {
  display: block;
  padding: 9px 10px;
  border: 1px solid #dce3d7;
  border-radius: 9px;
  color: #245443;
  font-size: 0.82rem;
  text-align: center;
  text-decoration: none;
}

.result-link:hover {
  background: #f3f6ef;
}

.results-status {
  margin: 0;
  padding: 12px 6px;
  color: #6d7972;
  font-size: 0.85rem;
  text-align: center;
}

.results-status.is-error {
  color: #862a2b;
}

.account-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-inline: 12px 28px;
}

.account-menu {
  position: relative;
}

.account-menu summary {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  cursor: pointer;
  list-style: none;
}

.account-menu summary::-webkit-details-marker {
  display: none;
}



.account-name {
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.8rem;
  font-weight: 600;
}

.chevron {
  color: #7f8a80;
}

.account-popover {
  position: absolute;
  right: 0;
  top: 54px;
  width: 268px;
  overflow: hidden;
  border: 1px solid #e3e6e2;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 18px 48px rgb(24 61 49 / 12%), 0 2px 8px rgb(24 61 49 / 6%);
}

.account-popover p {
  margin: 0;
  overflow-wrap: anywhere;
}

.popover-head {
  display: grid;
  justify-items: center;
  padding: 22px 20px 18px;
  text-align: center;
}

.popover-head .avatar-control {
  margin-bottom: 10px;
}

.popover-name {
  font-size: 1.05rem;
  font-weight: 700;
  line-height: 1.4;
}

.popover-profile {
  margin-top: 4px;
  color: #6d7972;
  font-size: 0.9rem;
  text-decoration: none;
}

.popover-profile:hover {
  color: #234b38;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.account-link,
.account-popover button {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 16px 22px;
  border: 0;
  border-top: 1px solid #edf0ec;
  background: transparent;
  color: #1f2b26;
  font: inherit;
  font-size: 0.95rem;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
}

.account-link:hover,
.account-popover button:hover:not(:disabled) {
  background: #f5f7f3;
}

.account-popover button:disabled {
  cursor: wait;
  opacity: 0.6;
}

.account-popover .logout-error {
  padding: 12px 22px;
  border-top: 1px solid #edf0ec;
  color: #862a2b;
  font-size: 0.8rem;
}

.sidebar {
  position: fixed;
  z-index: 10;
  top: var(--topbar-height);
  bottom: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  width: var(--sidebar-width);
  padding: 32px 16px 20px;
  border-right: 1px solid #e4e8df;
  background: #f4f6ef;
}

.nav-group + .nav-group {
  margin-top: 30px;
}

.nav-heading {
  margin: 0 14px 12px;
  font-size: 0.7rem;
  color: #8a9587;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  padding: 11px 14px;
  border-radius: 8px;
  text-decoration: none;
  color: #5d6e60;
  font-size: 0.85rem;
}

.nav-link:hover {
  background: #e9eee2;
  color: #245443;
}

.nav-link.router-link-exact-active {
  color: #224e35;
  background: #e0e9d7;
  font-weight: 600;
}

.nav-icon {
  position: relative;
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
}

.picture {
  border: 1.5px solid currentColor;
  border-radius: 3px;
}

.picture::before {
  content: '';
  position: absolute;
  inset: 6px 2px 2px;
  background: currentColor;
  clip-path: polygon(0 100%, 38% 15%, 62% 65%, 80% 45%, 100% 100%);
}

.picture::after {
  content: '';
  position: absolute;
  top: 3px;
  right: 3px;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: currentColor;
}

.collection {
  border: 1.5px solid currentColor;
  border-radius: 3px;
  height: 14px;
  margin-top: 3px;
}

.collection::before {
  content: '';
  position: absolute;
  left: 2px;
  right: 2px;
  top: -5px;
  border-top: 1.5px solid currentColor;
}

.team {
  border: 1.5px solid currentColor;
  border-radius: 3px;
  height: 14px;
  margin-top: 3px;
}

.team::before {
  content: '';
  position: absolute;
  width: 8px;
  height: 4px;
  left: -1.5px;
  top: -5px;
  border: 1.5px solid currentColor;
  border-bottom: 0;
  border-radius: 3px 3px 0 0;
}

.users::before {
  content: '';
  position: absolute;
  width: 7px;
  height: 7px;
  top: 0;
  left: 3px;
  border: 1.5px solid currentColor;
  border-radius: 50%;
  box-shadow: 5px 1px 0 -1px #f4f6ef, 6px 1px 0 0 currentColor;
}

.users::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 18px;
  height: 8px;
  border: 1.5px solid currentColor;
  border-radius: 8px 8px 3px 3px;
}

.collapse-button {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: auto;
  padding: 12px 14px;
  border: 0;
  background: transparent;
  color: #788774;
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;
}

.collapse-button > span:first-child {
  font-size: 22px;
  line-height: 18px;
}

.collapsed .sidebar .nav-heading {
  font-size: 0;
  height: 0;
  margin: 0 0 12px;
}

.collapsed .sidebar .nav-label {
  display: none;
}

.collapsed .sidebar .nav-link {
  padding-inline: 15px;
}

.workspace-content {
  min-height: 100svh;
  padding-top: var(--topbar-height);
  margin-left: var(--sidebar-width);
  outline: none;
}

:is(button, a, summary):focus-visible {
  outline: 2px solid #3d7255;
  outline-offset: 3px;
}

.mobile-toggle, .sidebar-backdrop {
  display: none;
}

@media (max-width: 1000px) {

  .breadcrumb {
    display: none;
  }

  .topbar-context {
    padding-inline: 20px;
  }
}

@media (max-width: 680px) {

  .app-layout {
    --topbar-height: 64px;
  }

  .brand-area {
    flex: 1;
    gap: 14px;
    padding-left: 16px;
  }

  .brand {
    font-size: 1.05rem;
  }

  .brand-mark {
    width: 28px;
    height: 28px;
  }

  .mobile-toggle {
    display: block;
    padding: 6px;
    border: 0;
    background: none;
    color: #34573c;
    font-size: 20px;
    cursor: pointer;
  }

  .topbar-context, .account-name {
    display: none;
  }

  .account-controls {
    margin-inline: 10px 20px;
  }

  .sidebar {
    display: none;
    width: 220px;
    z-index: 30;
    box-shadow: 15px 0 40px #183d3115;
  }

  .mobile-open .sidebar {
    display: flex;
  }

  .app-layout .sidebar .nav-heading {
    font-size: 0.7rem;
    height: auto;
    margin: 0 14px 12px;
  }

  .app-layout .sidebar .nav-label {
    display: inline;
  }

  .collapse-button {
    display: none;
  }

  .sidebar-backdrop {
    display: block;
    position: fixed;
    inset: var(--topbar-height) 0 0;
    z-index: 25;
    border: 0;
    background: #183d3144;
  }

  .workspace-content {
    margin-left: 0;
  }

  .profile-results {
    left: 0;
    padding: 16px 16px 28px;
  }
}
</style>
