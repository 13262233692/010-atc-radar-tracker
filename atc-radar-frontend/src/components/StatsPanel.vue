<template>
  <div class="stats-panel">
    <div class="stats-header">
      <h3>系统运行状态</h3>
      <span :class="['conn-status', connected ? 'online' : 'offline']">
        <span class="dot"></span>
        {{ connected ? 'SSE 在线' : 'SSE 离线' }}
      </span>
    </div>

    <el-divider />

    <div class="stats-section">
      <h4 class="section-title">实时统计</h4>
      <div class="stat-cards">
        <div class="stat-card blue">
          <div class="stat-icon"><el-icon :size="28"><Promotion /></el-icon></div>
          <div class="stat-info">
            <span class="stat-value">{{ totalTracks }}</span>
            <span class="stat-label">活动航迹</span>
          </div>
        </div>
        <div class="stat-card green">
          <div class="stat-icon"><el-icon :size="28"><DataLine /></el-icon></div>
          <div class="stat-info">
            <span class="stat-value">{{ cat048Count }}</span>
            <span class="stat-label">CAT 048</span>
          </div>
        </div>
        <div class="stat-card purple">
          <div class="stat-icon"><el-icon :size="28"><Connection /></el-icon></div>
          <div class="stat-info">
            <span class="stat-value">{{ cat062Count }}</span>
            <span class="stat-label">CAT 062</span>
          </div>
        </div>
        <div class="stat-card orange">
          <div class="stat-icon"><el-icon :size="28"><Histogram /></el-icon></div>
          <div class="stat-info">
            <span class="stat-value">{{ avgAltitude }}m</span>
            <span class="stat-label">平均高度</span>
          </div>
        </div>
      </div>
    </div>

    <div class="stats-section">
      <h4 class="section-title">高度分布</h4>
      <div class="altitude-bars">
        <div class="alt-bar">
          <span class="alt-label">高空 (>10km)</span>
          <div class="bar-track">
            <div class="bar-fill" :style="{ width: highAltPercent + '%', background: '#4d96ff' }"></div>
          </div>
          <span class="alt-count">{{ altitudeDistribution.high }}</span>
        </div>
        <div class="alt-bar">
          <span class="alt-label">中空 (7-10km)</span>
          <div class="bar-track">
            <div class="bar-fill" :style="{ width: midAltPercent + '%', background: '#6bcb77' }"></div>
          </div>
          <span class="alt-count">{{ altitudeDistribution.mid }}</span>
        </div>
        <div class="alt-bar">
          <span class="alt-label">低空 (3-7km)</span>
          <div class="bar-track">
            <div class="bar-fill" :style="{ width: lowAltPercent + '%', background: '#ffd93d' }"></div>
          </div>
          <span class="alt-count">{{ altitudeDistribution.low }}</span>
        </div>
        <div class="alt-bar">
          <span class="alt-label">超低空 (<3km)</span>
          <div class="bar-track">
            <div class="bar-fill" :style="{ width: veryLowPercent + '%', background: '#ff9f43' }"></div>
          </div>
          <span class="alt-count">{{ altitudeDistribution.veryLow }}</span>
        </div>
      </div>
    </div>

    <div class="stats-section">
      <h4 class="section-title">数据来源</h4>
      <div class="source-list">
        <div class="source-item" v-for="(count, source) in sourceDistribution" :key="source">
          <span class="source-label">{{ getSourceLabel(source) }}</span>
          <span class="source-count">{{ count }}</span>
        </div>
        <div class="source-item" v-if="Object.keys(sourceDistribution).length === 0">
          <span class="source-label">暂无数据</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Promotion, DataLine, Connection, Histogram } from '@element-plus/icons-vue'
import { getSourceLabel } from '../utils/format'

const props = defineProps({
  tracks: {
    type: Map,
    default: () => new Map()
  },
  connected: {
    type: Boolean,
    default: false
  }
})

const trackList = computed(() => Array.from(props.tracks.values()))

const totalTracks = computed(() => trackList.value.length)

