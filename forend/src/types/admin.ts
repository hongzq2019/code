import type { Draw } from './lottery'
import type { PendingUser } from './auth'
import type { ModelMetrics, PredictionModelType } from './prediction'

/** 管理员对待审核账户的审批载荷。 */
export interface ApprovalRequest {
  approved: boolean
}

/** 后台同步官方开奖数据后的执行摘要。 */
export interface SyncResult {
  result: string
  fetchedCount: number
  changedCount: number
  message: string
}

export type TrainingRunStatus =
  | 'STARTED'
  | 'SUCCESS'
  | 'RETAINED_ACTIVE_MODEL'
  | 'INSUFFICIENT_DATA'
  | 'TRAINING_IN_PROGRESS'
  | 'FAILED'

/** 单种模型的本次训练摘要。 */
export interface ModelTrainingResult {
  type: PredictionModelType
  modelVersion: string
  drawCount: number
  sampleCount: number
  trainedThroughDrawId: number
  trainedThroughIssueNo: string
  metrics: ModelMetrics
}

/** 管理员手动重训返回的批次状态。 */
export interface TrainingRunResult {
  status: TrainingRunStatus
  batchVersion: string | null
  message: string
  models: ModelTrainingResult[]
}

/** 手工录入一期大乐透开奖数据的请求体。 */
export interface ImportDrawRequest {
  issueNo: string
  drawDate: string
  frontNumbers: number[]
  backNumbers: number[]
}

/** 待审核用户列表的服务端分页参数。 */
export interface PendingUserQuery {
  page: number
  size: number
}

/** 待审核用户列表的服务端分页结果。 */
export interface PendingUserPage {
  items: PendingUser[]
  page: number
  size: number
  total: number
}

/** 导入成功后返回的开奖记录，与普通开奖详情结构一致。 */
export type ImportedDraw = Draw
