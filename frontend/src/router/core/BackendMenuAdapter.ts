/**
 * 将后端菜单树转换为前端路由菜单结构
 */
import type { AppRouteRecord } from '@/types/router'
import { RoutesAlias } from '../routesAlias'

export interface BackendMenuNode {
  id?: number
  name: string
  menuType?: string
  icon?: string
  path: string
  order?: number
  parentId?: number
  isHidden?: boolean
  component?: string
  keepalive?: boolean
  redirect?: string | null
  children?: BackendMenuNode[]
}

function toRouteName(path: string): string {
  return path
    .replace(/^\//, '')
    .split(/[/-]/)
    .filter(Boolean)
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join('')
}

function resolveComponent(component?: string, menuType?: string, hasChildren?: boolean): string {
  if (component === 'Layout' || (menuType === 'catalog' && hasChildren)) {
    return RoutesAlias.Layout
  }
  return component || ''
}

function adaptMenuNode(node: BackendMenuNode): AppRouteRecord {
  const hasChildren = Array.isArray(node.children) && node.children.length > 0
  const children = hasChildren ? node.children!.map(adaptMenuNode) : undefined

  return {
    id: node.id,
    path: node.path,
    name: toRouteName(node.path),
    component: resolveComponent(node.component, node.menuType, hasChildren),
    redirect: node.redirect || undefined,
    meta: {
      title: node.name,
      icon: node.icon,
      keepAlive: node.keepalive ?? true,
      isHide: node.isHidden ?? false
    },
    children
  }
}

export function adaptBackendMenus(nodes: BackendMenuNode[]): AppRouteRecord[] {
  if (!Array.isArray(nodes)) {
    return []
  }
  return nodes.map(adaptMenuNode)
}
