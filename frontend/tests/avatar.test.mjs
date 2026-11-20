import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { stripTypeScriptTypes } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'
import { computed, shallowRef, triggerRef, watch } from 'vue'

const upload = (avatarUrl) => ({ avatarUrl, avatarOriginalUrl: avatarUrl.replace('.webp', '-original.png') })

// 使用真实头像模块与 Vue 响应式状态，仅替换网络和会话边界，定序返回请求。
async function setup() {
  const session = shallowRef({ accessToken: 'first-token', user: { id: 42 } })
  const requests = []
  const invalidations = []
  const context = vm.createContext({})
  const dependencies = {
    vue: { computed, shallowRef, triggerRef, watch },
    '../auth/session': {
      getActiveSession: () => session.value,
      invalidateSession: (token) => {
        invalidations.push(token)
        if (session.value?.accessToken === token) session.value = null
      },
    },
    '../auth/api': { isInvalidSessionError: (error) => error.code === 4012 },
    './api': {
      fetchAvatar: (id, token) => new Promise((resolve, reject) => {
        requests.push({ id, token, resolve, reject })
      }),
    },
  }
  const source = await readFile(new URL('../src/profile/avatar.ts', import.meta.url), 'utf8')
  const module = new vm.SourceTextModule(stripTypeScriptTypes(source), { context })
  await module.link((specifier) => {
    const exports = dependencies[specifier]
    assert.ok(exports, 'Unexpected module dependency: ' + specifier)
    return new vm.SyntheticModule(Object.keys(exports), function () {
      for (const [key, value] of Object.entries(exports)) this.setExport(key, value)
    }, { context })
  })
  await module.evaluate()
  return { avatar: module.namespace, session, requests, invalidations }
}

test('保存后新发 GET，旧 GET 先返回也不清除草稿', async () => {
  const { avatar, requests } = await setup()
  assert.equal(requests.length, 1)
  avatar.setAvatarPreview(upload('https://images.invalid/new.webp'), 'first-token')
  // 模拟 PATCH 已完成，此时初始查询仍在等待服务端响应。
  const savedRead = avatar.refreshAvatar(true)
  assert.equal(requests.length, 2)
  requests[0].resolve('https://images.invalid/old.webp')
  await new Promise(setImmediate)
  assert.equal(avatar.hasAvatarDraft.value, true)
  assert.equal(avatar.avatarDraft.value.avatarOriginalUrl, 'https://images.invalid/new-original.png')
  const sharedRead = avatar.refreshAvatar()
  assert.equal(requests.length, 2, '旧请求的 finally 不得清除新请求的引用')
  requests[1].resolve('https://images.invalid/new.webp')
  assert.equal(await savedRead, true)
  assert.equal(await sharedRead, true)
  assert.equal(avatar.hasAvatarDraft.value, false)
  assert.equal(avatar.avatarPreviewUrl.value, 'https://images.invalid/new.webp')
})

test('保存后新 GET 先返回，晚到的旧 GET 不覆盖已存头像', async () => {
  const { avatar, requests } = await setup()
  avatar.setAvatarPreview(upload('https://images.invalid/new.webp'), 'first-token')
  const savedRead = avatar.refreshAvatar(true)
  requests[1].resolve('https://images.invalid/new.webp')
  assert.equal(await savedRead, true)
  requests[0].resolve('https://images.invalid/old.webp')
  await new Promise(setImmediate)
  assert.equal(avatar.hasAvatarDraft.value, false)
  assert.equal(avatar.avatarPreviewUrl.value, 'https://images.invalid/new.webp')
  avatar.setAvatarPreview(upload('https://images.invalid/unsaved.webp'), 'first-token')
  avatar.discardAvatarDraft()
  assert.equal(avatar.avatarDraft.value, undefined)
  assert.equal(avatar.avatarPreviewUrl.value, 'https://images.invalid/new.webp')
})

test('过期请求的失败不覆盖新查询状态或清除当前会话', async () => {
  const { avatar, requests, invalidations } = await setup()
  const savedRead = avatar.refreshAvatar(true)
  requests[1].resolve('https://images.invalid/new.webp')
  await savedRead
  requests[0].reject({ code: 4012 })
  await new Promise(setImmediate)
  assert.deepEqual(invalidations, [])
  assert.equal(avatar.avatarError.value, '')
  assert.equal(avatar.avatarPreviewUrl.value, 'https://images.invalid/new.webp')
})

test('切换会话后旧查询与上传不能覆盖新账号头像', async () => {
  const { avatar, session, requests } = await setup()
  session.value = { accessToken: 'second-token', user: { id: 99 } }
  assert.equal(requests.length, 2)
  assert.equal(requests[1].id, '99')
  assert.equal(requests[1].token, 'second-token')
  requests[1].resolve('https://images.invalid/second.webp')
  await new Promise(setImmediate)
  requests[0].resolve('https://images.invalid/first.webp')
  avatar.setAvatarPreview(upload('https://images.invalid/late-upload.webp'), 'first-token')
  await new Promise(setImmediate)
  assert.equal(avatar.avatarPreviewUrl.value, 'https://images.invalid/second.webp')
  assert.equal(avatar.hasAvatarDraft.value, false)
  session.value = null
  assert.equal(avatar.avatarPreviewUrl.value, undefined)
})

test('上传草稿仅保存成对地址，取消同时丢弃并恢复原头像', async () => {
  const { avatar, requests } = await setup()
  requests[0].resolve('https://images.invalid/saved.webp')
  await new Promise(setImmediate)
  const result = { ...upload('https://images.invalid/draft.webp'), password: 'ignored-extra-field' }
  avatar.setAvatarPreview(result, 'first-token')
  result.avatarOriginalUrl = 'https://images.invalid/changed-outside.png'
  assert.deepEqual(Object.keys(avatar.avatarDraft.value).sort(), ['avatarOriginalUrl', 'avatarUrl'])
  assert.equal(avatar.avatarDraft.value.avatarOriginalUrl, 'https://images.invalid/draft-original.png')
  avatar.discardAvatarDraft()
  assert.equal(avatar.avatarDraft.value, undefined)
  assert.equal(avatar.avatarPreviewUrl.value, 'https://images.invalid/saved.webp')
})
