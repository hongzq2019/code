<script setup lang="ts">
/**
 * 按开奖时间展示前区和后区号码分布的双区域散点图。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ScatterChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use, type ECharts, type EChartsCoreOption } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import type { Draw } from '../types/lottery'

use([ScatterChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

interface ScatterPoint {
  drawDate: string
  issueNo: string
  number: number
  value: [string, number]
}

const props = defineProps<{
  draws: Draw[]
}>()

const chartHost = ref<HTMLDivElement>()
let chart: ECharts | undefined
let resizeObserver: ResizeObserver | undefined

/**
 * 将指定分区的每个开奖号码展开为 ECharts 可消费的单个散点。
 */
function toPoints(zone: 'front' | 'back'): ScatterPoint[] {
  return props.draws.flatMap((draw) =>
    draw.numbers[zone].map((number) => ({
      drawDate: draw.drawDate,
      issueNo: draw.issueNo,
      number,
      value: [draw.drawDate, number],
    })),
  )
}

/**
 * 生成散点悬浮提示，保留期号、开奖日期及具体号码三项信息。
 */
function formatTooltip(parameter: unknown): string {
  const point = (parameter as { data?: ScatterPoint }).data
  if (!point) {
    return ''
  }
  return `${point.issueNo}<br/>开奖日期：${point.drawDate}<br/>号码：${String(point.number).padStart(2, '0')}`
}

/**
 * 构造上方前区、下方后区的独立坐标系配置。
 */
function createOption(): EChartsCoreOption {
  return {
    legend: {
      top: 4,
      data: ['前区号码', '后区号码'],
    },
    tooltip: {
      trigger: 'item',
      formatter: formatTooltip,
    },
    grid: [
      { top: 48, right: 28, bottom: '52%', left: 58 },
      { top: '60%', right: 28, bottom: 42, left: 58 },
    ],
    xAxis: [
      { type: 'time', gridIndex: 0, axisLabel: { hideOverlap: true } },
      { type: 'time', gridIndex: 1, axisLabel: { hideOverlap: true } },
    ],
    yAxis: [
      {
        type: 'value',
        gridIndex: 0,
        name: '前区',
        min: 1,
        max: 35,
        interval: 1,
      },
      {
        type: 'value',
        gridIndex: 1,
        name: '后区',
        min: 1,
        max: 12,
        interval: 1,
      },
    ],
    series: [
      {
        name: '前区号码',
        type: 'scatter',
        xAxisIndex: 0,
        yAxisIndex: 0,
        symbolSize: 9,
        itemStyle: { color: '#ef4444' },
        data: toPoints('front'),
      },
      {
        name: '后区号码',
        type: 'scatter',
        xAxisIndex: 1,
        yAxisIndex: 1,
        symbolSize: 9,
        itemStyle: { color: '#3b82f6' },
        data: toPoints('back'),
      },
    ],
  }
}

/**
 * 将当前开奖数据渲染到已初始化的图表实例中。
 */
function renderChart(): void {
  chart?.setOption(createOption(), { notMerge: true })
}

onMounted(() => {
  if (!chartHost.value) {
    return
  }
  chart = init(chartHost.value)
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartHost.value)
  renderChart()
})

watch(
  () => props.draws,
  () => renderChart(),
  { deep: true },
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div ref="chartHost" class="draw-scatter-chart" />
</template>

<style scoped>
.draw-scatter-chart {
  width: 100%;
  min-height: 1280px;
}
</style>
