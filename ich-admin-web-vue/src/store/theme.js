import { defineStore } from 'pinia'
import { ref } from 'vue'
import i18n from '@/i18n'

export const useThemeStore = defineStore('theme', () => {
  const theme = ref(localStorage.getItem('ich_theme') || 'light')
  const language = ref(localStorage.getItem('ich_lang') || 'zh')

  // Apply theme to <html> element
  function applyTheme(t) {
    document.documentElement.setAttribute('data-theme', t)
    if (t === 'dark') {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }

  function toggleTheme() {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    localStorage.setItem('ich_theme', theme.value)
    applyTheme(theme.value)
  }

  function setLanguage(lang) {
    language.value = lang
    localStorage.setItem('ich_lang', lang)
    // Sync with vue-i18n
    i18n.global.locale.value = lang
  }

  // Initialize on store creation
  applyTheme(theme.value)

  return { theme, language, toggleTheme, setLanguage, applyTheme }
})
