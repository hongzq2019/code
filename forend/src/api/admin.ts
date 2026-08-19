import { client, unwrap } from './client'
import type { ApiEnvelope } from '../types/api'
import type {
  ApprovalRequest,
  ImportedDraw,
  ImportDrawRequest,
  PendingUserPage,
  PendingUserQuery,
  SyncResult,
} from '../types/admin'

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

/** 手动触发官方大乐透开奖数据抓取与向量索引同步。 */
export async function runDltSync(): Promise<SyncResult> {
  const response = await client.post<ApiEnvelope<SyncResult>>('/api/v1/admin/dlt/sync')
  return unwrap(response)
}

/** 录入单期开奖数据，并由后端负责后续的向量同步。 */
export async function importDraw(request: ImportDrawRequest): Promise<ImportedDraw> {
  const response = await client.post<ApiEnvelope<ImportedDraw>>('/api/v1/admin/dlt/import', request)
  return unwrap(response)
}
