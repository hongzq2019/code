import { client, unwrap } from './client'
import type { ApiEnvelope } from '../types/api'
import type { PredictionResult } from '../types/prediction'

/** 获取同一生效批次生成的三种下一期开奖预测。 */
export async function createPrediction(): Promise<PredictionResult> {
  const response = await client.post<ApiEnvelope<PredictionResult>>('/api/v1/dlt/predictions', {})
  return unwrap(response)
}