const cat048Count = computed(() => trackList.value.filter(t => t.category === 48).length)
const cat062Count = computed(() => trackList.value.filter(t => t.category === 62).length)

const avgAltitude = computed(() => {
  const alts = trackList.value.filter(t => t.altitude != null).map(t => t.altitude)
  if (alts.length === 0) return 0
  return Math.round(alts.reduce((a, b) => a + b, 0) / alts.length)
})

const altitudeDistribution = computed(() => {
  const dist = { veryLow: 0, low: 0, mid: 0, high: 0 }
  trackList.value.forEach(t => {
    if (t.altitude == null) return
    if (t.altitude < 3000) dist.veryLow++
    else if (t.altitude < 7000) dist.low++
    else if (t.altitude < 10000) dist.mid++
    else dist.high++
  })
  return dist
})

const maxAltCount = computed(() => {
  const d = altitudeDistribution.value
  return Math.max(d.veryLow, d.low, d.mid, d.high, 1)
})

const veryLowPercent = computed(() => (altitudeDistribution.value.veryLow / maxAltCount.value) * 100)
const lowAltPercent = computed(() => (altitudeDistribution.value.low / maxAltCount.value) * 100)
const midAltPercent = computed(() => (altitudeDistribution.value.mid / maxAltCount.value) * 100)
const highAltPercent = computed(() => (altitudeDistribution.value.high / maxAltCount.value) * 100)

const sourceDistribution = computed(() => {
  const dist = {}
  trackList.value.forEach(t => {
    const s = t.source || 'UNKNOWN'
    dist[s] = (dist[s] || 0) + 1
  })
  return dist
})
</script>

<style scoped>
.stats-panel {
  padding: 16px;
}

.stats-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #e6e6e6;
}

.conn-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 12px;
}

.conn-status.online {
  background: rgba(0, 255, 136, 0.15);
  color: #00ff88;
}

.conn-status.offline {
  background: rgba(255, 107, 107, 0.15);
  color: #ff6b6b;
}

.conn-status .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

:deep(.el-divider) {
  border-color: #1e2a3f;
  margin: 12px 0;
}

.stats-section {
  margin-bottom: 20px;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #8892a6;
  margin-bottom: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  background: #141b2a;
  border: 1px solid #1e2a3f;
  border-radius: 8px;
}

.stat-card.blue { border-left: 3px solid #4d96ff; }
.stat-card.green { border-left: 3px solid #00ff88; }
.stat-card.purple { border-left: 3px solid #a78bfa; }
.stat-card.orange { border-left: 3px solid #ff9f43; }

.stat-card.blue .stat-icon { color: #4d96ff; }
.stat-card.green .stat-icon { color: #00ff88; }
.stat-card.purple .stat-icon { color: #a78bfa; }
.stat-card.orange .stat-icon { color: #ff9f43; }

.stat-icon {
  opacity: 0.8;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #e6e6e6;
  font-family: Consolas, monospace;
  line-height: 1.2;
}

.stat-label {
  font-size: 11px;
  color: #5a6478;
  margin-top: 2px;
}

.altitude-bars {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.alt-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.alt-label {
  width: 100px;
  font-size: 12px;
  color: #8892a6;
  flex-shrink: 0;
}

.bar-track {
  flex: 1;
  height: 12px;
  background: #141b2a;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #1e2a3f;
}

.bar-fill {
  height: 100%;
  border-radius: 6px;
  transition: width 0.3s ease;
  min-width: 4px;
}

.alt-count {
  width: 30px;
  text-align: right;
  font-size: 12px;
  font-weight: 600;
  color: #e6e6e6;
  font-family: Consolas, monospace;
}

.source-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.source-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: #141b2a;
  border: 1px solid #1e2a3f;
  border-radius: 4px;
}

.source-label {
  font-size: 13px;
  color: #c0c8d4;
}

.source-count {
  font-size: 13px;
  font-weight: 600;
  color: #00d4ff;
  font-family: Consolas, monospace;
}
</style>
