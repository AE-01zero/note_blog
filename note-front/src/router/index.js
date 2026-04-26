import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store'

const routes = [
  {
    path: '/',
    redirect: '/notebook'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue')
  },
  {
    path: '/notebook',
    name: 'Notebook',
    component: () => import('@/views/Notebook.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/blog',
    name: 'BlogHome',
    component: () => import('@/views/BlogHome.vue')
  },
  {
    path: '/blog/manage',
    name: 'BlogManage',
    component: () => import('@/views/BlogManage.vue'),
    meta: { requiresAuth: true, requiresDefaultAdmin: true }
  },
  {
    path: '/blog/edit/:id?',
    name: 'BlogEditor',
    redirect: (to) => {
      const query = { ...to.query, compose: '1' }
      if (to.params.id) {
        query.editId = String(to.params.id)
      }
      return { path: '/blog/manage', query }
    },
    meta: { requiresAuth: true, requiresDefaultAdmin: true }
  },
  {
    path: '/blog/:id',
    name: 'BlogDetail',
    component: () => import('@/views/BlogDetail.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const requiresAuth = !!to.meta.requiresAuth || !!to.meta.requiresDefaultAdmin

  if (!requiresAuth) {
    next()
    return
  }

  if (!userStore.isLoggedIn) {
    next('/login')
    return
  }

  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch (error) {
      next('/login')
      return
    }
  }

  if (to.meta.requiresDefaultAdmin && !userStore.isDefaultAdmin) {
    ElMessage.error('仅默认管理员可访问该页面')
    next('/notebook')
    return
  }

  next()
})

export default router
