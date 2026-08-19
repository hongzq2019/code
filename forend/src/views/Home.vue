<script setup lang="ts">
/** 首页展示产品说明和最新一期开奖，并提供进入预测页的入口。 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import NumberBalls from '../components/NumberBalls.vue'
import PageState from '../components/PageState.vue'
import { listDraws } from '../api/lottery'
import { toUserMessage } from '../api/client'
import type { Draw } from '../types/lottery'

const router = useRouter()
const latestDraw = ref<Draw>()
const loading = ref(false)
const errorMessage = ref('')

/** 列表按后端默认排序获取一条记录，作为首页最新开奖。 */
async function loadLatestDraw(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const page = await listDraws({ size: 1 })
    latestDraw.value = page.items[0]
  } catch (error) {
    errorMessage.value = toUserMessage(error)
  } finally {
    loading.value = false
  }
}

onMounted(loadLatestDraw)
</script>

<template>
  <section class="hero">
    <p class="eyebrow">大乐透历史数据分析</p>
    <h1>用可解释的历史相似性，生成娱乐参考号码</h1>
    <p>查询历史开奖、查看相似样本，并在充分理解随机性的前提下进行数据探索。</p>
    <el-button type="primary" size="large" @click="router.push('/predict')">
      生成参考号码
    </el-button>
  </section>

  <PageState :loading="loading" :error="errorMessage" :empty="!latestDraw">
    <el-card v-if="latestDraw">
      <template #header>最新开奖 · {{ latestDraw.issueNo }}（{{ latestDraw.drawDate }}）</template>
      <NumberBalls :front="latestDraw.numbers.front" :back="latestDraw.numbers.back" />
    </el-card>
  </PageState>
</template>
