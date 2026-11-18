import { createRouter, createWebHistory } from 'vue-router'
import { ensureSession, getActiveSession } from '../auth/session'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import SessionRetryView from '../views/SessionRetryView.vue'
import WorkspaceView from '../views/WorkspaceView.vue'
import AppLayout from '../layouts/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: { name: 'workspace' } },
    { path: '/login', name: 'login', component: LoginView, meta: { title: '登录' } },
    { path: '/register', name: 'register', component: RegisterView, meta: { title: '注册' } },
    {
      path: '/session-retry',
      name: 'session-retry',
      component: SessionRetryView,
      meta: { title: '验证登录' },
    },
    {
      path: '/',
      component: AppLayout,
      meta: { requiresAuth: true },
      children: [
        { path: 'workspace', name: 'workspace', component: WorkspaceView, meta: { title: '我的图库', section: '我的空间' } },
        { path: 'explore', name: 'explore', component: WorkspaceView, meta: { title: '公共图库', section: '发现' } },
        { path: 'teams', name: 'teams', component: WorkspaceView, meta: { title: '团队空间', section: '团队' } },
        {
          path: 'admin/users',
          name: 'admin-users',
          component: () => import('../views/AdminUsersView.vue'),
          meta: { title: '用户管理', section: '管理' },
        },
      ],
    },
  ],
})

// 受保护页面先确认会话，管理接口仍会独立检查最新角色。
router.beforeEach(async (to) => {
  const adminPage = to.name === 'admin-users'
  if (!to.meta.requiresAuth) return
  const status = await ensureSession()
  if (status === 'anonymous') return { name: 'login', replace: true }
  if (status === 'unavailable') {
    return { name: 'session-retry', state: { resumeRoute: String(to.name ?? 'workspace') }, replace: true }
  }
  if (adminPage && getActiveSession()?.user.platformRole !== 'ADMIN') {
    return { name: 'workspace', query: { notice: 'admin-only' }, replace: true }
  }
})

router.afterEach((to, _from, failure) => {
  if (!failure) document.title = to.meta.title ? `${to.meta.title} · ImageCloud` : 'ImageCloud'
})

export default router
