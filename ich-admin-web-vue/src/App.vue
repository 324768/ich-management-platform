<template>
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import { computed } from 'vue'
import { ElConfigProvider } from 'element-plus'
import { useThemeStore } from '@/store/theme'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import ja from 'element-plus/es/locale/lang/ja'

const themeStore = useThemeStore()

const localeMap = { zh: zhCn, en, ja }
const elementLocale = computed(() => localeMap[themeStore.language] || zhCn)
</script>

<style>
#app {
  width: 100%;
  height: 100%;
}
</style>
