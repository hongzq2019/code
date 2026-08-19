import { createRouter, createWebHistory } from 'vue-router'
import AdminSyncView from './views/AdminSync.vue'
import AnalysisView from './views/Analysis.vue'
import DrawsView from './views/Draws.vue'
import HomeView from './views/Home.vue'
import LoginView from './views/Login.vue'
import PendingUsersView from './views/PendingUsers.vue'
import PredictView from './views/Predict.vue'
import { useSessionStore } from './stores/session'

/** 应用路由表；预测页要求登录，管理页额外要求管理员角色。 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomeView },
    { path: '/draws', component: DrawsView },
    { path: '/analysis', component: AnalysisView, meta: { auth: true } },
    { path: '/predict', component: PredictView, meta: { auth: true } },
    { path: '/login', component: LoginView },
    { path: '/admin', redirect: '/admin/sync' },
    { path: '/admin/sync', component: AdminSyncView, meta: { auth: true, admin: true } },
    { path: '/admin/reviews', component: PendingUsersView, meta: { auth: true, admin: true } },
  ],
})

/** 统一执行路由访问控制，避免仅依赖导航菜单隐藏受限页面。 */
router.beforeEach((to) => {
  const session = useSessionStore()
  if (to.meta.auth && !session.loggedIn) {
    return '/login'
  }
  if (to.meta.admin && session.role !== 'ADMIN') {
    return '/'
  }
  return true
})

export default router
