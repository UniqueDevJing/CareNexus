import axios from 'axios'

// 后端走 /v2 上下文；dev 下 vite 代理到 127.0.0.1:18092
const http = axios.create({ baseURL: '/v2/api', timeout: 30000, withCredentials: true })

let refreshing = null

http.interceptors.response.use(r => r, async error => {
  const original = error.config
  const path = original?.url || ''
  if (error.response?.status === 401 && original && !original._retry
      && !path.endsWith('/auth/login') && !path.endsWith('/auth/register')
      && !path.endsWith('/auth/refresh') && !path.endsWith('/auth/logout')) {
    original._retry = true
    refreshing ||= axios.post('/v2/api/auth/refresh', null, { withCredentials: true })
      .finally(() => { refreshing = null })
    try {
      await refreshing
      return http(original)
    } catch (_) {
      // Fall through to the normal session cleanup below.
    }
  }
  if (error.response?.status === 401) {
    localStorage.removeItem('hospital_user')
    if (!location.pathname.endsWith('/login')) location.href = '/CareNexus/login'
  }
  return Promise.reject(error)
})

function ok(res) {
  const body = res.data
  if (body && body.code === 200) return body.data
  throw new Error((body && body.msg) || '请求失败')
}

export const api = {
  get: (url, params) => http.get(url, { params }).then(ok),
  post: (url, params, data) => http.post(url, data, { params }).then(ok),
  sse: (url, params) => {
    const qs = new URLSearchParams(params).toString()
    return `${http.defaults.baseURL}${url}?${qs}`
  }
}

export default api
