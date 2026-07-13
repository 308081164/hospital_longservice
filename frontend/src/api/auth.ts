import request from '@/utils/http'

export function fetchLogin(params: { userName: string; password: string }) {
  return request.post<Api.Auth.LoginResponse>({
    url: '/api/v1/base/access_token',
    data: { username: params.userName, password: params.password }
  })
}

export async function fetchGetUserInfo() {
  const data = await request.get<Record<string, unknown>>({
    url: '/api/v1/base/userinfo'
  })
  // normalize backend format to what template expects
  return {
    id: data.id,
    userId: data.id,
    username: data.username,
    userName: data.username,
    email: data.email,
    is_active: data.is_active,
    is_superuser: data.is_superuser,
    avatar: data.avatar,
    roles: data.roles || [],
    buttons: [],
    createdAt: data.createdAt,
    updatedAt: data.updatedAt,
    last_login: data.last_login,
  } as unknown as Api.Auth.UserInfo
}
