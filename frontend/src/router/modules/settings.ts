import { AppRouteRecord } from '@/types/router'

export const settingsRoutes: AppRouteRecord[] = [
  {
    path: '/settings',
    name: 'Settings',
    component: '/index/index',
    meta: {
      title: 'menus.settings.title',
      icon: 'ri:settings-3-line',
      keepAlive: true,
    },
    children: [
      {
        path: 'appearance',
        name: 'SettingsAppearance',
        meta: {
          title: 'menus.settings.appearance',
          icon: 'ri:palette-line',
          openSettingPanel: true,
        },
      },
      {
        path: 'pricing-rules',
        name: 'SettingsPricingRules',
        component: '/hospital/pricing-rules',
        meta: {
          title: 'menus.settings.pricingRules',
          icon: 'ri:price-tag-3-line',
          keepAlive: true,
        },
      },
      {
        path: 'version-management',
        name: 'SettingsVersionManagement',
        component: '/hospital/version-management',
        meta: {
          title: 'menus.settings.versionManagement',
          icon: 'ri:history-line',
          keepAlive: true,
        },
      },
    ],
  },
]
