<script setup lang="ts">
/**
 * 统一渲染大乐透前区、后区号码；使用不同样式区分两个号码池。
 */
withDefaults(
  defineProps<{
    front: number[]
    back: number[]
    size?: 'small' | 'default'
  }>(),
  { size: 'default' },
)

/** 将单个号码格式化为两位显示，保持列表视觉对齐。 */
function formatNumber(number: number): string {
  return String(number).padStart(2, '0')
}
</script>

<template>
  <div class="number-balls" :class="`number-balls--${size}`">
    <span v-for="number in front" :key="`front-${number}`" class="number-ball number-ball--front">
      {{ formatNumber(number) }}
    </span>
    <span v-for="number in back" :key="`back-${number}`" class="number-ball number-ball--back">
      {{ formatNumber(number) }}
    </span>
  </div>
</template>
