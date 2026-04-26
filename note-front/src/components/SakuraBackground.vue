<template>
  <div class="sakura-scene" aria-hidden="true">
    <div
      v-for="petal in petals"
      :key="petal.id"
      class="sakura-petal"
      :style="petal.shellStyle"
    >
      <span class="sakura-petal-shape" :style="petal.shapeStyle"></span>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

const petals = ref([])

const clamp = (value, min, max) => Math.min(max, Math.max(min, value))

const getPetalCount = () => {
  const width = window.innerWidth
  const height = window.innerHeight
  const isTallViewport = height >= 900

  if (width < 640) return isTallViewport ? 18 : 16
  if (width < 1024) return isTallViewport ? 30 : 26
  return isTallViewport ? 44 : 38
}

const createPetal = (index, total) => {
  const lane = (index / total) * 100
  const laneOffset = Math.random() * 10 - 5
  const size = 14 + Math.random() * 24
  const height = size * (0.72 + Math.random() * 0.24)
  const opacity = 0.36 + Math.random() * 0.34
  const tint = 0.62 + Math.random() * 0.2
  const scale = 0.74 + Math.random() * 0.68
  const duration = 10 + Math.random() * 8

  return {
    id: `${index}-${Math.round(Math.random() * 100000)}`,
    shellStyle: {
      left: `${clamp(lane + laneOffset, -6, 104)}vw`,
      '--fall-duration': `${duration}s`,
      '--fall-delay': `${-Math.random() * duration * 1.6}s`,
      '--drift-start': `${Math.random() * 96 - 48}px`,
      '--drift-mid': `${Math.random() * 220 - 110}px`,
      '--drift-end': `${Math.random() * 340 - 170}px`,
      '--petal-opacity': `${opacity}`
    },
    shapeStyle: {
      width: `${size}px`,
      height: `${height}px`,
      '--spin-duration': `${3.8 + Math.random() * 4.6}s`,
      '--spin-delay': `${-Math.random() * 6}s`,
      '--spin-angle': `${Math.random() * 360}deg`,
      '--blur-radius': `${Math.random() * 0.8}px`,
      '--petal-scale': `${scale}`,
      '--petal-tint': `${tint}`
    }
  }
}

const regeneratePetals = () => {
  const count = getPetalCount()
  petals.value = Array.from({ length: count }, (_, index) => createPetal(index, count))
}

let resizeTimer = 0

const handleResize = () => {
  window.clearTimeout(resizeTimer)
  resizeTimer = window.setTimeout(() => {
    regeneratePetals()
  }, 120)
}

onMounted(() => {
  regeneratePetals()
  window.addEventListener('resize', handleResize, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  window.clearTimeout(resizeTimer)
})
</script>

<style scoped>
.sakura-scene {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  filter: saturate(1.12);
}

.sakura-scene::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 16% 12%, rgba(255, 234, 241, 0.24), transparent 16%),
    radial-gradient(circle at 82% 18%, rgba(222, 233, 248, 0.2), transparent 18%);
}

.sakura-scene::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 28% 26%, rgba(255, 223, 235, 0.18), transparent 20%),
    radial-gradient(circle at 72% 32%, rgba(241, 230, 214, 0.14), transparent 18%);
}

.sakura-petal {
  position: fixed;
  top: -18vh;
  opacity: var(--petal-opacity, 0.56);
  will-change: transform;
  animation: sakura-fall var(--fall-duration) linear infinite;
  animation-delay: var(--fall-delay);
}

.sakura-petal-shape {
  display: block;
  position: relative;
  border-radius: 90% 14% 78% 18%;
  background:
    radial-gradient(circle at 28% 24%, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0) 36%),
    linear-gradient(
      155deg,
      rgba(255, 250, 252, calc(var(--petal-tint) * 0.94)),
      rgba(249, 217, 229, var(--petal-tint)) 56%,
      rgba(222, 168, 193, calc(var(--petal-tint) * 0.9))
    );
  box-shadow: 0 8px 18px rgba(214, 171, 190, 0.22), 0 0 18px rgba(255, 225, 236, 0.18);
  filter: blur(var(--blur-radius));
  transform-origin: 50% 82%;
  transform: rotate(var(--spin-angle)) scale(var(--petal-scale));
  animation: sakura-spin var(--spin-duration) ease-in-out infinite alternate;
  animation-delay: var(--spin-delay);
}

.sakura-petal-shape::before {
  content: '';
  position: absolute;
  inset: 12% 18% auto auto;
  width: 38%;
  height: 34%;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.4);
  filter: blur(1px);
}

.sakura-petal-shape::after {
  content: '';
  position: absolute;
  top: 14%;
  left: 50%;
  width: 1px;
  height: 64%;
  border-radius: 999px;
  background: rgba(213, 116, 155, 0.34);
  transform: translateX(-50%) rotate(14deg);
}

@keyframes sakura-fall {
  0% {
    transform: translate3d(var(--drift-start), -14vh, 0);
  }

  45% {
    transform: translate3d(var(--drift-mid), 42vh, 0);
  }

  100% {
    transform: translate3d(var(--drift-end), 112vh, 0);
  }
}

@keyframes sakura-spin {
  0% {
    transform: rotate(var(--spin-angle)) scale(var(--petal-scale));
  }

  48% {
    transform: rotate(calc(var(--spin-angle) + 72deg)) scale(calc(var(--petal-scale) * 1.04));
  }

  100% {
    transform: rotate(calc(var(--spin-angle) + 148deg)) scale(var(--petal-scale));
  }
}

@media (prefers-reduced-motion: reduce) {
  .sakura-scene {
    display: none;
  }
}
</style>
