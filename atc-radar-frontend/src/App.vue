<template>
  <div class="app-container">
    <header class="app-header">
      <div class="header-left">
        <el-icon :size="28" color="#00d4ff"><Promotion /></el-icon>
        <h1>ATC 空中交通管制雷达监控系统</h1>
      </div>
      <div class="header-right">
        <div class="status-item alert-status" :class="{ 'has-alert': criticalAlertCount > 0 }" @click="activeTab = 'alerts'">
          <el-icon :size="18" :color="criticalAlertCount > 0 ? '#ff4d4d' : '#8892a6'">
            <component :is="criticalAlertCount > 0 ? Warning : InfoFilled" />
          </el-icon>
          <span class="status-label">告警:</span>
          <span class="status-value alert-count" :class="`severity-${highestSeverity}`">
            {{ activeAlertCount }}
          </span>
        </div>
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
          :alert-track-ids="alertTrackIds"
          :highest-severity="highestSeverity"
          @track-selected="handleTrackSelected"
        />

        <div v-if="highestAlert" class="alert-banner" :class="`severity-${highestAlert.severity}`">
          <div class="alert-banner-content">
            <el-icon :size="22" class="alert-icon"><Warning /></el-icon>
            <div class="alert-info">
              <span class="alert-title">{{ getAlertTypeLabel(highestAlert.type) }} - {{ getSeverityLabel(highestAlert.severity) }}</span>
              <span class="alert-detail">
                {{ highestAlert.primaryCallsign || highestAlert.primaryTrackId }} ↔ {{ highestAlert.secondaryCallsign || highestAlert.secondaryTrackId }}
                | TCA: {{ Math.round(highestAlert.predictedTimeToClosestApproachSeconds) }}s
                | H: {{ (highestAlert.predictedHorizontalDistanceMeters / 1852).toFixed(1) }}NM
              </span>
            </div>
            <el-button size="small" type="danger" text @click="activeTab = 'alerts'">查看详情</el-button>
          </div>
        </div>
      </div>

      <aside class="side-panel">
        <el-tabs v-model="activeTab" type="border-card">
          <el-tab-pane label="航迹列表" name="list">
            <TrackList
              :tracks="tracks"
              :selected-track-id="selectedTrackId"
              :alert-track-ids="alertTrackIds"
              @select-track="handleTrackSelected"
            />
          </el-tab-pane>
          <el-tab-pane label="航迹详情" name="detail">
            <TrackDetail :track="selectedTrack" />
          </el-tab-pane>
          <el-tab-pane :label="alertTabLabel" name="alerts">
            <AlertPanel
              :alerts="alerts"
              @acknowledge="handleAcknowledge"
              @resolve="handleResolve"
              @focus-track="handleTrackSelected"
            />
          </el-tab-pane>
          <el-tab-pane label="系统统计" name="stats">
            <StatsPanel :tracks="tracks" :connected="connected" :alerts="alerts" />
          </el-tab-pane>
        </el-tabs>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Promotion, Warning, InfoFilled } from '@element-plus/icons-vue'
import { ElNotification } from 'element-plus'
import RadarMap from './components/RadarMap.vue'
import TrackList from './components/TrackList.vue'
import TrackDetail from './components/TrackDetail.vue'
import AlertPanel from './components/AlertPanel.vue'
import StatsPanel from './components/StatsPanel.vue'
import { createSseClient } from './utils/sseClient'
import { fetchAllTracks } from './utils/api'
import { fetchAllAlerts, acknowledgeAlert, resolveAlert } from './utils/alertApi'

const tracks = ref(new Map())
const alerts = ref(new Map())
const connected = ref(false)
const selectedTrackId = ref(null)
const activeTab = ref('list')
const currentTime = ref('')
const radarMapRef = ref(null)
let sseClient = null
let timeInterval = null
let notificationId = 0

const trackCount = computed(() => tracks.value.size)
const activeAlertCount = computed(() => {
  let count = 0
  alerts.value.forEach(a => { if (a.status === 'ACTIVE') count++ })
  return count
})

