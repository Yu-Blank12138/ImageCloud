<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { register } from '../auth/api'
import AuthLayout from '../components/AuthLayout.vue'

const router = useRouter()
const account = ref('')
const password = ref('')
const displayName = ref('')
const pending = ref(false)
const error = ref('')
let request: AbortController | undefined

async function submit() {
  if (pending.value) return
  error.value = ''

  if (!/^[A-Za-z0-9_]{3,16}$/.test(account.value.trim())) {
    error.value = '请输入 3–16 位英文字母、数字或下划线作为账号。'
    return
  }
  if (password.value.length < 8 || password.value.length > 32 || !password.value.trim()) {
    error.value = '请输入 8–32 位密码，不能全为空白。'
    return
  }
  if (displayName.value.trim().length > 12) {
    error.value = '昵称不能超过 12 位。'
    return
  }

  const attempt = new AbortController()
  request = attempt
  pending.value = true
  try {
    // 密码按原值发送，注册成功后以服务端返回的账号为准。
    const result = await register(
      {
        account: account.value,
        password: password.value,
        displayName: displayName.value || undefined,
      },
      attempt.signal,
    )
    if (attempt.signal.aborted) return
    password.value = ''
    await router.replace({ name: 'login', state: { registeredAccount: result.account } })
  } catch (failure) {
    if (!attempt.signal.aborted) {
      error.value = failure instanceof Error ? failure.message : '注册失败，请重试。'
    }
  } finally {
    pending.value = false
    request = undefined
  }
}

// 切换到登录页时取消表单等待，忽略迟到的注册结果。
onBeforeUnmount(() => request?.abort())
</script>

<template>
  <AuthLayout title="注册" description="创建你的 ImageCloud 账号。">
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
          aria-describedby="account-hint"
          :readonly="pending"
          required
        />
        <p id="account-hint" class="field-hint">3–16 位英文字母、数字或下划线，区分大小写。</p>
      </div>

      <div class="field">
        <label for="password">密码</label>
        <input
          id="password"
          v-model="password"
          name="password"
          type="password"
          autocomplete="new-password"
          aria-describedby="password-hint"
          :readonly="pending"
          required
        />
        <p id="password-hint" class="field-hint">8–32 位字符，不能全为空白。</p>
      </div>

      <div class="field">
        <label for="display-name">昵称（选填）</label>
        <input
          id="display-name"
          v-model="displayName"
          name="displayName"
          autocomplete="nickname"
          placeholder="最多 12 位字符"
          :readonly="pending"
        />
      </div>

      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button type="submit" :disabled="pending">{{ pending ? '注册中…' : '注册' }}</button>
    </form>

    <template #footer>
      已有账号？ <RouterLink :to="{ name: 'login' }">登录</RouterLink>
    </template>
  </AuthLayout>
</template>
