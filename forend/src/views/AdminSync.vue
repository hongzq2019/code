<script setup lang="ts">
/** 管理员数据同步页：手动触发官方开奖抓取及后续的 Milvus 索引同步。 */
import { ref } from 'vue'
import { runDltSync } from '../api/admin'
import { toUserMessage } from '../api/client'
import type { SyncResult } from '../types/admin'

const syncResult = ref<SyncResult>()
const syncing = ref(false)
const errorMessage = ref('')

/** 执行一次服务端同步，并将摘要或错误直接展示给管理员。 */
async function sync(): Promise<void> {
  syncing.value = true
  errorMessage.value = ''
  try {
    syncResult.value = await runDltSync()
  } catch (error) {
    errorMessage.value = toUserMessage(error)
  } finally {
    syncing.value = false
  }
}
</script>

<template>
  <el-card>
    <template #header>数据同步</template>
    <p>生产环境请配置合法的数据源、访问频率与重试策略。</p>
    <el-button type="primary" :loading="syncing" @click="sync">手动同步</el-button>
    <el-alert
      v-if="syncResult"
      type="success"
      :closable="false"
      :title="syncResult.message"
    />
    <el-alert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" />
  </el-card>
</template>
