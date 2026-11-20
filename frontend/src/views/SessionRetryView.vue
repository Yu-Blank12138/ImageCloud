<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { parseUserId } from '../admin/users'
import AuthLayout from '../components/AuthLayout.vue'

const router = useRouter()
const pending = ref(false)
const requestedRoute: unknown = window.history.state?.resumeRoute
const requestedId: unknown = window.history.state?.resumeId
const resumeId = typeof requestedId === 'string' ? parseUserId(requestedId) : null
const destination = typeof requestedRoute === 'string' && ['workspace', 'explore', 'teams', 'admin-users', 'admin-images', 'profile', 'account-settings', 'public-profile'].includes(requestedRoute)
  ? requestedRoute : 'workspace'

async function retry() {
  if (pending.value) return
  pending.value = true
  try {
    // 通过同一个路由守卫重试，服务仍不可用时继续留在此页。
    const name = destination === 'public-profile' && !resumeId ? 'workspace' : destination
    await router.replace({ name, params: name === 'public-profile' ? { id: resumeId! } : {} })
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <AuthLayout title="暂时无法验证登录" description="请重试验证以继续访问。">
    <form class="auth-form" :aria-busy="pending" @submit.prevent="retry">
      <button type="submit" :disabled="pending">{{ pending ? '正在验证…' : '重试验证' }}</button>
    </form>
  </AuthLayout>
</template>
