<script setup lang="ts">
/** 预测页：一次加载三种模型结果，通过下拉框只展示用户选中的一组号码。 */
import { computed, ref } from 'vue'
import NumberBalls from '../components/NumberBalls.vue'
import PageState from '../components/PageState.vue'
import RiskDisclaimer from '../components/RiskDisclaimer.vue'
import { createPrediction } from '../api/prediction'
import { toUserMessage } from '../api/client'
import type {
  ModelPrediction,
  PredictionModelType,
  PredictionResult,
} from '../types/prediction'

const selectedModel = ref<PredictionModelType>('DYNAMIC_ENSEMBLE')
const result = ref<PredictionResult>()
const loading = ref(false)
const errorMessage = ref('')

const modelOptions: Array<{ value: PredictionModelType; label: string }> = [
  { value: 'DYNAMIC_ENSEMBLE', label: '动态集成（主推荐）' },
  { value: 'LOGISTIC_REGRESSION', label: '逻辑回归' },
  { value: 'VECTOR_TRANSITION', label: '向量转移' },
]

const selectedPrediction = computed<ModelPrediction | undefined>(() =>
  result.value?.predictions.find((prediction) => prediction.type === selectedModel.value),
)

function percentage(value: number): string {
  return `${(value * 100).toFixed(0)}%`
}

/** 请求一次完整批次；模型切换只读取本地结果，不再次访问后端。 */
async function submit(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    result.value = await createPrediction()
    if (!selectedPrediction.value && result.value.predictions.length > 0) {
      selectedModel.value = result.value.predictions[0].type
    }
  } catch (error) {
    errorMessage.value = toUserMessage(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-card class="prediction-action-card">
    <template #header>下一期开奖参考</template>
    <div class="prediction-actions">
      <el-button type="primary" :loading="loading" @click="submit">
        生成下一期参考号码
      </el-button>
      <el-select v-model="selectedModel" aria-label="预测模型" class="prediction-model-select">
        <el-option
          v-for="option in modelOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
    </div>
  </el-card>

  <PageState :loading="loading" :error="errorMessage" :empty="!selectedPrediction">
    <div v-if="result && selectedPrediction" class="prediction-grid">
      <el-card class="prediction-card">
        <template #header>
          <div class="prediction-card__header">
            <span>{{ selectedPrediction.name }}</span>
            <el-tag v-if="selectedPrediction.type === 'DYNAMIC_ENSEMBLE'" type="success">
              主推荐
            </el-tag>
          </div>
        </template>

        <NumberBalls
          :front="selectedPrediction.numbers.front"
          :back="selectedPrediction.numbers.back"
        />

        <el-descriptions :column="2" border class="prediction-metadata">
          <el-descriptions-item label="模型版本">{{ selectedPrediction.modelVersion }}</el-descriptions-item>
          <el-descriptions-item label="依据期号">{{ result.basedOnIssueNo }}</el-descriptions-item>
          <el-descriptions-item label="训练水位">{{ selectedPrediction.trainedThroughIssueNo }}</el-descriptions-item>
          <el-descriptions-item label="样本期数">{{ selectedPrediction.drawCount }}</el-descriptions-item>
          <el-descriptions-item label="前区 LogLoss">{{ selectedPrediction.metrics.frontLogLoss.toFixed(4) }}</el-descriptions-item>
          <el-descriptions-item label="后区 LogLoss">{{ selectedPrediction.metrics.backLogLoss.toFixed(4) }}</el-descriptions-item>
          <el-descriptions-item label="前区平均命中">{{ selectedPrediction.metrics.frontMeanHits.toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="后区平均命中">{{ selectedPrediction.metrics.backMeanHits.toFixed(2) }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="selectedPrediction.ensembleWeights" class="ensemble-weights">
          <span>前区：逻辑回归 {{ percentage(selectedPrediction.ensembleWeights.frontLogisticWeight) }} / 向量转移 {{ percentage(selectedPrediction.ensembleWeights.frontVectorWeight) }}</span>
          <span>后区：逻辑回归 {{ percentage(selectedPrediction.ensembleWeights.backLogisticWeight) }} / 向量转移 {{ percentage(selectedPrediction.ensembleWeights.backVectorWeight) }}</span>
        </div>
      </el-card>
      <RiskDisclaimer :message="result.disclaimer" />
    </div>
  </PageState>
</template>
