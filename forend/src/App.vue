<script setup lang="ts">
/** 应用根布局：管理导航、会话入口以及始终可见的风险提示。 */
import { useRouter } from 'vue-router'
import RiskDisclaimer from './components/RiskDisclaimer.vue'
import { useSessionStore } from './stores/session'

const session = useSessionStore()
const router = useRouter()

/** 清理本地会话后返回公开首页。 */
function exit(): void {
  session.logout()
  router.push('/')
}
</script>

<template>
  <el-container>
    <el-header>
      <div class="brand">大乐透 · 数据分析</div>
      <el-menu mode="horizontal" router :default-active="$route.path">
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/draws">历史开奖</el-menu-item>
        <el-menu-item v-if="session.loggedIn" index="/analysis">历史点位分析</el-menu-item>
        <el-menu-item index="/predict">参考号码</el-menu-item>
        <el-menu-item v-if="session.role === 'ADMIN'" index="/admin/sync">数据同步</el-menu-item>
        <el-menu-item v-if="session.role === 'ADMIN'" index="/admin/reviews">待审核用户</el-menu-item>
      </el-menu>
      <div class="account">
        <el-button v-if="!session.loggedIn" link @click="router.push('/login')">
          登录 / 注册
        </el-button>
        <el-button v-else link @click="exit">退出</el-button>
      </div>
    </el-header>
    <el-main><router-view /></el-main>
    <el-footer>
      <RiskDisclaimer />
    </el-footer>
  </el-container>
</template>
