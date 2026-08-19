<script setup lang="ts">
/** 展示预测结果必需的风险提示，允许后端返回的文案覆盖默认值。 */
withDefaults(
  defineProps<{
    message?: string
  }>(),
  { message: '仅根据历史数据相似性生成，用于数据分析与娱乐，不构成中奖概率承诺。' },
)
</script>

<template>
  <el-alert type="warning" :closable="false" :title="message" />
</template>
