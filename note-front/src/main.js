import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import {
  ArrowDown,
  ArrowRight,
  Avatar,
  Back,
  ChatRound,
  Clock,
  Close,
  CopyDocument,
  Delete,
  Document,
  DocumentAdd,
  Download,
  Edit,
  Expand,
  Fold,
  Folder,
  Hide,
  MagicStick,
  Memo,
  More,
  MoreFilled,
  Plus,
  Search,
  Star,
  Upload,
  UploadFilled,
  User,
  UserFilled,
  View,
} from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './style.css'
import './global.css'
import VueMarkdownEditor from '@kangc/v-md-editor'
import '@kangc/v-md-editor/lib/style/base-editor.css'
import vuepressTheme from '@kangc/v-md-editor/lib/theme/vuepress.js'
import '@kangc/v-md-editor/lib/theme/style/vuepress.css'
import Prism from 'prismjs'

VueMarkdownEditor.use(vuepressTheme, {
  Prism,
})

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(ElementPlus)
app.use(VueMarkdownEditor)
app.use(router)

app.component('ArrowDown', ArrowDown)
app.component('ArrowRight', ArrowRight)
app.component('Avatar', Avatar)
app.component('Back', Back)
app.component('ChatRound', ChatRound)
app.component('Clock', Clock)
app.component('Close', Close)
app.component('CopyDocument', CopyDocument)
app.component('Delete', Delete)
app.component('Document', Document)
app.component('DocumentAdd', DocumentAdd)
app.component('Download', Download)
app.component('Edit', Edit)
app.component('Expand', Expand)
app.component('Fold', Fold)
app.component('Folder', Folder)
app.component('Hide', Hide)
app.component('MagicStick', MagicStick)
app.component('Memo', Memo)
app.component('More', More)
app.component('MoreFilled', MoreFilled)
app.component('Plus', Plus)
app.component('Search', Search)
app.component('Star', Star)
app.component('Upload', Upload)
app.component('UploadFilled', UploadFilled)
app.component('User', User)
app.component('UserFilled', UserFilled)
app.component('View', View)

app.mount('#app')
