import { client, unwrap } from './client'
import type { ApiEnvelope } from '../types/api'
import type { PredictionRequest, PredictionResult } from '../types/prediction'

/** 提交输入号码，获取基于相似开奖向量检索生成的预测结果。 */
export async function createPrediction(request: PredictionRequest): Promise<PredictionResult> {
  const response = await client.post<ApiEnvelope<PredictionResult>>(
    '/api/v1/dlt/predictions',
    request,
  )
  return unwrap(response)
}