const criticalAlertCount = computed(() => {
  let count = 0
  alerts.value.forEach(a => {
    if (a.status === 'ACTIVE' && (a.severity === 'CRITICAL' || a.severity === 'HIGH')) count++
  })
  return count
})

const highestSeverity = computed(() => {
  let highest = null
  const order = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
  alerts.value.forEach(a => {
    if (a.status === 'ACTIVE') {
      const idx = order.indexOf(a.severity)
      if (idx !== -1 && (highest === null || idx < order.indexOf(highest))) {
        highest = a.severity
      }
    }
  })
  return highest || 'NONE'
})

const highestAlert = computed(() => {
  let highest = null
  const order = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
  alerts.value.forEach(a => {
    if (a.status === 'ACTIVE') {
      const idx = order.indexOf(a.severity)
      if (idx !== -1 && (highest === null || idx < order.indexOf(highest.severity))) {
        highest = a
      }
    }
  })
  return highest
})

const alertTrackIds = computed(() => {
  const ids = new Set()
  alerts.value.forEach(a => {
    if (a.status === 'ACTIVE') {
      ids.add(a.primaryTrackId)
      ids.add(a.secondaryTrackId)
    }
  })
  return ids
})

const alertTabLabel = computed(() => {
  const count = activeAlertCount.value
  return count > 0 ? `告警 (${count})` : '告警'
})

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

function handleAlert(alert) {
  const existing = alerts.value.get(alert.alertId)

  if (!existing || existing.status !== alert.status) {
    if (alert.status === 'ACTIVE') {
      showAlertNotification(alert)
    }
  }

  alerts.value.set(alert.alertId, alert)

  if (alert.status !== 'ACTIVE') {
    setTimeout(() => {
      if (alerts.value.get(alert.alertId)?.status !== 'ACTIVE') {
        alerts.value.delete(alert.alertId)
      }
    }, 10000)
  }

  cleanupExpiredAlerts()
}

function showAlertNotification(alert) {
  const typeMap = {
    CRITICAL: { type: 'error', duration: 0 },
    HIGH: { type: 'warning', duration: 8000 },
    MEDIUM: { type: 'warning', duration: 5000 },
    LOW: { type: 'info', duration: 3000 }
  }

  const config = typeMap[alert.severity] || typeMap.LOW
  const title = `${getSeverityLabel(alert.severity)} - ${getAlertTypeLabel(alert.type)}`
  const message = `${alert.primaryCallsign || alert.primaryTrackId} ↔ ${alert.secondaryCallsign || alert.secondaryTrackId}\n预计 ${Math.round(alert.predictedTimeToClosestApproachSeconds)}s 后到达最近点，水平距离 ${(alert.predictedHorizontalDistanceMeters / 1852).toFixed(2)} 海里`

  notificationId++
  ElNotification({
    id: `alert-${alert.alertId}-${notificationId}`,
    title,
    message,
    type: config.type,
    duration: config.duration,
    position: 'top-right',
    onClick: () => {
      activeTab.value = 'alerts'
    }
  })
}

function cleanupExpiredAlerts() {
  const now = Date.now()
  const ttl = 60000
  alerts.value.forEach((alert, id) => {
    if (alert.createdAt) {
      const age = now - new Date(alert.createdAt).getTime()
      if (age > ttl && alert.status !== 'ACTIVE') {
        alerts.value.delete(id)
      }
    }
  })
}

function handleTrackSelected(trackId) {
  selectedTrackId.value = trackId
  activeTab.value = 'detail'
}

async function handleAcknowledge(alertId) {
  try {
    const alert = await acknowledgeAlert(alertId)
    alerts.value.set(alertId, alert)
  } catch (e) {
    console.error('Failed to acknowledge alert:', e)
  }
}

