<script setup lang="ts">
import { computed } from 'vue'
import { emailError, phoneError, profileDraft } from '../profile/fields'

const props = defineProps<{ draft: ReturnType<typeof profileDraft>; prefix: string }>()

// 输入过程中即时校验邮箱和手机号，非法值在保存前就提示，不让其提交到后端。
const emailMessage = computed(() => emailError(props.draft.email.trim()))
const phoneMessage = computed(() => phoneError(props.draft.phoneNumber.trim()))
</script>

<template>
  <div class="profile-fields">
    <div><label :for="prefix + '-gender'">性别</label><select :id="prefix + '-gender'" v-model="draft.gender"><option value="" disabled>未设置</option><option :value="0">未设置</option><option :value="1">男</option><option :value="2">女</option></select></div>
    <div><label :for="prefix + '-bio'">个人简介</label><textarea :id="prefix + '-bio'" v-model="draft.bio" rows="4" maxlength="128" /><small>最多 128 个字符，留空清除。</small></div>
    <div>
      <label :for="prefix + '-email'">邮箱</label>
      <input :id="prefix + '-email'" v-model="draft.email" type="email" maxlength="254" autocomplete="email" spellcheck="false" :aria-invalid="Boolean(emailMessage)" :aria-describedby="emailMessage ? prefix + '-email-error' : undefined" />
      <small v-if="emailMessage" class="field-error" role="alert">{{ emailMessage }}</small>
      <small v-else>邮箱会展示在公开资料中。</small>
    </div>
    <div>
      <label :for="prefix + '-phone'">手机号</label>
      <input :id="prefix + '-phone'" v-model="draft.phoneNumber" type="tel" autocomplete="tel" inputmode="tel" :aria-invalid="Boolean(phoneMessage)" :aria-describedby="phoneMessage ? prefix + '-phone-error' : undefined" />
      <small v-if="phoneMessage" class="field-error" role="alert">{{ phoneMessage }}</small>
      <small v-else>6–20 位数字，可带 + 前缀。仅本人和管理员可见。</small>
    </div>
    <div><label :for="prefix + '-birth'">出生年月</label><input :id="prefix + '-birth'" v-model="draft.birthMonth" type="month" max="9999-12" /><small>通过弹窗选择年月；留空清除。</small></div>
  </div>
</template>

<style scoped>
.profile-fields { display: grid; gap: 24px; }
.profile-fields > div { display: grid; gap: 8px; }
label { font-size: .875rem; font-weight: 600; }
input, select, textarea { width: 100%; min-width: 0; box-sizing: border-box; padding: 12px 14px; border: 1px solid #bac8ba; border-radius: 8px; background: #fcfdfb; color: #193d31; font: inherit; }
input[aria-invalid='true'] { border-color: #b3574f; }
/* 去掉原生贴边箭头，改用自定义 SVG 箭头，定位在离右边框 14px 处。 */
select { appearance: none; padding-right: 34px; background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='8' viewBox='0 0 12 8'%3E%3Cpath d='M1 1.5l5 5 5-5' fill='none' stroke='%23626f5e' stroke-width='1.6' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E"); background-repeat: no-repeat; background-position: right 14px center; }
textarea { resize: vertical; }
small { color: #738276; line-height: 1.5; }
.field-error { color: #a23c35; }
</style>
