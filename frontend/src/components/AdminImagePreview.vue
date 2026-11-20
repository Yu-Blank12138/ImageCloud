<script setup lang="ts">
import { ref, watch } from 'vue'
const props = defineProps<{ src: string | null | undefined; alt: string }>()
const emit = defineEmits<{ retry: [] }>()
const failed = ref(false)
// 预览故障只影响图片展示，不改变上传草稿或保存状态。
watch(() => props.src, () => { failed.value = false })
function retry() { failed.value = false; emit('retry') }
</script>

<template>
  <div class="image-preview">
    <img v-if="src && !failed" :key="src" :src="src" :alt="alt" loading="lazy" referrerpolicy="no-referrer" @error="failed = true" />
    <div v-else class="placeholder">
      <span>{{ failed ? '预览加载失败' : '暂无预览' }}</span>
      <button v-if="failed" type="button" @click="retry">重新加载</button>
    </div>
  </div>
</template>

<style scoped>
.image-preview { display: grid; place-items: center; aspect-ratio: 4 / 3; overflow: hidden; background: #edf0e9; border-radius: 10px; }
img { width: 100%; height: 100%; object-fit: contain; }
.placeholder { display: grid; gap: 8px; justify-items: center; color: #626f5e; font-size: .8rem; }
button { padding: 6px 10px; border: 1px solid #b9c2b9; border-radius: 6px; background: white; color: #224c42; cursor: pointer; }
</style>
