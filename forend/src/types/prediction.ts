import type { DltNumbers } from './lottery'

/** 后端支持的四种下一期开奖模型。 */
export type PredictionModelType =
  | 'LOGISTIC_REGRESSION'
  | 'VECTOR_TRANSITION'
  | 'DYNAMIC_ENSEMBLE'
  | 'DECAYED_FREQUENCY'

/** 滚动验证指标及对应随机基线。 */
export interface ModelMetrics {
  frontLogLoss: number
  backLogLoss: number
  frontMeanHits: number
  backMeanHits: number
  frontBaselineLogLoss: number
  backBaselineLogLoss: number
  frontBaselineHits: number
  backBaselineHits: number
}

/** 动态集成模型在前后区分别使用的基础模型权重。 */
export interface EnsembleWeights {
  frontLogisticWeight: number
  frontVectorWeight: number
  backLogisticWeight: number
  backVectorWeight: number
  logisticModelVersion: string
  vectorModelVersion: string
}

/** 单个模型的号码、完整候选分数及训练信息。 */
export interface ModelPrediction {
  type: PredictionModelType
  name: string
  numbers: DltNumbers
  frontScores: Record<string, number>
  backScores: Record<string, number>
  modelVersion: string
  trainedAt: string
  drawCount: number
  trainedThroughIssueNo: string
  metrics: ModelMetrics
  ensembleWeights: EnsembleWeights | null
  frontBeatsBaseline: boolean
  backBeatsBaseline: boolean
}

/** 同一生效批次生成的四模型下一期开奖预测。 */
export interface PredictionResult {
  generatedAt: string
  basedOnIssueNo: string
  disclaimer: string
  predictions: ModelPrediction[]
}
