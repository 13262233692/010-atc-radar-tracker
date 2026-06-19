<template>
  <div class="track-detail" v-if="track">
    <div class="detail-header">
      <div class="callsign-block">
        <span class="callsign">{{ track.callsign || '未知呼号' }}</span>
        <span class="track-badge" :class="`cat-${track.category}`">
          {{ getCategoryLabel(track.category) }}
        </span>
      </div>
      <div class="status-block">
        <span :class="['status-dot', `status-${track.status?.toLowerCase() || 'active'}`]"></span>
        <span class="status-text">{{ getStatusLabel(track.status) }}</span>
      </div>
    </div>

    <el-divider />

    <div class="detail-section">
      <h4 class="section-title"><el-icon><Location /></el-icon> 位置信息</h4>
      <div class="info-grid">
        <div class="info-card">
          <span class="info-label">纬度</span>
          <span class="info-value highlight">{{ track.latitude?.toFixed(6) }}°</span>
        </div>
        <div class="info-card">
          <span class="info-label">经度</span>
          <span class="info-value highlight">{{ track.longitude?.toFixed(6) }}°</span>
        </div>
        <div class="info-card">
          <span class="info-label">高度</span>
          <span class="info-value alt">{{ formatAltitude(track.altitude) }}</span>
        </div>
        <div class="info-card">
          <span class="info-label">航向</span>
          <span class="info-value">{{ formatHeading(track.heading) }}</span>
        </div>
      </div>
    </div>

    <div class="detail-section">
      <h4 class="section-title"><el-icon><Van /></el-icon> 运动状态</h4>
      <div class="info-grid">
        <div class="info-card">
          <span class="info-label">地速</span>
          <span class="info-value">{{ formatSpeed(track.groundSpeed) }}</span>
        </div>
        <div class="info-card">
          <span class="info-label">升降率</span>
          <span :class="['info-value', track.verticalRate >= 0 ? 'climb' : 'descend']">
            {{ formatVerticalRate(track.verticalRate) }}
          </span>
        </div>
        <div class="info-card" v-if="track.targetAddress">
          <span class="info-label">地址码</span>
          <span class="info-value mono">0x{{ track.targetAddress }}</span>
        </div>
        <div class="info-card" v-if="track.trackNumber != null">
          <span class="info-label">航迹编号</span>
          <span class="info-value mono">#{{ track.trackNumber }}</span>
        </div>
      </div>
    </div>

    <div class="detail-section">
      <h4 class="section-title"><el-icon><Setting /></el-icon> 系统信息</h4>
      <div class="info-list">
        <div class="list-item">
          <span class="item-label">数据来源</span>
          <span class="item-value">{{ getSourceLabel(track.source) }}</span>
        </div>
        <div class="list-item">
          <span class="item-label">航迹ID</span>
          <span class="item-value mono">{{ track.trackId }}</span>
        </div>
        <div class="list-item">
          <span class="item-label">时间戳</span>
          <span class="item-value">{{ formatTime(track.timestamp) }}</span>
        </div>
        <div class="list-item">
          <span class="item-label">最后更新</span>
          <span class="item-value">{{ formatTime(track.lastUpdate) }}</span>
        </div>
      </div>
    </div>
  </div>

  <div v-else class="empty-detail">
    <el-empty description="请选择一架飞行器查看详情" :image-size="100" />
  </div>
</template>

<script setup>
import { Location, Van, Setting } from '@element-plus/icons-vue'
import {
  formatAltitude,
  formatSpeed,
  formatHeading,
  formatVerticalRate,
  getSourceLabel,
  getCategoryLabel
} from '../utils/format'

defineProps({
  track: {
    type: Object,
    default: null
  }
})

function getStatusLabel(status) {
  const map = {
    ACTIVE: '活动',
    COASTING: '滑行',
    DROPPED: '已消失',
    PREDICTED: '预测'
  }
  return map[status] || '未知'
}

function formatTime(t) {
  if (!t) return '--'
  return new Date(t).toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.track-detail {
  padding: 16px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.callsign-block {
  display: flex;
  align-items: center;
  gap: 10px;
}

.callsign {
  font-size: 24px;
  font-weight: 700;
  color: #00d4ff;
  font-family: Consolas, monospace;
  letter-spacing: 1px;
}

.track-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 4px;
}

.track-badge.cat-48 {
  background: rgba(77, 150, 255, 0.2);
  color: #4d96ff;
  border: 1px solid rgba(77, 150, 255, 0.4);
}

.track-badge.cat-62 {
  background: rgba(0, 255, 136, 0.2);
  color: #00ff88;
  border: 1px solid rgba(0, 255, 136, 0.4);
}

.track-badge:not(.cat-48):not(.cat-62) {
  background: rgba(255, 159, 67, 0.2);
  color: #ff9f43;
  border: 1px solid rgba(255, 159, 67, 0.4);
}

.status-block {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.status-dot.status-active {
  background: #00ff88;
  box-shadow: 0 0 6px #00ff88;
}

.status-dot.status-coasting {
  background: #ffd93d;
  box-shadow: 0 0 6px #ffd93d;
}

.status-dot.status-dropped {
  background: #ff6b6b;
}

.status-text {
  font-size: 13px;
  color: #c0c8d4;
}

:deep(.el-divider) {
  border-color: #1e2a3f;
  margin: 12px 0;
}

.detail-section {
  margin-bottom: 20px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #8892a6;
  margin-bottom: 10px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.info-card {
  padding: 10px 12px;
  background: #141b2a;
  border: 1px solid #1e2a3f;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 11px;
  color: #5a6478;
  text-transform: uppercase;
}

.info-value {
  font-size: 14px;
  font-weight: 600;
  color: #e6e6e6;
  font-family: Consolas, monospace;
}

.info-value.highlight {
  color: #00d4ff;
}

.info-value.alt {
  color: #4d96ff;
}

.info-value.climb {
  color: #00ff88;
}

.info-value.descend {
  color: #ff6b6b;
}

.info-value.mono {
  font-family: Consolas, monospace;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.list-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #141b2a;
  border: 1px solid #1e2a3f;
  border-radius: 4px;
}

.item-label {
  font-size: 12px;
  color: #8892a6;
}

.item-value {
  font-size: 13px;
  color: #e6e6e6;
}

.item-value.mono {
  font-family: Consolas, monospace;
  color: #00d4ff;
}

.empty-detail {
  padding: 60px 20px;
}
</style>
