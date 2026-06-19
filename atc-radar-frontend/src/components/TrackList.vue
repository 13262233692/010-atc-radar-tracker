<template>
  <div class="track-list">
    <div class="search-bar">
      <el-input
        v-model="searchQuery"
        size="small"
        placeholder="搜索呼号/航迹号..."
        :prefix-icon="Search"
        clearable
      />
    </div>

    <div class="tracks-container">
      <div
        v-for="track in filteredTracks"
        :key="track.trackId"
        :class="['track-item', { active: track.trackId === selectedTrackId, 'has-alert': isAlertTrack(track.trackId) }]"
        @click="$emit('select-track', track.trackId)"
      >
        <div class="track-header">
          <div class="track-title">
            <el-icon v-if="isAlertTrack(track.trackId)" :size="14" color="#ff4d4d" class="alert-icon">
              <Warning />
            </el-icon>
            <span class="track-callsign" :class="{ 'alert-callsign': isAlertTrack(track.trackId) }">
              {{ track.callsign || 'N/A' }}
            </span>
          </div>
          <span class="track-cat" :class="`cat-${track.category}`">
            CAT{{ track.category }}
          </span>
        </div>
        <div class="track-body">
          <div class="track-info-row">
            <el-icon :size="12"><Location /></el-icon>
            <span class="info-value">{{ formatLatLon(track.latitude, track.longitude) }}</span>
          </div>
          <div class="track-info-row">
            <div class="info-item">
              <el-icon :size="12" color="#4d96ff"><Van /></el-icon>
              <span class="info-value">{{ formatAltitude(track.altitude) }}</span>
            </div>
            <div class="info-item">
              <el-icon :size="12" color="#6bcb77"><Aim /></el-icon>
              <span class="info-value">{{ formatHeading(track.heading) }}</span>
            </div>
          </div>
          <div class="track-info-row">
            <div class="info-item">
              <el-icon :size="12" color="#ffd93d"><Odometer /></el-icon>
              <span class="info-value">{{ formatSpeed(track.groundSpeed) }}</span>
            </div>
            <div class="info-item" v-if="track.verticalRate != null">
              <el-icon :size="12" :color="track.verticalRate >= 0 ? '#00ff88' : '#ff6b6b'">
                <component :is="track.verticalRate >= 0 ? Top : Bottom" />
              </el-icon>
              <span class="info-value">{{ formatVerticalRate(track.verticalRate) }}</span>
            </div>
          </div>
        </div>
        <div class="track-footer">
          <span class="track-id">{{ track.trackId }}</span>
          <span class="track-source">{{ getSourceLabel(track.source) }}</span>
        </div>
      </div>

      <div v-if="filteredTracks.length === 0" class="empty-state">
        <el-empty description="暂无航迹数据" :image-size="80" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Search, Location, Van, Aim, Odometer, Top, Bottom, Warning } from '@element-plus/icons-vue'
import {
  formatAltitude,
  formatSpeed,
  formatHeading,
  formatVerticalRate,
  formatLatLon,
  getSourceLabel
} from '../utils/format'

const props = defineProps({
  tracks: {
    type: Map,
    default: () => new Map()
  },
  selectedTrackId: {
    type: String,
    default: null
  },
  alertTrackIds: {
    type: Set,
    default: () => new Set()
  }
})

defineEmits(['select-track'])

const searchQuery = ref('')

function isAlertTrack(trackId) {
  return props.alertTrackIds && props.alertTrackIds.has(trackId)
}

const filteredTracks = computed(() => {
  const list = Array.from(props.tracks.values())
  let result = list
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = list.filter(t =>
      (t.callsign && t.callsign.toLowerCase().includes(q)) ||
      (t.trackId && t.trackId.toLowerCase().includes(q)) ||
      (t.targetAddress && t.targetAddress.toLowerCase().includes(q))
    )
  }
  return result.sort((a, b) => {
    const aAlert = isAlertTrack(a.trackId) ? 1 : 0
    const bAlert = isAlertTrack(b.trackId) ? 1 : 0
    if (aAlert !== bAlert) return bAlert - aAlert
    return (a.callsign || '').localeCompare(b.callsign || '')
  })
})
</script>

<style scoped>
.track-list {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.search-bar {
  padding: 12px;
  border-bottom: 1px solid #1e2a3f;
  background: #0f1520;
}

.search-bar :deep(.el-input__wrapper) {
  background: #141b2a;
  border: 1px solid #2a3548;
  box-shadow: none;
}

.search-bar :deep(.el-input__wrapper:hover) {
  border-color: #00d4ff;
}

.search-bar :deep(.el-input__inner) {
  color: #e6e6e6;
}

.tracks-container {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.track-item {
  padding: 12px;
  margin-bottom: 8px;
  background: #141b2a;
  border: 1px solid #1e2a3f;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.track-item:hover {
  border-color: #00d4ff;
  background: #1a2336;
}

.track-item.active {
  border-color: #00d4ff;
  background: linear-gradient(135deg, #14233a 0%, #1a2d4a 100%);
  box-shadow: 0 0 0 1px rgba(0, 212, 255, 0.3);
}

.track-item.has-alert {
  border-color: #ff4d4d;
  animation: alert-track-blink 1.5s infinite;
}

@keyframes alert-track-blink {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255, 77, 77, 0.3); }
  50% { box-shadow: 0 0 8px 1px rgba(255, 77, 77, 0.5); }
}

.track-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.track-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.alert-icon {
  animation: icon-shake 0.5s infinite;
}

@keyframes icon-shake {
  0%, 100% { transform: rotate(-5deg); }
  50% { transform: rotate(5deg); }
}

.track-callsign {
  font-size: 15px;
  font-weight: 700;
  color: #00d4ff;
  font-family: Consolas, monospace;
  letter-spacing: 0.5px;
}

.track-callsign.alert-callsign {
  color: #ff4d4d;
}

.track-cat {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: Consolas, monospace;
}

.track-cat.cat-48 {
  background: rgba(77, 150, 255, 0.2);
  color: #4d96ff;
}

.track-cat.cat-62 {
  background: rgba(0, 255, 136, 0.2);
  color: #00ff88;
}

.track-cat:not(.cat-48):not(.cat-62) {
  background: rgba(255, 159, 67, 0.2);
  color: #ff9f43;
}

.track-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.track-info-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
}

.info-value {
  font-size: 12px;
  color: #c0c8d4;
  font-family: Consolas, monospace;
}

.track-footer {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #1e2a3f;
  display: flex;
  justify-content: space-between;
}

.track-id {
  font-size: 10px;
  color: #5a6478;
  font-family: Consolas, monospace;
}

.track-source {
  font-size: 10px;
  color: #8892a6;
}

.empty-state {
  padding: 40px 20px;
}
</style>
