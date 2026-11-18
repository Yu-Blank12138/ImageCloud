<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { getActiveSession, logoutSession } from '../auth/session'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const mobileOpen = ref(false)
const pending = ref(false)
const error = ref('')
const accountMenu = ref<HTMLDetailsElement | null>(null)
// 所有登录后页面共用布局，导航权限取自守卫已验证的会话。
const user = computed(() => {
  void route.fullPath
  return getActiveSession()?.user
})
const displayName = computed(() => user.value?.displayName || user.value?.account || '我的账号')
const groups = computed(() => [
  { title: '发现', links: [{ name: 'explore', label: '公共图库', icon: 'picture' }] },
  { title: '我的空间', links: [{ name: 'workspace', label: '我的图库', icon: 'collection' }] },
  { title: '团队空间', links: [{ name: 'teams', label: '团队空间', icon: 'team' }] },
  ...(user.value?.platformRole === 'ADMIN'
    ? [{ title: '管理', links: [{ name: 'admin-users', label: '用户管理', icon: 'users' }] }]
    : []),
])

watch(() => route.fullPath, () => {
  mobileOpen.value = false
  if (accountMenu.value) accountMenu.value.open = false
})

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
</script>

<template>
  <div class="app-layout" :class="{ collapsed, 'mobile-open': mobileOpen }" @keydown.esc="mobileOpen = false">
    <a class="skip-link" href="#workspace-content">跳到页面内容</a>
    <header class="topbar">
      <div class="brand-area">
        <button class="mobile-toggle icon-button" type="button" :aria-expanded="mobileOpen" aria-controls="app-sidebar" aria-label="切换导航" @click="mobileOpen = !mobileOpen">☰</button>
        <RouterLink class="brand" :to="{ name: 'workspace' }" aria-label="ImageCloud 我的图库">
          <span class="brand-mark" aria-hidden="true">▧</span><span>ImageCloud</span>
        </RouterLink>
      </div>
      <div class="topbar-context">
        <span class="breadcrumb">{{ route.meta.section }} <span aria-hidden="true">/</span> <strong>{{ route.meta.title }}</strong></span>
        <div v-if="route.name !== 'admin-users'" class="search-placeholder">
          <span aria-hidden="true">⌕</span>
          <input :placeholder="'搜索' + String(route.meta.title) + '（即将上线）'" aria-label="图库搜索即将上线" disabled />
        </div>
      </div>
      <details ref="accountMenu" class="account-menu">
        <summary aria-label="账号菜单">
          <span class="account-avatar" aria-hidden="true">{{ displayName.slice(0, 1).toUpperCase() }}</span>
          <span class="account-name">{{ displayName }}</span><span class="chevron" aria-hidden="true">⌄</span>
        </summary>
        <div class="account-popover">
          <p class="popover-name">{{ displayName }}</p>
          <p class="popover-account">{{ user?.account }}</p>
          <button type="button" :disabled="pending" @click="logout">{{ pending ? '正在退出…' : '退出登录' }}</button>
          <p v-if="error" class="logout-error" role="alert">{{ error }}</p>
        </div>
      </details>
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
  display: grid;
  place-items: center;
  width: 31px;
  height: 34px;
  border-radius: 9px;
  background: #245443;
  color: #fff;
  font-size: 26px;
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

.account-menu {
  position: relative;
  flex-shrink: 0;
  margin-inline: 12px 28px;
}

.account-menu summary {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  list-style: none;
}

.account-menu summary::-webkit-details-marker {
  display: none;
}

.account-avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid #dce5d8;
  border-radius: 50%;
  background: #eaf0e4;
  color: #315642;
  font-size: 0.85rem;
  font-weight: 600;
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
  top: 50px;
  width: 242px;
  padding: 20px;
  border: 1px solid #e0e5dc;
  border-radius: 12px;
  background: white;
  box-shadow: 0 12px 40px #183d3112;
}

.account-popover p {
  margin: 0;
  overflow-wrap: anywhere;
}

.popover-name {
  font-weight: 600;
}

.popover-account {
  margin-top: 5px !important;
  color: #778375;
  font-size: 0.8rem;
}

.account-popover button {
  width: 100%;
  margin-top: 20px;
  padding: 10px;
  border: 1px solid #dce3d7;
  border-radius: 8px;
  background: #f6f8f2;
  color: #234b38;
  font: inherit;
  cursor: pointer;
}

.account-popover .logout-error {
  margin-top: 12px;
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
    height: 30px;
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

  .account-menu {
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
}
</style>
