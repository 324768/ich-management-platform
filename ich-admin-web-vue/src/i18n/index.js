import { createI18n } from 'vue-i18n'
import zh from './zh.js'
import en from './en.js'
import zhTW from './ja.js'

const i18n = createI18n({
  legacy: false,
  locale: localStorage.getItem('ich_lang') || 'zh',
  fallbackLocale: 'zh',
  messages: { zh, en, 'zh-TW': zhTW },
})

export default i18n
