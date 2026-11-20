<script setup lang="ts">
import { computed, ref } from 'vue'
import { avatarDraft, avatarPreviewUrl } from '../profile/avatar'
import AvatarOriginalViewer from './AvatarOriginalViewer.vue'

const props = defineProps<{ name: string; size?: 'medium' | 'large'; targetId?: string; avatarUrl?: string | null }>()
const viewing = ref(false)
const draft = computed(() => props.targetId ? undefined : avatarDraft.value)
const url = computed(() => props.targetId ? props.avatarUrl : avatarPreviewUrl.value)
</script>

<template>
  <span class="avatar-control">
    <button class="avatar-trigger" type="button" :aria-label="draft ? '查看未保存的头像原图' : '查看头像原图'" aria-haspopup="dialog" @click="viewing = true">
      <span class="avatar" :class="size" aria-hidden="true">
        {{ name.slice(0, 1).toUpperCase() }}
        <img v-if="url" :key="url" :src="url" alt="" @error="($event.target as HTMLImageElement).hidden = true" />
      </span>
    </button>
    <AvatarOriginalViewer v-if="viewing" :draft="draft" :target-id="targetId" @close="viewing = false" />
  </span>
</template>

<style scoped>
.avatar-control {
  display: inline-flex;
  flex-shrink: 0;
}

.avatar-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  min-height: 44px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
}

.avatar-trigger:focus-visible {
  outline: 3px solid #628d76;
  outline-offset: 3px;
}

.avatar {
  position: relative;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border: 1px solid #dce5d8;
  border-radius: 50%;
  background: #eaf0e4;
  color: #315642;
  font-size: 0.85rem;
  overflow: hidden;
}

.medium {
  width: 64px;
  height: 64px;
  font-size: 24px;
}

.large {
  width: 88px;
  height: 88px;
  font-size: 30px;
}

img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

@media (max-width: 600px) {

  .large {
    width: 64px;
    height: 64px;
  }
}
</style>
