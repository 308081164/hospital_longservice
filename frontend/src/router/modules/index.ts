import { AppRouteRecord } from '@/types/router'
import { billingConfigRoutes } from './billing-config'
import { exceptionRoutes } from './exception'
import { hospitalRoutes } from './hospital'
import { masterDataRoutes } from './master-data'
import { settingsRoutes } from './settings'

/**
 * 导出所有模块化路由
 */
export const routeModules: AppRouteRecord[] = [
  ...masterDataRoutes,
  ...billingConfigRoutes,
  ...hospitalRoutes,
  ...settingsRoutes,
  exceptionRoutes,
]
