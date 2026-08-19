import type { DltNumbers } from './lottery'

/** 预测输入可取最近一期开奖或用户自定义号码。 */
export type PredictionInputType = 'LATEST_DRAW' | 'CUSTOM_NUMBERS'

/** 发起预测时提交给后端的号码及相似样本数量。 */
export interface PredictionRequest {
  inputType: PredictionInputType
  topK: number
  frontNumbers: number[]
  backNumbers: number[]
}

/** Milvus 向量检索命中的历史相似开奖。 */
export interface SimilarDraw {
  drawId: number
  issueNo: string
  drawDate: string
  numbers: DltNumbers
  similarity: number
}

/** 推荐引擎的完整输出，含推荐号码、评分与相似样本。 */
export interface PredictionResult {
  numbers: DltNumbers
  frontScores: Record<string, number>
  backScores: Record<string, number>
  similarDraws: SimilarDraw[]
  modelVersion: string
  generatedAt: string
  disclaimer: string
}
