import { AppRouteRecord } from '@/types/router'

export const masterDataRoutes: AppRouteRecord[] = [
  {
    path: '/master-data',
    name: 'MasterData',
    component: '/index/index',
    meta: {
      title: 'menus.masterData.title',
      icon: 'ri:database-2-line',
      keepAlive: true
    },
    children: [
      {
        path: 'customers',
        name: 'MasterDataCustomers',
        component: '/master-data/customers',
        meta: {
          title: 'menus.masterData.customers',
          icon: 'ri:team-line',
          keepAlive: true
        }
      },
      {
        path: 'customers/:customerId/dept-physician',
        name: 'MasterDataCustomerDeptPhysician',
        component: '/billing-config/dept-physician',
        meta: {
          title: 'menus.billingConfig.deptPhysician',
          isHide: true,
          keepAlive: true
        }
      },
      {
        path: 'product-categories',
        name: 'MasterDataProductCategories',
        component: '/master-data/product-categories',
        meta: {
          title: 'menus.masterData.productCategories',
          icon: 'ri:folder-settings-line',
          keepAlive: true
        }
      },
      {
        path: 'products',
        name: 'MasterDataProducts',
        component: '/master-data/products',
        meta: {
          title: 'menus.masterData.products',
          icon: 'ri:product-hunt-line',
          keepAlive: true
        }
      }
    ]
  }
]
