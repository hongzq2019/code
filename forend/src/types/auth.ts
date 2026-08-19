/** 登录与注册共用的账号凭据。 */
export interface Credentials {
  username: string
  password: string
}

/** 注册接口返回的状态提示。 */
export interface RegistrationResult {
  message: string
}

/** 登录成功后用于初始化浏览器会话的数据。 */
export interface LoginResult {
  accessToken: string
  role: string
  mustChangePassword: boolean
}

/** 刷新令牌接口的响应。 */
export interface TokenResult {
  accessToken: string
}

/** 后台待审批用户列表中的一项。 */
export interface PendingUser {
  username: string
  role: string
  status: string
  mustChangePassword: boolean
  createdAt: string
}
