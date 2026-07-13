import { AppRouteRecord } from '@/types/router'

export const hospitalRoutes: AppRouteRecord[] = [
  {
    path: '/hospital',
    name: 'HospitalTracking',
    component: '/index/index',
    meta: {
      title: 'menus.hospital.title',
      icon: 'ri:file-excel-2-line',
      keepAlive: true,
    },
    children: [
      {
        path: 'reconciliation',
        name: 'HospitalReconciliation',
        component: '/hospital/reconciliation',
        meta: {
          title: 'menus.hospital.reconciliation',
          icon: 'ri:file-excel-2-line',
          keepAlive: true,
        },
      },
      {
        path: 'pricing-rules',
        redirect: '/settings/pricing-rules',
        meta: {
          title: 'menus.settings.pricingRules',
          isHide: true,
        },
      },
    ],
  },
]
