<script setup lang="ts">
/** 管理员审核页：分页加载待审核账户，并允许逐个通过或拒绝。 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageState from '../components/PageState.vue'
import { approveUser, listPendingUsers } from '../api/admin'
import { toUserMessage } from '../api/client'
import type { PendingUser } from '../types/auth'

const PAGE_SIZE = 10
const users = ref<PendingUser[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')

/**
 * 请求当前页；若审批使末页为空，则自动退回上一页并重新加载。
 */
async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await listPendingUsers({ page: page.value, size: PAGE_SIZE })
    users.value = result.items
    total.value = result.total

    if (users.value.length === 0 && page.value > 1 && total.value <= (page.value - 1) * PAGE_SIZE) {
      page.value -= 1
      await load()
    }
  } catch (error) {
    errorMessage.value = toUserMessage(error)
  } finally {
    loading.value = false
  }
}

/** 提交审批结果，并刷新当前页以移除已处理的账户。 */
async function approve(username: string, approved: boolean): Promise<void> {
  try {
    await approveUser(username, { approved })
    ElMessage.success(approved ? '已通过' : '已拒绝')
    await load()
  } catch (error) {
    ElMessage.error(toUserMessage(error))
  }
}

onMounted(load)
</script>

<template>
  <el-card>
    <template #header>待审核用户</template>
    <PageState :loading="loading" :error="errorMessage" :empty="total === 0">
      <el-table :data="users">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="createdAt" label="注册时间" />
        <el-table-column label="操作">
          <template #default="{ row }: { row: PendingUser }">
            <el-button type="success" size="small" @click="approve(row.username, true)">
              通过
            </el-button>
            <el-button type="danger" size="small" @click="approve(row.username, false)">
              拒绝
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > PAGE_SIZE"
        v-model:current-page="page"
        :page-size="PAGE_SIZE"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="load"
      />
    </PageState>
  </el-card>
</template>
