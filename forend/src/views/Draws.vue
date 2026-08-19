<script setup lang="ts">
/**
 * 历史开奖查询页，支持按期号筛选和每页二十条的分页浏览。
 */
import { onMounted, ref } from 'vue'
import NumberBalls from '../components/NumberBalls.vue'
import PageState from '../components/PageState.vue'
import { toUserMessage } from '../api/client'
import { listDraws } from '../api/lottery'
import type { Draw } from '../types/lottery'

const rows = ref<Draw[]>([])
const total = ref(0)
const issueNo = ref('')
const loading = ref(false)
const errorMessage = ref('')
const PAGE_SIZE = 20
const page = ref(1)

/**
 * 根据当前页码和期号筛选项加载开奖记录，并维护统一的页面状态。
 */
async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await listDraws({
      page: page.value,
      size: PAGE_SIZE,
      issueNo: issueNo.value || undefined,
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = toUserMessage(error)
  } finally {
    loading.value = false
  }
}

/**
 * 应用新的期号筛选条件时回到第一页，避免落在无数据的旧页码。
 */
function handleSearch(): void {
  page.value = 1
  void load()
}

/**
 * 切换分页器页码后读取相应的开奖记录。
 */
function handlePageChange(currentPage: number): void {
  page.value = currentPage
  void load()
}

onMounted(load)
</script>

<template>
  <el-card>
    <template #header>历史开奖</template>
    <el-form inline @submit.prevent="handleSearch">
      <el-form-item label="期号">
        <el-input v-model="issueNo" clearable />
      </el-form-item>
      <el-button type="primary" native-type="submit">查询</el-button>
    </el-form>

    <PageState :loading="loading" :error="errorMessage" :empty="!rows.length">
      <el-table :data="rows" stripe>
        <el-table-column prop="issueNo" label="期号" />
        <el-table-column prop="drawDate" label="开奖日期" />
        <el-table-column label="前区">
          <template #default="{ row }: { row: Draw }">
            <NumberBalls :front="row.numbers.front" :back="[]" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="后区">
          <template #default="{ row }: { row: Draw }">
            <NumberBalls :front="[]" :back="row.numbers.back" size="small" />
          </template>
        </el-table-column>
      </el-table>
      <div class="draw-pagination">
        <p class="muted">共 {{ total }} 条记录，每页 {{ PAGE_SIZE }} 条</p>
        <el-pagination
          v-if="total > 0"
          :current-page="page"
          :page-size="PAGE_SIZE"
          :total="total"
          layout="prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </PageState>
  </el-card>
</template>

<style scoped>
.draw-pagination {
  display: flex;
  gap: var(--space-4);
  align-items: center;
  justify-content: space-between;
  margin-top: var(--space-4);
}

@media (max-width: 600px) {
  .draw-pagination {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
