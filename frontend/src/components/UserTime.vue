<script setup lang="ts">
import { computed } from 'vue'
import { normalizeBusinessDateTime } from '../utils/time'

const props = defineProps<{ value: string | null | undefined; emptyText?: string }>()
// 业务时间按接口日历值及秒小数展示，原值同时保留在属性中，不附加时区。
const local = computed(() => props.value ? normalizeBusinessDateTime(props.value) : null)
</script>

<template>
  <time v-if="local && value" class="user-time" :datetime="value" :title="value.replace('T', ' ')">
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
