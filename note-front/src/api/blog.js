import request from './request'

// ========== 文章管理（需登录） ==========

export const createBlogPost = (data) => {
  return request({ url: '/blog/posts', method: 'post', data })
}

export const updateBlogPost = (postId, data) => {
  return request({ url: `/blog/posts/${postId}`, method: 'put', data })
}

export const deleteBlogPost = (postId) => {
  return request({ url: `/blog/posts/${postId}`, method: 'delete' })
}

export const getMyBlogPostDetail = (postId) => {
  return request({ url: `/blog/posts/${postId}`, method: 'get' })
}

export const getMyBlogPosts = (params) => {
  return request({ url: '/blog/posts/mine', method: 'get', params })
}

export const publishBlogPost = (postId) => {
  return request({ url: `/blog/posts/${postId}/publish`, method: 'post' })
}

export const unpublishBlogPost = (postId) => {
  return request({ url: `/blog/posts/${postId}/unpublish`, method: 'post' })
}

export const noteToBlog = (data) => {
  return request({ url: '/blog/posts/from-note', method: 'post', data })
}

// ========== 公开接口（无需登录） ==========

export const getPublicBlogPosts = (params) => {
  return request({ url: '/blog/public', method: 'get', params })
}

export const getPublicBlogPostDetail = (postId) => {
  return request({ url: `/blog/public/${postId}`, method: 'get' })
}

// 获取相关推荐文章（同分类优先，不足则补热门文章）
export const getRelatedPosts = (postId, params) => {
  return request({ url: `/blog/public/${postId}/related`, method: 'get', params })
}

// ========== 分类管理 ==========

export const createCategory = (data) => {
  return request({ url: '/blog/categories', method: 'post', data })
}

export const updateCategory = (categoryId, data) => {
  return request({ url: `/blog/categories/${categoryId}`, method: 'put', data })
}

export const deleteCategory = (categoryId) => {
  return request({ url: `/blog/categories/${categoryId}`, method: 'delete' })
}

export const getMyCategories = () => {
  return request({ url: '/blog/categories', method: 'get' })
}

export const getPublicCategories = () => {
  return request({ url: '/blog/categories/public', method: 'get' })
}

// ========== 标签管理 ==========

export const createTag = (data) => {
  return request({ url: '/blog/tags', method: 'post', data })
}

export const deleteTag = (tagId) => {
  return request({ url: `/blog/tags/${tagId}`, method: 'delete' })
}

export const getMyTags = () => {
  return request({ url: '/blog/tags', method: 'get' })
}
