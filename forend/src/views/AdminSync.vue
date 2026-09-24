<script setup lang="ts">
/** 管理员数据同步页：独立控制官方采集和四模型手动重训。 */
import { ref } from 'vue'
import { retrainModels, runDltSync } from '../api/admin'
import { toUserMessage } from '../api/client'
import type { SyncResult, TrainingRunResult } from '../types/admin'

const pageCount = ref(30)
const syncResult = ref<SyncResult>()
const syncing = ref(false)
const syncError = ref('')
const trainingResult = ref<TrainingRunResult>()
const training = ref(false)
const trainingError = ref('')

const modelNames = {
  LOGISTIC_REGRESSION: '逻辑回归',
  VECTOR_TRANSITION: '向量转移',
  DYNAMIC_ENSEMBLE: '动态集成',
  DECAYED_FREQUENCY: '历史衰减概率',
} as const

/** 按管理员指定页数执行一次采集与向量同步。 */
async function sync(): Promise<void> {
  syncing.value = true
  syncError.value = ''
  syncResult.value = undefined
  try {
    syncResult.value = await runDltSync(pageCount.value)
  } catch (error) {
    syncError.value = toUserMessage(error)
  } finally {
    syncing.value = false
  }
}

/** 同步等待响应，展示实际训练批次状态，不与数据采集结果混用。 */
async function retrain(): Promise<void> {
  training.value = true
  trainingError.value = ''
  trainingResult.value = undefined
  try {
    trainingResult.value = await retrainModels()
  } catch (error) {
    trainingError.value = toUserMessage(error)
  } finally {
    training.value = false
  }
}
</script>

<template>
  <div class="admin-sync-grid">
    <el-card>
      <template #header>开奖数据同步</template>
      <p>从中国体彩网抓取开奖，写入 PostgreSQL 后同步 Milvus 索引。</p>
      <div class="admin-sync-actions">
        <el-input-number v-model="pageCount" :min="1" :max="100" controls-position="right" />
        <el-button type="primary" :loading="syncing" @click="sync">手动同步开奖</el-button>
      </div>
      <el-alert
        v-if="syncResult"
        :type="syncResult.result === 'SUCCESS' ? 'success' : 'warning'"
        :closable="false"
        :title="syncResult.message"
        :description="`抓取 ${syncResult.fetchedCount} 期，新增或修正 ${syncResult.changedCount} 期`"
      />
      <el-alert v-if="syncError" type="error" :closable="false" :title="syncError" />
    </el-card>

    <el-card>
      <template #header>预测模型训练</template>
      <p>基于当前 PostgreSQL 历史数据回测四种模型，并按评估结果切换生效批次。</p>
      <el-button type="success" :loading="training" @click="retrain">手动重训模型</el-button>
      <el-alert
        v-if="trainingResult"
        :type="trainingResult.status === 'SUCCESS' ? 'success' : 'warning'"
        :closable="false"
        :title="trainingResult.message"
      />
      <el-alert v-if="trainingError" type="error" :closable="false" :title="trainingError" />

      <el-table v-if="trainingResult?.models.length" :data="trainingResult.models">
        <el-table-column label="模型" min-width="110">
          <template #default="scope">{{ modelNames[scope.row.type as keyof typeof modelNames] }}</template>
        </el-table-column>
        <el-table-column prop="trainedThroughIssueNo" label="训练水位" min-width="110" />
        <el-table-column prop="drawCount" label="开奖期数" width="90" />
        <el-table-column prop="sampleCount" label="样本数" width="90" />
        <el-table-column label="前区 LogLoss" min-width="120">
          <template #default="scope">{{ scope.row.metrics.frontLogLoss.toFixed(4) }}</template>
        </el-table-column>
        <el-table-column label="后区 LogLoss" min-width="120">
          <template #default="scope">{{ scope.row.metrics.backLogLoss.toFixed(4) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
