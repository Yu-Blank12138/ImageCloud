<script setup lang="ts">
import { computed } from 'vue'
import { browserTimeZone, toLocalDateTime } from '../utils/time'

const props = defineProps<{ value: string | null | undefined; emptyText?: string }>()
// 时间按浏览器时区展示到秒，接口原值保留用于 datetime 属性。
const local = computed(() => props.value ? toLocalDateTime(props.value)?.slice(0, 19) : null)
</script>

<template>
  <time v-if="local && value" class="user-time" :datetime="value" :title="local.replace('T', ' ') + ' · ' + browserTimeZone">
    <span>{{ local.slice(0, 10) }}</span>
    <span class="clock-time">{{ local.slice(11) }}</span>
  </time>
  <span v-else class="empty-time">{{ value === null ? emptyText || '—' : '—' }}</span>
</template>

<style scoped>
.user-time {
  display: grid;
  gap: 4px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  font-size: 0.8rem;
  line-height: 1.4;
}

.clock-time,
.empty-time {
  color: #7b8774;
  font-size: 0.75rem;
}
</style>
