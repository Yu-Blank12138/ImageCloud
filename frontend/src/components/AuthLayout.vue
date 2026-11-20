<script setup lang="ts">
// 登录与注册共用品牌布局和表单样式，各自处理校验和提交。
defineProps<{ title: string; description: string }>()
</script>

<template>
  <main class="auth-page">
    <div class="cover">
      <p>个人与团队的<br />私有云图库</p>
    </div>

    <section class="auth-panel" aria-labelledby="auth-title">
      <div class="brand">
        <img class="brand-mark" src="/icon.png" alt="" aria-hidden="true">
        ImageCloud
      </div>

      <div class="auth-content">
        <h1 id="auth-title">{{ title }}</h1>
        <p class="intro">{{ description }}</p>

        <slot />
        <p class="auth-footer"><slot name="footer" /></p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: 18px;
  min-height: 100vh;
  min-height: 100svh;
  padding: 18px;
}

.cover {
  display: flex;
  align-items: end;
  padding: clamp(28px, 4.8vw, 76px);
  border-radius: 20px;
  color: #fffdf3;
  background:
    linear-gradient(0deg, rgb(10 27 27 / 82%), transparent 53%),
    #213b3c url('../assets/login-cover.jpg') center 57% / cover no-repeat;
}

.cover p {
  margin: 0;
  font-size: clamp(1.9rem, 3.6vw, 3.75rem);
  font-weight: 600;
  line-height: 1.25;
  letter-spacing: 0.025em;
}

.auth-panel {
  display: grid;
  grid-template-rows: auto 1fr;
  justify-self: center;
  width: min(100% - 64px, 390px);
  padding-block: clamp(28px, 6vh, 64px) 28px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  font-size: 1.25rem;
  font-weight: 700;
  letter-spacing: -0.045em;
}

.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  object-fit: cover;
}

.auth-content {
  align-self: center;
  padding-block: 50px 72px;
}

h1 {
  margin: 0;
  font-size: clamp(2rem, 3.2vw, 3rem);
  font-weight: 700;
  line-height: 1.2;
}

.intro {
  margin: 12px 0 32px;
  color: #525f59;
  line-height: 1.6;
}

:deep(.auth-form),
:deep(.field) {
  display: grid;
}

:deep(.auth-form) {
  gap: 20px;
}

:deep(.field) {
  gap: 8px;
}

:deep(label) {
  font-size: 0.875rem;
  font-weight: 600;
}

:deep(input),
:deep(button) {
  min-width: 0;
  min-height: 50px;
  border-radius: 9px;
  font: inherit;
}

:deep(input) {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid #b9c2b9;
  color: inherit;
  background: #fdfdf9;
}

:deep(input::placeholder) {
  color: #67746d;
}

:deep(input:focus-visible) {
  border-color: #2e6154;
  outline: 2px solid #2e6154;
  outline-offset: 2px;
}

:deep(button) {
  margin-top: 4px;
  border: 0;
  color: #fffdf3;
  background: #365c52;
  font-weight: 600;
}

:deep(button:disabled) {
  cursor: wait;
  opacity: 0.7;
}

:deep(.field-hint),
:deep(.form-error),
:deep(.form-status) {
  margin: 0;
  font-size: 0.875rem;
  line-height: 1.5;
}

:deep(.field-hint) {
  color: #525f59;
}

:deep(.form-error),
:deep(.form-status) {
  padding: 10px 12px;
  border-radius: 8px;
}

:deep(.form-error) {
  color: #862a2b;
  background: #fbeceb;
}

:deep(.form-status) {
  margin-bottom: 16px;
  color: #224c42;
  background: #e6eee6;
}

.auth-footer {
  margin: 20px 0 0;
  color: #525f59;
  font-size: 0.875rem;
  text-align: center;
}

.auth-footer :deep(a) {
  color: #224c42;
  font-weight: 600;
  text-underline-offset: 3px;
}

@media (max-width: 720px) {
  .auth-page {
    grid-template-columns: minmax(0, 1fr);
    padding: 0;
  }

  .cover {
    display: none;
  }

  .auth-panel {
    width: min(100% - 48px, 390px);
  }

  .auth-content {
    padding-block: 40px 66px;
  }
}
</style>
