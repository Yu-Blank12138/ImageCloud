<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { login } from '../auth/api'
import { startSession } from '../auth/session'
import AuthLayout from '../components/AuthLayout.vue'

const router = useRouter()
// 注册账号通过浏览器历史状态传递，避免将它写入 URL。
const historyAccount: unknown = window.history.state?.registeredAccount
const registeredAccount = typeof historyAccount === 'string' ? historyAccount : ''
const account = ref(registeredAccount)
const password = ref('')
// 保留超长粘贴的完整输入并立即提示，提交时拒绝发送。
const passwordTooLong = computed(() => password.value.length > 32)
const pending = ref(false)
const error = ref('')
let request: AbortController | undefined

async function submit() {
  if (pending.value) return
  error.value = ''

  if (!/^[A-Za-z0-9_]{3,32}$/.test(account.value.trim())) {
    error.value = '请输入 3–32 位英文字母、数字或下划线作为账号。'
    return
  }
  if (passwordTooLong.value) return
  // 与后端的长度校验及 Java trim 判空一致，提交时保留密码原文。
  if (password.value.length < 8 || !/[^\x00-\x20]/.test(password.value)) {
    error.value = '请输入 8–32 个字符的密码，不能全为空白。'
    return
  }

  const attempt = new AbortController()
  request = attempt
  pending.value = true
  try {
    const result = await login({ account: account.value, password: password.value }, attempt.signal)
    if (attempt.signal.aborted) return
    startSession(result)
    password.value = ''
    await router.replace({ name: 'workspace' })
  } catch (failure) {
    if (!attempt.signal.aborted) {
      error.value = failure instanceof Error ? failure.message : '登录失败，请重试。'
    }
  } finally {
    pending.value = false
    request = undefined
  }
}

// 离开表单后丢弃本次请求结果，避免迟到的响应触发跳转。
onBeforeUnmount(() => request?.abort())
</script>

<template>
  <AuthLayout title="登录" description="登录后即可访问你的图库。">
    <p v-if="registeredAccount" class="form-status" role="status">注册成功，请登录。</p>

    <form class="auth-form" novalidate :aria-busy="pending" @submit.prevent="submit">
      <div class="field">
        <label for="account">账号</label>
        <input
          id="account"
          v-model="account"
          name="account"
          autocomplete="username"
          autocapitalize="none"
          spellcheck="false"
          placeholder="请输入账号"
          :readonly="pending"
          required
        />
      </div>

      <div class="field">
        <label for="password">密码</label>
        <input
          id="password"
          v-model="password"
          name="password"
          type="password"
          autocomplete="current-password"
          placeholder="请输入密码"
          minlength="8"
          :aria-describedby="passwordTooLong ? 'password-hint password-length-error' : 'password-hint'"
          :aria-invalid="passwordTooLong || undefined"
          :readonly="pending"
          required
          @input="error = ''"
        />
        <p id="password-hint" class="field-hint">8–32 个字符，不能全为空白。</p>
        <p v-if="passwordTooLong" id="password-length-error" class="form-error" role="alert">
          密码不能超过 32 个字符。
        </p>
      </div>

      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button type="submit" :disabled="pending">{{ pending ? '登录中…' : '登录' }}</button>
    </form>

    <template #footer>
      还没有账号？ <RouterLink :to="{ name: 'register' }">注册</RouterLink>
    </template>
  </AuthLayout>
</template>
