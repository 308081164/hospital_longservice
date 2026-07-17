import { AppRouteRecord } from '@/types/router'

export const billingConfigRoutes: AppRouteRecord[] = [
  {
    path: '/billing-config',
    name: 'BillingConfig',
    component: '/index/index',
    meta: {
      title: 'menus.billingConfig.title',
      icon: 'ri:truck-line',
      keepAlive: true
    },
    children: [
      {
        path: 'export-templates',
        name: 'BillingConfigExportTemplates',
        component: '/billing-config/export-templates',
        meta: {
          title: 'menus.billingConfig.exportTemplates',
          icon: 'ri:file-excel-2-line',
          keepAlive: true,
          roles: ['billing_configurator', 'R_SUPER', 'R_ADMIN']
        }
      },
      {
        path: 'logistics-import',
        name: 'BillingConfigLogisticsImport',
        component: '/billing-config/logistics-import',
        meta: {
          title: 'menus.billingConfig.logisticsImport',
          icon: 'ri:route-line',
          keepAlive: true
        }
      },
      {
        path: 'logistics-card',
        name: 'BillingConfigLogisticsCard',
        component: '/billing-config/logistics-card',
        meta: {
          title: 'menus.billingConfig.logisticsCard',
          icon: 'ri:bank-card-line',
          keepAlive: true
        }
      },
      {
        path: 'roster',
        name: 'BillingConfigRoster',
        component: '/billing-config/roster',
        meta: {
          title: 'menus.billingConfig.roster',
          icon: 'ri:user-search-line',
          keepAlive: true,
          roles: ['billing_configurator', 'R_SUPER', 'R_ADMIN']
        }
      },
      {
        path: 'dept-physician',
        name: 'BillingConfigDeptPhysician',
        component: '/billing-config/dept-physician',
        meta: {
          title: 'menus.billingConfig.deptPhysician',
          icon: 'ri:hospital-line',
          keepAlive: true
        }
      },
      {
        path: 'external-instruments',
        name: 'BillingConfigExternalInstruments',
        component: '/billing-config/external-instruments',
        meta: {
          title: 'menus.billingConfig.externalInstruments',
          icon: 'ri:surgical-mask-line',
          keepAlive: true,
          roles: ['billing_configurator', 'R_SUPER', 'R_ADMIN']
        }
      }
    ]
  }
]
