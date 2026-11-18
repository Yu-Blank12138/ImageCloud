<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <!-- 图库与团队功能尚未接入，共用正式导航中的占位内容。 -->
  <section class="placeholder-page" :aria-label="String(route.meta.title)">
    <h1>{{ route.meta.title }}</h1>
    <p v-if="route.query.notice === 'admin-only'" class="permission-notice" role="alert">
      当前账号没有用户管理权限。
    </p>
    <div class="placeholder-content">
      <span class="placeholder-picture" aria-hidden="true">▧</span>
      <h2>{{ route.meta.title }}即将上线</h2>
      <p>页面建设中</p>
    </div>
  </section>
</template>

<style scoped>
.placeholder-page {
  padding: clamp(24px, 3vw, 44px);
}

h1 {
  margin: 0;
  font-size: 1.65rem;
  letter-spacing: -0.04em;
}

.placeholder-content {
  display: grid;
  justify-items: center;
  align-content: center;
  min-height: 60vh;
}

.placeholder-picture {
  font-size: 42px;
  color: #647b70;
}

h2 {
  margin: 18px 0 8px;
  font-size: 1.125rem;
  font-weight: 500;
}

p {
  color: #728077;
  font-size: 0.875rem;
}

.permission-notice {
  padding: 12px;
  color: #862a2b;
  background: #fbeceb;
  border-radius: 8px;
}
</style>
