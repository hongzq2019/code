import { client, unwrap } from './client'
import type { Credentials, LoginResult, RegistrationResult, TokenResult } from '../types/auth'
import type { ApiEnvelope } from '../types/api'

/** 注册账号；账号需通过后台审批后才能登录。 */
export async function registerAccount(credentials: Credentials): Promise<RegistrationResult> {
  const response = await client.post<ApiEnvelope<RegistrationResult>>(
    '/api/v1/auth/register',
    credentials,
  )
  return unwrap(response)
}

/** 使用账号密码换取当前会话的访问令牌及角色信息。 */
export async function loginAccount(credentials: Credentials): Promise<LoginResult> {
  const response = await client.post<ApiEnvelope<LoginResult>>('/api/v1/auth/login', credentials)
  return unwrap(response)
}

/** 请求后端刷新当前会话的访问令牌。 */
export async function refreshAccessToken(): Promise<TokenResult> {
  const response = await client.post<ApiEnvelope<TokenResult>>('/api/v1/auth/refresh')
  return unwrap(response)
}

/** 通知后端注销，并由调用方清理浏览器中的会话状态。 */
export async function logoutAccount(): Promise<void> {
  const response = await client.post<ApiEnvelope<null>>('/api/v1/auth/logout')
  unwrap(response)
}
