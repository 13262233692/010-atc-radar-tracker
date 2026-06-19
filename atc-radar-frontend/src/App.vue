<template>
  <div class="app-container">
    <header class="app-header">
      <div class="header-left">
        <el-icon :size="28" color="#00d4ff"><Promotion /></el-icon>
        <h1>ATC 空中交通管制雷达监控系统</h1>
      </div>
      <div class="header-right">
        <div class="status-item">
          <span class="status-label">连接状态:</span>
          <span :class="['status-value', connected ? 'status-online' : 'status-offline']">
            <span class="dot"></span>
            {{ connected ? '已连接' : '已断开' }}
          </span>
        </div>
        <div class="status-item">
          <span class="status-label">活动航迹:</span>
          <span class="status-value highlight">{{ trackCount }}</span>
        </div>
        <div class="status-item">
          <span class="status-label">当前时间:</span>
          <span class="status-value">{{ currentTime }}</span>
        </div>
      </div>
    </header>

    <div class="main-content">
      <div class="map-container">
        <RadarMap
          ref="radarMapRef"
          :tracks="tracks"
          @track-selected="handleTrackSelected"
        />
      </div>

      <aside class="side-panel">
        <el-tabs v-model="activeTab" type="border-card">
          <el-tab-pane label="航迹列表" name="list">
            <TrackList
              :tracks="tracks"
              :selected-track-id="selectedTrackId"
              @select-track="handleTrackSelected"
            />
          </el-tab-pane>
          <el-tab-pane label="航迹详情" name="detail">
            <TrackDetail :track="selectedTrack" />
          </el-tab-pane>
          <el-tab-pane label="系统统计" name="stats">
            <StatsPanel :tracks="tracks" :connected="connected" />
          </el-tab-pane>
        </el-tabs>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Promotion } from '@element-plus/icons-vue'
import RadarMap from './components/RadarMap.vue'
import TrackList from './components/TrackList.vue'
import TrackDetail from './components/TrackDetail.vue'
import StatsPanel from './components/StatsPanel.vue'
import { createSseClient } from './utils/sseClient'
import { fetchAllTracks } from './utils/api'

const tracks = ref(new Map())
const connected = ref(false)
const selectedTrackId = ref(null)
const activeTab = ref('list')
const currentTime = ref('')
const radarMapRef = ref(null)
let sseClient = null
let timeInterval = null

const trackCount = computed(() => tracks.value.size)
const selectedTrack = computed(() => {
  if (!selectedTrackId.value) return null
  return tracks.value.get(selectedTrackId.value) || null
})

function updateTrack(trackData) {
  const existing = tracks.value.get(trackData.trackId)
  if (existing) {
    tracks.value.set(trackData.trackId, {
      ...existing,
      ...trackData,
      history: existing.history ? [...existing.history.slice(-19), {
        latitude: existing.latitude,
        longitude: existing.longitude,
        timestamp: existing.lastUpdate || existing.timestamp
      }] : [{
        latitude: trackData.latitude,
        longitude: trackData.longitude,
        timestamp: trackData.lastUpdate || trackData.timestamp
      }]
    })
  } else {
    tracks.value.set(trackData.trackId, {
      ...trackData,
      history: [{
        latitude: trackData.latitude,
        longitude: trackData.longitude,
        timestamp: trackData.lastUpdate || trackData.timestamp
      }]
    })
  }
}

function handleTrackSelected(trackId) {
  selectedTrackId.value = trackId
  activeTab.value = 'detail'
}

function updateCurrentTime() {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour12: false })
}

function cleanupStaleTracks() {
  const now = Date.now()
  const staleIds = []
  tracks.value.forEach((track, id) => {
    const lastUpdate = track.lastUpdate || track.timestamp
    if (lastUpdate) {
      const age = now - new Date(lastUpdate).getTime()
      if (age > 60000) {
        staleIds.push(id)
      }
    }
  })
  staleIds.forEach(id => tracks.value.delete(id))
}

onMounted(async () => {
  updateCurrentTime()
  timeInterval = setInterval(updateCurrentTime, 1000)
  setInterval(cleanupStaleTracks, 30000)

  try {
    const initialTracks = await fetchAllTracks()
    initialTracks.forEach(t => updateTrack(t))
  } catch (e) {
    console.warn('Failed to fetch initial tracks:', e)
  }

  sseClient = createSseClient({
    onTrack: (track) => {
      updateTrack(track)
    },
    onConnect: () => {
      connected.value = true
    },
    onDisconnect: () => {
      connected.value = false
    }
  })
  sseClient.connect()
})

onUnmounted(() => {
  if (sseClient) sseClient.disconnect()
  if (timeInterval) clearInterval(timeInterval)
})
</script>

<style scoped>
.app-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 24px;
  background: linear-gradient(90deg, #0d1320 0%, #121a2b 100%);
  border-bottom: 1px solid #1e2a3f;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h1 {
  font-size: 20px;
  font-weight: 600;
  background: linear-gradient(90deg, #00d4ff, #00ff88);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 1px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 32px;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-label {
  font-size: 13px;
  color: #8892a6;
}

.status-value {
  font-size: 14px;
  font-weight: 600;
  color: #e6e6e6;
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-value.highlight {
  color: #00ff88;
  font-size: 18px;
}

.status-online {
  color: #00ff88;
}

.status-offline {
  color: #ff6b6b;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.map-container {
  flex: 1;
  position: relative;
}

.side-panel {
  width: 380px;
  border-left: 1px solid #1e2a3f;
  background: #0f1520;
}

:deep(.el-tabs--border-card) {
  border: none;
  background: transparent;
  height: 100%;
}

:deep(.el-tabs--border-card > .el-tabs__header) {
  background: #141b2a;
  border: none;
  border-bottom: 1px solid #1e2a3f;
}

:deep(.el-tabs--border-card > .el-tabs__content) {
  padding: 0;
  height: calc(100% - 58px);
  overflow: hidden;
}

:deep(.el-tab-pane) {
  height: 100%;
  overflow: auto;
}

:deep(.el-tabs__item) {
  color: #8892a6;
}

:deep(.el-tabs__item.is-active) {
  color: #00d4ff;
}
</style>
