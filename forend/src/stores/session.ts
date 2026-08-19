import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { loginAccount } from '../api/auth'

/** 与 HTTP 客户端共用的 sessionStorage 键，保证刷新后会话可恢复。 */
const ACCESS_TOKEN_KEY = 'dlt-token'
const ROLE_KEY = 'dlt-role'

/**
 * 浏览器端会话状态：令牌和角色持久化在 sessionStorage，页面刷新时重新读取。
 */
export const useSessionStore = defineStore('session', () => {
  const token = ref(sessionStorage.getItem(ACCESS_TOKEN_KEY) ?? '')
  const role = ref(sessionStorage.getItem(ROLE_KEY) ?? '')
  const loggedIn = computed(() => Boolean(token.value))

  /** 登录成功后同步更新响应式状态与浏览器存储。 */
  async function login(username: string, password: string): Promise<void> {
    const session = await loginAccount({ username, password })
    token.value = session.accessToken
    role.value = session.role
    sessionStorage.setItem(ACCESS_TOKEN_KEY, session.accessToken)
    sessionStorage.setItem(ROLE_KEY, session.role)
  }

  /** 清理内存和 sessionStorage 中的全部本地会话数据。 */
  function logout(): void {
    token.value = ''
    role.value = ''
    sessionStorage.removeItem(ACCESS_TOKEN_KEY)
    sessionStorage.removeItem(ROLE_KEY)
  }

  return { token, role, loggedIn, login, logout }
})
