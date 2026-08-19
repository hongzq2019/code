<script setup lang="ts">
/** 登录和注册共用的表单页；注册完成后切回登录模式。 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { registerAccount } from '../api/auth'
import { toUserMessage } from '../api/client'
import { useSessionStore } from '../stores/session'

const username = ref('')
const password = ref('')
const registering = ref(false)
const submitting = ref(false)
const session = useSessionStore()
const router = useRouter()

/** 根据当前模式注册账户或建立本地会话。 */
async function submit(): Promise<void> {
  submitting.value = true
  try {
    if (registering.value) {
      const result = await registerAccount({ username: username.value, password: password.value })
      ElMessage.success(result.message)
      registering.value = false
      return
    }

    await session.login(username.value, password.value)
    await router.push('/')
  } catch (error) {
    ElMessage.error(toUserMessage(error))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-card class="login">
    <template #header>{{ registering ? '注册账号' : '登录' }}</template>
    <el-form @submit.prevent="submit">
      <el-form-item label="用户名">
        <el-input v-model="username" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="password" type="password" show-password />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="submitting">
        {{ registering ? '提交注册' : '登录' }}
      </el-button>
      <el-button link @click="registering = !registering">
        {{ registering ? '返回登录' : '注册账号' }}
      </el-button>
    </el-form>
    <el-alert
      v-if="registering"
      type="info"
      :closable="false"
      title="注册后须经管理员审核才可登录"
    />
  </el-card>
</template>
