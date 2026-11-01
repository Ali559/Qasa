import { useDark, useToggle } from '@vueuse/core'

// Follows the OS until the admin picks a side; the choice persists per browser.
const isDark = useDark({ storageKey: 'qasa-admin-theme' })
const toggleTheme = useToggle(isDark)

export function useTheme() {
  return { isDark, toggleTheme }
}
