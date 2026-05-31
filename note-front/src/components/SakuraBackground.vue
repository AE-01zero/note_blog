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

  if (width < 640) return 12
  if (width < 1024) return 18
  return 24
}

const createPetal = (index, total) => {
  const lane = (index / total) * 100
  const laneOffset = Math.random() * 8 - 4
  const size = 14 + Math.random() * 18
  const height = size * (0.72 + Math.random() * 0.24)
  const opacity = 0.32 + Math.random() * 0.42
  const tint = 0.72 + Math.random() * 0.18
  const scale = 0.78 + Math.random() * 0.64

  return {
    id: `${index}-${Math.round(Math.random() * 100000)}`,
    shellStyle: {
      left: `${clamp(lane + laneOffset, -6, 104)}vw`,
      '--fall-duration': `${12 + Math.random() * 10}s`,
      '--fall-delay': `${-Math.random() * 24}s`,
      '--drift-start': `${Math.random() * 64 - 32}px`,
      '--drift-mid': `${Math.random() * 140 - 70}px`,
      '--drift-end': `${Math.random() * 220 - 110}px`,
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
}

.sakura-scene::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 18% 12%, rgba(255, 234, 241, 0.28), transparent 16%),
    radial-gradient(circle at 82% 18%, rgba(255, 214, 230, 0.18), transparent 18%);
}

.sakura-petal {
  position: fixed;
  top: -16vh;
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
    radial-gradient(circle at 28% 24%, rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0) 36%),
    linear-gradient(
      155deg,
      rgba(255, 249, 252, calc(var(--petal-tint) * 0.94)),
      rgba(255, 210, 228, var(--petal-tint)) 56%,
      rgba(235, 144, 177, calc(var(--petal-tint) * 0.94))
    );
  box-shadow: 0 8px 18px rgba(231, 155, 186, 0.18);
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
