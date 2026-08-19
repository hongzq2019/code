<script setup lang="ts">
/**
 * 历史点位分析页：按日期区间读取开奖数据并展示前后区双散点图。
 */
import { onMounted, ref } from 'vue'
import { toUserMessage } from '../api/client'
import { listDrawsForAnalysis } from '../api/lottery'
import DrawScatterChart from '../components/DrawScatterChart.vue'
import PageState from '../components/PageState.vue'
import type { Draw } from '../types/lottery'

const draws = ref<Draw[]>([])
const loading = ref(false)
const errorMessage = ref('')
const dateRange = ref<[string, string]>(defaultRange())

/**
 * 将本地日期格式化为后端 ISO 日期参数所需的 YYYY-MM-DD 格式。
 */
function formatDate(value: Date): string {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * 以当天为结束、同日往前一年为开始，生成最近十二个月默认范围。
 */
function defaultRange(): [string, string] {
  const endDate = new Date()
  const startDate = new Date(endDate)
  startDate.setFullYear(startDate.getFullYear() - 1)
  return [formatDate(startDate), formatDate(endDate)]
}

/**
 * 读取当前选择日期区间内的开奖数据并同步页面状态。
 */
async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [startDate, endDate] = dateRange.value
    draws.value = await listDrawsForAnalysis(startDate, endDate)
  } catch (error) {
    errorMessage.value = toUserMessage(error)
  } finally {
    loading.value = false
  }
}

/**
 * 应用日期选择器中的新范围并重新加载散点数据。
 */
function handleSearch(): void {
  void load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <el-card>
    <template #header>历史点位分析</template>
    <el-form inline @submit.prevent="handleSearch">
      <el-form-item label="开奖日期">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          :clearable="false"
        />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="loading">查询</el-button>
    </el-form>

    <p class="muted">上图为前区号码（1–35，红色）；下图为后区号码（1–12，蓝色）。悬浮点位可查看期号和开奖日期。</p>

    <PageState :loading="loading" :error="errorMessage" :empty="!draws.length">
      <DrawScatterChart :draws="draws" />
    </PageState>
  </el-card>
</template>
