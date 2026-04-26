import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建axios实例
const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    // 从sessionStorage获取token
    const token = sessionStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    console.log('API响应:', response) // 调试信息
    const res = response.data
    
    // 如果响应没有code字段，直接返回整个response对象
    if (res.code === undefined) {
      return response
    }
    
    // 如果有code字段且为0，返回整个response对象
    if (res.code === 0) {
      return response
    } else {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
  },
  error => {
    console.error('网络错误详情:', error) // 调试信息
    if (error.response && error.response.status === 401) {
      sessionStorage.removeItem('token')
      ElMessage.error('您的账号已在其他设备登录，请重新登录')
      window.location.href = '/login'
      return Promise.reject(error)
    }
    ElMessage.error('网络错误: ' + (error.message || '未知错误'))
    return Promise.reject(error)
  }
)

export default request 