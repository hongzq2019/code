import axios, { type AxiosError } from 'axios'
import type { ApiEnvelope, ApiErrorPayload } from '../types/api'

/** 与会话状态仓库共用的访问令牌存储键。 */
const ACCESS_TOKEN_KEY = 'dlt-token'

export const client = axios.create({
  baseURL: '/',
  timeout: 10_000,
})

/** 在请求发出前统一附加 Bearer 令牌，避免各接口重复处理鉴权。 */
client.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(ACCESS_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 提取后端统一响应信封中的业务数据。 */
export function unwrap<T>(response: { data: ApiEnvelope<T> }): T {
  return response.data.data
}

/** 将 Axios 或未知异常转换为可直接展示给用户的提示文本。 */
export function toUserMessage(error: unknown, fallback = '请求失败，请稍后重试'): string {
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<ApiErrorPayload>
    if (axiosError.code === 'ECONNABORTED' || axiosError.code === 'ETIMEDOUT') {
      return '请求等待超时，后台任务可能仍在执行，请稍后刷新数据'
    }
    return axiosError.response?.data?.message ?? fallback
  }
  return fallback
}
