/** 大乐透号码分区：前区 5 个、后区 2 个。 */
export interface DltNumbers {
  front: number[]
  back: number[]
}

/** PostgreSQL 主库中保存并对外展示的一期开奖记录。 */
export interface Draw {
  id: number
  issueNo: string
  drawDate: string
  numbers: DltNumbers
  sourceUrl: string
}

/** 开奖列表的分页和期号筛选参数。 */
export interface DrawQuery {
  page?: number
  size?: number
  issueNo?: string
}

/** 开奖记录分页查询结果。 */
export interface DrawPage {
  items: Draw[]
  page: number
  size: number
  total: number
}
