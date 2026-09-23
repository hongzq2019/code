import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './styles/tokens.css'
import './styles/base.css'
import './style.css'
import './styles/prediction.css'
import App from './App.vue'
import router from './router'

/**
 * 装配状态管理、路由和已配置简体中文的 UI 组件库后挂载 Vue 应用。
 */
createApp(App).use(createPinia()).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
