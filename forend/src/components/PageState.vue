<script setup lang="ts">
/**
 * 复用的页面状态切换器：优先显示加载、错误、空数据，正常时再渲染默认插槽。
 */
defineProps<{
  loading: boolean
  error?: string
  empty: boolean
}>()
</script>

<template>
  <el-skeleton v-if="loading" :rows="4" animated />
  <el-alert v-else-if="error" type="error" :title="error" :closable="false" />
  <el-empty v-else-if="empty" description="暂无数据" />
  <slot v-else />
</template>
