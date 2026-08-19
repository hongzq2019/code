import { client, unwrap } from './client'
import type { ApiEnvelope } from '../types/api'
import type { Draw, DrawPage, DrawQuery } from '../types/lottery'

/** 按期号筛选并分页查询已入库的开奖数据。 */
export async function listDraws(params: DrawQuery): Promise<DrawPage> {
  const response = await client.get<ApiEnvelope<DrawPage>>('/api/v1/dlt/draws', { params })
  return unwrap(response)
}

/**
 * 查询指定开奖日期区间内的记录，为历史点位分析提供完整的时间序列数据。
 */
export async function listDrawsForAnalysis(startDate: string, endDate: string): Promise<Draw[]> {
  const response = await client.get<ApiEnvelope<Draw[]>>('/api/v1/dlt/draws/analysis', {
    params: { startDate, endDate },
  })
  return unwrap(response)
}

/** 查询指定期号的单期开奖详情。 */
export async function getDraw(issueNo: string): Promise<Draw> {
  const response = await client.get<ApiEnvelope<Draw>>(`/api/v1/dlt/draws/${issueNo}`)
  return unwrap(response)
}
