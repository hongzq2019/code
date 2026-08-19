/** 后端所有成功响应共用的信封结构。 */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId: string
}

/** 后端错误响应中可供前端展示或追踪的字段。 */
export interface ApiErrorPayload {
  code?: number
  message?: string
  traceId?: string
}
