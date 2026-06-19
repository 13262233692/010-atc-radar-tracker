<template>
  <div class="alert-panel">
    <div v-if="sortedAlerts.length === 0" class="empty-state">
      <el-icon :size="48" color="#4a5568"><CircleCheck /></el-icon>
      <span class="empty-text">当前无活跃告警</span>
    </div>

    <div v-else class="alert-list">
      <div
        v-for="alert in sortedAlerts"
        :key="alert.alertId"
        class="alert-item"
        :class="[`severity-${alert.severity}`, { 'status-active': alert.status === 'ACTIVE' }]"
      >
        <div class="alert-header">
          <div class="alert-type-badge" :class="`severity-${alert.severity}`">
            <el-icon :size="14"><Warning /></el-icon>
            <span>{{ getSeverityLabel(alert.severity) }}</span>
          </div>
          <span class="alert-status" :class="`status-${alert.status.toLowerCase()}`">
            {{ getStatusLabel(alert.status) }}
          </span>
        </div>

        <div class="alert-content">
          <div class="alert-title">
            {{ getAlertTypeLabel(alert.type) }}
          </div>

          <div class="aircraft-pair">
            <div class="aircraft" @click="$emit('focus-track', alert.primaryTrackId)">
              <span class="callsign">{{ alert.primaryCallsign || alert.primaryTrackId }}</span>
              <span class="fl" v-if="alert.primaryAltitude != null">
                FL{{ Math.round(alert.primaryAltitude / 30.48 / 100).toString().padStart(3, '0') }}
              </span>
            </div>
            <el-icon :size="16" color="#ff4d4d"><Switch /></el-icon>
            <div class="aircraft" @click="$emit('focus-track', alert.secondaryTrackId)">
              <span class="callsign">{{ alert.secondaryCallsign || alert.secondaryTrackId }}</span>
              <span class="fl" v-if="alert.secondaryAltitude != null">
                FL{{ Math.round(alert.secondaryAltitude / 30.48 / 100).toString().padStart(3, '0') }}
              </span>
            </div>
          </div>

          <div class="alert-metrics">
            <div class="metric">
              <span class="metric-label">TCA</span>
              <span class="metric-value critical">{{ Math.round(alert.predictedTimeToClosestApproachSeconds) }}s</span>
            </div>
            <div class="metric">
              <span class="metric-label">水平距离</span>
              <span class="metric-value">{{ (alert.predictedHorizontalDistanceMeters / 1852).toFixed(2) }}NM</span>
            </div>
            <div class="metric">
              <span class="metric-label">垂直距离</span>
              <span class="metric-value">{{ Math.round(alert.predictedVerticalDistanceMeters * 3.28084) }}ft</span>
            </div>
          </div>

          <div class="alert-time">
            <el-icon :size="12"><Clock /></el-icon>
            <span>{{ formatTime(alert.createdAt) }}</span>
          </div>
        </div>

        <div class="alert-actions" v-if="alert.status === 'ACTIVE'">
          <el-button size="small" type="warning" @click="$emit('acknowledge', alert.alertId)">
            确认
          </el-button>
          <el-button size="small" type="success" @click="$emit('resolve', alert.alertId)">
            解除
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Warning, Clock, Switch, CircleCheck } from '@element-plus/icons-vue'

const props = defineProps({
  alerts: {
    type: Map,
    default: () => new Map()
  }
})

defineEmits(['acknowledge', 'resolve', 'focus-track'])

const severityOrder = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 }

const sortedAlerts = computed(() => {
  return Array.from(props.alerts.values())
    .filter(a => a.status === 'ACTIVE')
    .sort((a, b) => {
      const severityDiff = severityOrder[a.severity] - severityOrder[b.severity]
      if (severityDiff !== 0) return severityDiff
      return new Date(b.createdAt) - new Date(a.createdAt)
    })
})

function getSeverityLabel(severity) {
  const map = { CRITICAL: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }
  return map[severity] || severity
}

function getStatusLabel(status) {
  const map = { ACTIVE: '活跃', ACKNOWLEDGED: '已确认', RESOLVED: '已解除', EXPIRED: '已过期' }
  return map[status] || status
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

function formatTime(dateStr) {
  if (!dateStr) return '--'
  const date = new Date(dateStr)
  return date.toLocaleTimeString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.alert-panel {
  height: 100%;
  overflow-y: auto;
  padding: 12px;
  background: #0a0e14;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  gap: 12px;
  color: #4a5568;
}

.empty-text {
  font-size: 14px;
}

.alert-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.alert-item {
  background: #141b2a;
  border: 1px solid #1e2a3f;
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.2s;
}

.alert-item.severity-CRITICAL.status-active {
  border-color: #ff4d4d;
  animation: critical-item-blink 1s infinite;
}

@keyframes critical-item-blink {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255, 77, 77, 0.3); }
  50% { box-shadow: 0 0 12px 2px rgba(255, 77, 77, 0.5); }
}

.alert-item.severity-HIGH.status-active {
  border-color: #ff994d;
}

.alert-item.severity-MEDIUM.status-active {
  border-color: #ffcc00;
}

.alert-item.severity-LOW.status-active {
  border-color: #4d96ff;
}

.alert-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: rgba(0, 0, 0, 0.2);
}

.alert-type-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.alert-type-badge.severity-CRITICAL {
  background: rgba(255, 77, 77, 0.2);
  color: #ff4d4d;
}

.alert-type-badge.severity-HIGH {
  background: rgba(255, 153, 77, 0.2);
  color: #ff994d;
}

.alert-type-badge.severity-MEDIUM {
  background: rgba(255, 204, 0, 0.2);
  color: #ffcc00;
}

.alert-type-badge.severity-LOW {
  background: rgba(77, 150, 255, 0.2);
  color: #4d96ff;
}

.alert-status {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
}

.alert-status.status-active {
  background: rgba(255, 77, 77, 0.15);
  color: #ff4d4d;
}

.alert-status.status-acknowledged {
  background: rgba(255, 204, 0, 0.15);
  color: #ffcc00;
}

.alert-status.status-resolved {
  background: rgba(0, 255, 136, 0.15);
  color: #00ff88;
}

.alert-status.status-expired {
  background: rgba(136, 146, 166, 0.15);
  color: #8892a6;
}

.alert-content {
  padding: 12px;
}

.alert-title {
  font-size: 13px;
  font-weight: 600;
  color: #e6e6e6;
  margin-bottom: 10px;
}

.aircraft-pair {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 6px;
}

.aircraft {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}

.aircraft:hover {
  background: rgba(0, 212, 255, 0.1);
}

.aircraft .callsign {
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  font-family: Consolas, monospace;
}

.aircraft .fl {
  font-size: 11px;
  color: #8892a6;
  font-family: Consolas, monospace;
}

.alert-metrics {
  display: flex;
  justify-content: space-around;
  gap: 8px;
  margin-bottom: 8px;
}

.metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.metric-label {
  font-size: 10px;
  color: #6b7280;
  text-transform: uppercase;
}

.metric-value {
  font-size: 13px;
  font-weight: 600;
  color: #e6e6e6;
  font-family: Consolas, monospace;
}

.metric-value.critical {
  color: #ff4d4d;
  animation: value-blink 0.5s infinite;
}

@keyframes value-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.alert-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #6b7280;
}

.alert-actions {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #1e2a3f;
  background: rgba(0, 0, 0, 0.1);
}

.alert-actions .el-button {
  flex: 1;
}
</style>
