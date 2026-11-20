<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

// 头像裁剪弹窗：固定正方形展示区，拖动平移、滑杆缩放，确认后输出裁剪区域 PNG。
const props = defineProps<{ file: File }>()
const emit = defineEmits<{ confirm: [blob: Blob]; cancel: [] }>()

const FRAME = 300
const MAX_OUTPUT = 1024
// 滑杆对称取值，中点 0 对应 1×（铺满裁剪框）；往左最小 0.25×，往右最大 4×。
const MIN_SLIDER = -2
const MAX_SLIDER = 2

const dialog = ref<HTMLDialogElement | null>(null)
const url = ref('')
const natural = ref({ width: 0, height: 0 })
// 默认缩放比例位于滑杆正中间，往左缩小、往右放大。
const sliderPos = ref(0)
const zoom = computed(() => 2 ** sliderPos.value)
const offset = reactive({ x: 0, y: 0 })
const loadError = ref('')
let dragging = false
let lastX = 0
let lastY = 0

const drawScale = computed(() => {
  const { width, height } = natural.value
  if (!width || !height) return 1
  // 以 cover 基准缩放，保证裁剪框始终被图片填满。
  return Math.max(FRAME / width, FRAME / height) * zoom.value
})

const imgStyle = computed(() => ({
  width: natural.value.width * drawScale.value + 'px',
  height: natural.value.height * drawScale.value + 'px',
  transform: `translate(${offset.x}px, ${offset.y}px)`,
}))

function clampOffset() {
  const width = natural.value.width * drawScale.value
  const height = natural.value.height * drawScale.value
  // 图片小于裁剪框时居中放置，铺满或超出时限制在框内拖动。
  offset.x = width <= FRAME ? (FRAME - width) / 2 : Math.min(0, Math.max(FRAME - width, offset.x))
  offset.y = height <= FRAME ? (FRAME - height) / 2 : Math.min(0, Math.max(FRAME - height, offset.y))
}

function startDrag(event: PointerEvent) {
  if (loadError.value) return
  dragging = true
  lastX = event.clientX
  lastY = event.clientY
  dialog.value?.querySelector('.crop-frame')?.setPointerCapture?.(event.pointerId)
}

function onDrag(event: PointerEvent) {
  if (!dragging) return
  offset.x += event.clientX - lastX
  offset.y += event.clientY - lastY
  lastX = event.clientX
  lastY = event.clientY
  clampOffset()
}

function endDrag() { dragging = false }

async function load() {
  url.value = URL.createObjectURL(props.file)
  try {
    const image = new Image()
    image.src = url.value
    await image.decode()
    natural.value = { width: image.naturalWidth, height: image.naturalHeight }
    sliderPos.value = 0
    offset.x = (FRAME - natural.value.width * drawScale.value) / 2
    offset.y = (FRAME - natural.value.height * drawScale.value) / 2
  } catch {
    loadError.value = '图片读取失败，请更换文件重试。'
  }
}

// 将裁剪框映射回原图像素区域，导出为 PNG 供上传接口使用。
function confirm() {
  const scale = drawScale.value
  const sourceSize = FRAME / scale
  const sx = -offset.x / scale
  const sy = -offset.y / scale
  const output = Math.min(MAX_OUTPUT, Math.max(1, Math.round(sourceSize)))
  const canvas = document.createElement('canvas')
  canvas.width = output
  canvas.height = output
  const context = canvas.getContext('2d')
  if (!context) { loadError.value = '当前浏览器不支持图片裁剪。'; return }
  const image = new Image()
  image.src = url.value
  image.decode().then(() => {
    context.drawImage(image, sx, sy, sourceSize, sourceSize, 0, 0, output, output)
    canvas.toBlob((blob) => {
      if (blob) emit('confirm', blob)
      else { loadError.value = '图片导出失败，请重试。' }
    }, 'image/png')
  }).catch(() => { loadError.value = '图片导出失败，请重试。' })
}

onMounted(() => {
  dialog.value?.showModal()
  void load()
})

onBeforeUnmount(() => URL.revokeObjectURL(url.value))
</script>

<template>
  <dialog ref="dialog" aria-labelledby="avatar-crop-title" @close="emit('cancel')">
    <h2 id="avatar-crop-title">选择头像展示区域</h2>
    <p class="crop-hint">拖动图片调整位置，滑杆缩放大小。</p>
    <div v-if="!loadError" class="crop-frame" @pointerdown="startDrag" @pointermove="onDrag" @pointerup="endDrag" @pointercancel="endDrag">
      <img v-if="url" :src="url" :style="imgStyle" draggable="false" alt="待裁剪的头像原图" />
    </div>
    <p v-if="loadError" role="alert">{{ loadError }}</p>
    <label v-if="!loadError" class="zoom-row">缩放<input v-model.number="sliderPos" type="range" :min="MIN_SLIDER" :max="MAX_SLIDER" step="0.01" @input="clampOffset" /></label>
    <div class="dialog-actions">
      <button type="button" @click="emit('cancel')">取消</button>
      <button v-if="!loadError" type="button" class="primary" @click="confirm">截取并上传</button>
      <button v-else type="button" class="primary" @click="emit('cancel')">关闭</button>
    </div>
  </dialog>
</template>

<style scoped>
dialog {
  width: min(400px, 92vw);
  padding: 24px;
  border: 1px solid #e1e6db;
  border-radius: 14px;
  background: white;
  box-shadow: 0 18px 50px #183d3126;
}

dialog::backdrop {
  background: #183d3144;
}

h2 {
  margin: 0;
  font-size: 1.1rem;
}

.crop-hint {
  margin: 8px 0 14px;
  color: #7b8774;
  font-size: 0.8rem;
}

.crop-frame {
  position: relative;
  overflow: hidden;
  width: 300px;
  height: 300px;
  margin: auto;
  border-radius: 50%;
  background: #f0f2ea;
  touch-action: none;
  cursor: grab;
}

.crop-frame:active {
  cursor: grabbing;
}

.crop-frame img {
  position: absolute;
  left: 0;
  top: 0;
  max-width: none;
  user-select: none;
  -webkit-user-drag: none;
}

p[role='alert'] {
  margin: 10px 0 0;
  color: #862a2b;
  font-size: 0.8rem;
}

.zoom-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  color: #46564b;
  font-size: 0.85rem;
}

.zoom-row input {
  flex: 1;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

.dialog-actions button {
  padding: 10px 18px;
  border: 1px solid #b9c2b9;
  border-radius: 8px;
  background: #f6f8f2;
  color: #224c42;
  font: inherit;
  cursor: pointer;
}

.dialog-actions .primary {
  background: #245443;
  border-color: #245443;
  color: #fff;
}
</style>
