import request from '@/utils/http'

/** 创建用户（注册） */
export function createUser(data: {
  username: string
  email: string
  password: string
  isActive?: boolean
  isSuperuser?: boolean
  roleIds?: number[]
}) {
  return request.post<any>({
    url: '/api/v1/users',
    data
  })
}

/** 获取菜单列表（树形结构，用于动态路由和侧边栏） */
export function fetchGetMenuList() {
  return request.get<any[]>({
    url: '/api/v3/system/menus'
  })
}
