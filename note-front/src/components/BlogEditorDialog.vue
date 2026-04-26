<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    size="92%"
    :with-header="false"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="handleClosed"
  >
    <div class="editor-window">
      <div class="window-bar">
        <div>
          <div class="window-kicker">Workspace Window</div>
          <h3>{{ postId ? '文章编辑窗口' : '文章写作窗口' }}</h3>
        </div>
        <el-button class="window-close" @click="visible = false">关闭</el-button>
      </div>

      <BlogEditorWorkspace
        :post-id="postId"
        :note-id="noteId"
        :note-title="noteTitle"
        :note-content="noteContent"
        variant="floating"
        editor-height="64vh"
        return-text="关闭窗口"
        @saved="handleSaved"
        @cancel="visible = false"
      />
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import BlogEditorWorkspace from '@/components/BlogEditorWorkspace.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  postId: { type: [String, Number], default: null },
  noteId: { type: [String, Number], default: null },
  noteTitle: { type: String, default: '' },
  noteContent: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const visible = ref(false)

watch(
  () => props.modelValue,
  (value) => {
    visible.value = value
  },
  { immediate: true }
)

watch(visible, (value) => {
  if (value !== props.modelValue) {
    emit('update:modelValue', value)
  }
})

const handleSaved = (payload) => {
  emit('saved', payload)
  visible.value = false
}

const handleClosed = () => {
  emit('update:modelValue', false)
}
</script>

<style scoped>
.editor-window {
  min-height: 100%;
  padding: 18px;
  background:
    radial-gradient(circle at 10% 10%, rgba(255, 196, 215, 0.22), transparent 24%),
    radial-gradient(circle at 88% 14%, rgba(134, 220, 234, 0.18), transparent 22%),
    linear-gradient(180deg, #f8f9ff 0%, #fffaf7 48%, #f4f8ff 100%);
}

.window-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding: 16px 18px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(174, 191, 235, 0.22);
  box-shadow: 0 18px 40px rgba(83, 99, 149, 0.1);
  backdrop-filter: blur(18px);
}

.window-kicker {
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #7c88a5;
}

.window-bar h3 {
  margin: 8px 0 0;
  font-size: 22px;
  color: #24314d;
  font-family: "Source Han Serif SC", "Noto Serif SC", "Songti SC", serif;
}

.window-close {
  border-radius: 999px !important;
}

@media (max-width: 720px) {
  .editor-window {
    padding: 10px;
  }

  .window-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
