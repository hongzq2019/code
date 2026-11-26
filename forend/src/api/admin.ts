import { client, unwrap } from './client'
import type { ApiEnvelope } from '../types/api'
import type {
  ApprovalRequest,
  ImportedDraw,
  ImportDrawRequest,
  PendingUserPage,
  PendingUserQuery,
  SyncResult,
  TrainingRunResult,
} from '../types/admin'

/** 官方开奖多页抓取与向量同步允许的最长等待时间。 */
const MANUAL_SYNC_TIMEOUT_MS = 10 * 60 * 1000

/** 按页获取等待管理员审批的注册账户。 */
export async function listPendingUsers(params: PendingUserQuery): Promise<PendingUserPage> {
  const response = await client.get<ApiEnvelope<PendingUserPage>>('/api/v1/admin/users', { params })
  return unwrap(response)
}

/** 提交指定账户的审批结果。 */
export async function approveUser(username: string, request: ApprovalRequest): Promise<void> {
  const response = await client.post<ApiEnvelope<null>>(
    `/api/v1/admin/users/${username}/approval`,
    request,
  )
  unwrap(response)
}

/** 手动触发指定页数的官方开奖抓取与向量索引同步。 */
export async function runDltSync(pageCount = 30): Promise<SyncResult> {
  const response = await client.post<ApiEnvelope<SyncResult>>('/api/v1/admin/dlt/sync', null, {
    params: { pageCount },
    timeout: MANUAL_SYNC_TIMEOUT_MS,
  })
  return unwrap(response)
}

/** 立即执行五种预测模型的完整训练和批次切换。 */
export async function retrainModels(): Promise<TrainingRunResult> {
  const response = await client.post<ApiEnvelope<TrainingRunResult>>(
    '/api/v1/admin/dlt/model/retrain',
    null,
    { timeout: 10 * 60 * 1000 },
  )
  return unwrap(response)
}

/** 录入单期开奖数据，并由后端负责后续的向量同步。 */
export async function importDraw(request: ImportDrawRequest): Promise<ImportedDraw> {
  const response = await client.post<ApiEnvelope<ImportedDraw>>('/api/v1/admin/dlt/import', request)
  return unwrap(response)
}
