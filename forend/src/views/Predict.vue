<script setup lang="ts">
/**
 * 预测页：可用最近一期或自定义号码作为查询向量，展示后端返回的参考号码和相似样本。
 */
import { computed, ref } from 'vue'
import NumberBalls from '../components/NumberBalls.vue'
import PageState from '../components/PageState.vue'
import RiskDisclaimer from '../components/RiskDisclaimer.vue'
import { createPrediction } from '../api/prediction'
import { toUserMessage } from '../api/client'
import type { PredictionInputType, PredictionResult } from '../types/prediction'

const inputType = ref<PredictionInputType>('LATEST_DRAW')
const frontText = ref('1,6,12,20,35')
const backText = ref('3,11')
const result = ref<PredictionResult>()
const loading = ref(false)
const errorMessage = ref('')

const hasResult = computed(() => Boolean(result.value))

/** 将逗号分隔的输入转为数字数组；号码边界和数量由后端最终校验。 */
function parseNumbers(value: string): number[] {
  return value
    .split(',')
    .map((item) => Number(item.trim()))
    .filter(Boolean)
}

/** 请求预测接口，并将加载、错误和结果状态反馈给页面。 */
async function submit(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    result.value = await createPrediction({
      inputType: inputType.value,
      topK: 10,
      frontNumbers: parseNumbers(frontText.value),
      backNumbers: parseNumbers(backText.value),
    })
  } catch (error) {
    errorMessage.value = toUserMessage(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-row :gutter="20">
    <el-col :md="10">
      <el-card>
        <template #header>参考号码生成</template>
        <el-radio-group v-model="inputType">
          <el-radio value="LATEST_DRAW">最近一期</el-radio>
          <el-radio value="CUSTOM_NUMBERS">自定义号码</el-radio>
        </el-radio-group>
        <div v-if="inputType === 'CUSTOM_NUMBERS'" class="fields">
          <el-input v-model="frontText" placeholder="前区：5 个号码，以逗号分隔" />
          <el-input v-model="backText" placeholder="后区：2 个号码，以逗号分隔" />
        </div>
        <el-button type="primary" :loading="loading" @click="submit">生成</el-button>
      </el-card>
    </el-col>
    <el-col :md="14">
      <PageState :loading="loading" :error="errorMessage" :empty="!hasResult">
        <el-card v-if="result">
          <template #header>本次参考号码</template>
          <NumberBalls :front="result.numbers.front" :back="result.numbers.back" />
          <RiskDisclaimer :message="result.disclaimer" />
          <h3>相似历史期</h3>
          <el-table :data="result.similarDraws">
            <el-table-column prop="issueNo" label="期号" />
            <el-table-column prop="drawDate" label="日期" />
            <el-table-column prop="similarity" label="相似度" />
          </el-table>
        </el-card>
      </PageState>
    </el-col>
  </el-row>
</template>
