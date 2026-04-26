<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isBlogPage = computed(() => route.path.startsWith('/blog'))
</script>

<template>
  <div id="app" :class="{ 'blog-mode': isBlogPage }">
    <router-view />
  </div>
</template>

<style>
#app {
  font-family: "Avenir Next", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #1e293b;
  height: 100vh;
  max-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at 10% 10%, rgba(255, 196, 215, 0.36), transparent 24%),
    radial-gradient(circle at 88% 14%, rgba(134, 220, 234, 0.28), transparent 22%),
    radial-gradient(circle at 70% 84%, rgba(255, 219, 173, 0.2), transparent 26%),
    linear-gradient(135deg, #eef3ff 0%, #f5f8ff 32%, #fff8f3 72%, #f4f9ff 100%);
  display: flex;
  flex-direction: column;
}

#app.blog-mode {
  overflow: auto;
  height: auto;
  min-height: 100vh;
  max-height: none;
  background:
    radial-gradient(circle at 12% 8%, rgba(255, 196, 215, 0.26), transparent 22%),
    radial-gradient(circle at 84% 14%, rgba(134, 220, 234, 0.2), transparent 18%),
    linear-gradient(180deg, #f7f8ff 0%, #fffaf6 42%, #f3f8ff 100%);
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body {
  height: 100%;
  max-height: 100vh;
  overflow: hidden;
}

html:has(.blog-mode),
html:has(.blog-mode) body {
  overflow: auto;
  max-height: none;
}

body {
  background:
    radial-gradient(circle at 10% 10%, rgba(255, 196, 215, 0.32), transparent 24%),
    radial-gradient(circle at 88% 14%, rgba(134, 220, 234, 0.22), transparent 22%),
    linear-gradient(135deg, #eef3ff 0%, #f5f8ff 30%, #fff8f3 70%, #f4f9ff 100%);
  font-family: "Avenir Next", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  line-height: 1.6;
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: rgba(140, 166, 227, 0.08);
  border-radius: 4px;
}

::-webkit-scrollbar-thumb {
  background: linear-gradient(135deg, #7aa2ff 0%, #86dcea 56%, #ffbfd7 100%);
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(135deg, #688cf2 0%, #7ed2df 56%, #f4aac8 100%);
}
</style>