async function handleResolve(alertId) {
  try {
    await resolveAlert(alertId)
    alerts.value.delete(alertId)
  } catch (e) {
    console.error('Failed to resolve alert:', e)
  }
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

function getSeverityLabel(severity) {
  const map = { CRITICAL: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }
  return map[severity] || severity
}

function getAlertTypeLabel(type) {
  const map = {
    TCAS_RESOLUTION_ADVISORY: 'TCAS RA 决断咨询',
    TCAS_TRAFFIC_ADVISORY: 'TCAS TA 交通咨询',
    PROXIMITY_WARNING: '临近警告',
    CONFLICT_PREDICTION: '冲突预测',
    HEADING_CONVERGENCE: '航向汇聚'
  }
  return map[type] || type
}

onMounted(async () => {
  updateCurrentTime()
  timeInterval = setInterval(updateCurrentTime, 1000)
  setInterval(cleanupStaleTracks, 30000)

  try {
    const [initialTracks, initialAlerts] = await Promise.all([
      fetchAllTracks(),
      fetchAllAlerts().catch(() => [])
    ])
    initialTracks.forEach(t => updateTrack(t))
    initialAlerts.forEach(a => alerts.value.set(a.alertId, a))
  } catch (e) {
    console.warn('Failed to fetch initial data:', e)
  }

  sseClient = createSseClient({
    onTrack: (track) => {
      updateTrack(track)
    },
    onAlert: (alert) => {
      handleAlert(alert)
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

.alert-status {
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 6px;
  transition: all 0.2s;
}

.alert-status:hover {
  background: rgba(255, 77, 77, 0.1);
}

.alert-status.has-alert {
  animation: alert-pulse 1.5s infinite;
}

@keyframes alert-pulse {
  0%, 100% { background: transparent; }
  50% { background: rgba(255, 77, 77, 0.15); }
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

.status-value.alert-count {
  font-family: Consolas, monospace;
  font-size: 16px;
  min-width: 24px;
  text-align: center;
}

.status-value.alert-count.severity-CRITICAL {
  color: #ff4d4d;
  animation: critical-blink 0.5s infinite;
}

.status-value.alert-count.severity-HIGH {
  color: #ff994d;
}

.status-value.alert-count.severity-MEDIUM {
  color: #ffcc00;
}

.status-value.alert-count.severity-LOW {
  color: #4d96ff;
}

@keyframes critical-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
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

.alert-banner {
  position: absolute;
  top: 16px;
  right: 16px;
  left: 50%;
  transform: translateX(-50%);
  max-width: 600px;
  z-index: 1001;
  border-radius: 8px;
  border: 1px solid;
  backdrop-filter: blur(10px);
  animation: banner-appear 0.3s ease;
}

@keyframes banner-appear {
  from { opacity: 0; transform: translateX(-50%) translateY(-10px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}

.alert-banner.severity-CRITICAL {
  background: linear-gradient(90deg, rgba(255, 77, 77, 0.25), rgba(255, 77, 77, 0.15));
  border-color: #ff4d4d;
  animation: banner-appear 0.3s ease, critical-banner-blink 0.8s infinite;
}

@keyframes critical-banner-blink {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255, 77, 77, 0.4); }
  50% { box-shadow: 0 0 20px 4px rgba(255, 77, 77, 0.6); }
}

.alert-banner.severity-HIGH {
  background: linear-gradient(90deg, rgba(255, 153, 77, 0.25), rgba(255, 153, 77, 0.15));
  border-color: #ff994d;
}

.alert-banner.severity-MEDIUM {
  background: linear-gradient(90deg, rgba(255, 204, 0, 0.25), rgba(255, 204, 0, 0.15));
  border-color: #ffcc00;
}

.alert-banner-content {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
}

.alert-icon {
  flex-shrink: 0;
}

.severity-CRITICAL .alert-icon,
.severity-HIGH .alert-icon {
  color: #ff4d4d;
  animation: icon-shake 0.5s infinite;
}

@keyframes icon-shake {
  0%, 100% { transform: rotate(-5deg); }
  50% { transform: rotate(5deg); }
}

.severity-MEDIUM .alert-icon { color: #ffcc00; }
.severity-LOW .alert-icon { color: #4d96ff; }

.alert-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.alert-title {
  font-size: 14px;
  font-weight: 700;
  color: #fff;
}

.alert-detail {
  font-size: 12px;
  color: #c0c8d4;
  font-family: Consolas, monospace;
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
