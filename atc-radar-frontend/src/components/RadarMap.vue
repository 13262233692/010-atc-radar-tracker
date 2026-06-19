<template>
  <div class="radar-map-wrapper">
    <div ref="mapContainer" class="map-container-inner"></div>

    <div class="map-controls">
      <div class="control-group">
        <el-button size="small" @click="zoomIn">
          <el-icon><ZoomIn /></el-icon>
        </el-button>
        <el-button size="small" @click="zoomOut">
          <el-icon><ZoomOut /></el-icon>
        </el-button>
        <el-button size="small" @click="resetView">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
      <div class="control-group">
        <el-switch
          v-model="showHistory"
          size="small"
          active-text="轨迹"
          inactive-text="轨迹"
        />
      </div>
      <div class="control-group">
        <el-switch
          v-model="showLabels"
          size="small"
          active-text="标签"
          inactive-text="标签"
        />
      </div>
    </div>

    <div class="map-scale">
      <span class="scale-label">中心:</span>
      <span class="scale-value">{{ centerCoord }}</span>
    </div>
  </div>
</template>

<script setup>import { ref, onMounted, onUnmounted, watch, computed } from 'vue';
import { ZoomIn, ZoomOut, Refresh } from '@element-plus/icons-vue';
import Map from 'ol/Map';
import View from 'ol/View';
import TileLayer from 'ol/layer/Tile';
import VectorLayer from 'ol/layer/Vector';
import OSM from 'ol/source/OSM';
import VectorSource from 'ol/source/Vector';
import { fromLonLat, toLonLat } from 'ol/proj';
import Feature from 'ol/Feature';
import Point from 'ol/geom/Point';
import LineString from 'ol/geom/LineString';
import { Style, Icon, Stroke, Text, Fill, Circle as CircleStyle } from 'ol/style';
import { getAltitudeColor } from '../utils/format';
const props = defineProps({
  tracks: {
    type: Map,
    default: () => new Map()
  },
  alertTrackIds: {
    type: Set,
    default: () => new Set()
  },
  highestSeverity: {
    type: String,
    default: 'NONE'
  }
});
const emit = defineEmits(['track-selected']);
const mapContainer = ref(null);
const showHistory = ref(true);
const showLabels = ref(true);
let map = null;
let trackLayer = null;
let historyLayer = null;
let trackSource = null;
let historySource = null;
const trackFeatures = new Map();
const historyFeatures = new Map();
const alertPulseFeatures = new Map();
const initialCenter = fromLonLat([116.4074, 39.9042]);
const initialZoom = 6;
let alertPulseSource = null;
let alertPulseLayer = null;
let animationFrameId = null;
let pulsePhase = 0;
const centerCoord = computed(() => {
 if (!map)
 return '--';
 const view = map.getView();
 const center = view.getCenter();
 if (!center)
 return '--';
 const [lon, lat] = toLonLat(center);
 return `${lat.toFixed(3)}°N, ${lon.toFixed(3)}°E`;
});
function getAlertSeverityForTrack(trackId) {
  if (!props.alertTrackIds || !props.alertTrackIds.has(trackId)) return null;
  return props.highestSeverity || 'HIGH';
}

function getAlertColors(severity) {
  const colorMap = {
    'CRITICAL': { primary: '#ff4d4d', secondary: '#ff1a1a', glow: 'rgba(255, 77, 77, 0.8)' },
    'HIGH': { primary: '#ff994d', secondary: '#ff7700', glow: 'rgba(255, 153, 77, 0.7)' },
    'MEDIUM': { primary: '#ffcc00', secondary: '#cc9900', glow: 'rgba(255, 204, 0, 0.6)' },
    'LOW': { primary: '#4d96ff', secondary: '#0066ff', glow: 'rgba(77, 150, 255, 0.5)' }
  };
  return colorMap[severity] || colorMap.HIGH;
}

function createAlertAircraftIcon(baseColor, alertColors) {
  const canvas = document.createElement('canvas');
  canvas.width = 48;
  canvas.height = 48;
  const ctx = canvas.getContext('2d');
  const cx = 24;
  const cy = 24;
  
  ctx.save();
  
  const gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, 22);
  gradient.addColorStop(0, alertColors.glow);
  gradient.addColorStop(1, 'transparent');
  ctx.fillStyle = gradient;
  ctx.beginPath();
  ctx.arc(cx, cy, 22, 0, Math.PI * 2);
  ctx.fill();
  
  ctx.strokeStyle = alertColors.primary;
  ctx.lineWidth = 2.5;
  ctx.fillStyle = alertColors.primary;
  ctx.beginPath();
  ctx.moveTo(cx, cy - 14);
  ctx.lineTo(cx + 4, cy + 5);
  ctx.lineTo(cx + 12, cy + 12);
  ctx.lineTo(cx + 3, cy + 5);
  ctx.lineTo(cx, cy + 10);
  ctx.lineTo(cx - 3, cy + 5);
  ctx.lineTo(cx - 12, cy + 12);
  ctx.lineTo(cx - 4, cy + 5);
  ctx.closePath();
  ctx.fill();
  ctx.stroke();
  
  ctx.beginPath();
  ctx.arc(cx, cy, 16, 0, Math.PI * 2);
  ctx.strokeStyle = `${alertColors.primary}99`;
  ctx.lineWidth = 1.5;
  ctx.stroke();
  
  ctx.restore();
  return canvas.toDataURL();
}

function createAlertPulseStyle(track, phase) {
  const severity = getAlertSeverityForTrack(track.trackId);
  if (!severity) return null;
  
  const colors = getAlertColors(severity);
  const pulseRadius = 15 + phase * 35;
  const opacity = Math.max(0, 0.8 - phase * 0.8);
  
  return new Style({
    image: new CircleStyle({
      radius: pulseRadius,
      stroke: new Stroke({
        color: colors.primary,
        width: 3,
        opacity: opacity
      }),
      fill: new Fill({
        color: `rgba(255, 77, 77, ${opacity * 0.2})`
      })
    }),
    zIndex: 8
  });
}

function createAircraftStyle(track) {
  const severity = getAlertSeverityForTrack(track.trackId);
  const alertColors = severity ? getAlertColors(severity) : null;
  const color = alertColors ? alertColors.primary : getAltitudeColor(track.altitude);
  const heading = track.heading || 0;
  const styles = [];
  
  const iconSrc = alertColors 
    ? createAlertAircraftIcon(color, alertColors)
    : createAircraftIcon(color);
  
  styles.push(new Style({
    image: new Icon({
      src: iconSrc,
      scale: 1,
      rotation: (heading * Math.PI) / 180,
      rotateWithView: false,
      anchor: [0.5, 0.5],
      crossOrigin: 'anonymous'
    }),
    zIndex: 10
  }));
  
  if (track.groundSpeed != null && track.heading != null) {
    const headingLength = Math.min(track.groundSpeed * 50, 150000);
    const rad = (track.heading * Math.PI) / 180;
    const start = fromLonLat([track.longitude, track.latitude]);
    const end = [
      start[0] + Math.sin(rad) * headingLength,
      start[1] + Math.cos(rad) * headingLength
    ];
    styles.push(new Style({
      geometry: new LineString([start, end]),
      stroke: new Stroke({
        color: color,
        width: 2,
        lineDash: [8, 4]
      }),
      zIndex: 5
    }));
  }
  
  if (props.showLabels && showLabels.value && (track.callsign || track.trackId)) {
    const label = track.callsign || track.trackId;
    styles.push(new Style({
      text: new Text({
        text: label,
        font: 'bold 12px Consolas, monospace',
        fill: new Fill({ color: '#ffffff' }),
        stroke: new Stroke({ color: '#000000', width: 2 }),
        offsetY: -28,
        textAlign: 'center',
        textBaseline: 'middle'
      }),
      zIndex: 15
    }));
    if (track.altitude != null) {
      const fl = Math.round(track.altitude / 30.48 / 100);
      styles.push(new Style({
        text: new Text({
          text: `FL${fl.toString().padStart(3, '0')}`,
          font: '11px Consolas, monospace',
          fill: new Fill({ color: color }),
          stroke: new Stroke({ color: '#000000', width: 2 }),
          offsetY: 24,
          offsetX: 0,
          textAlign: 'center',
          textBaseline: 'middle'
        }),
        zIndex: 15
      }));
    }
  }
  return styles;
}
function createAircraftIcon(color) {
 const canvas = document.createElement('canvas');
 canvas.width = 40;
 canvas.height = 40;
 const ctx = canvas.getContext('2d');
 const cx = 20;
 const cy = 20;
 ctx.save();
 ctx.strokeStyle = color;
 ctx.lineWidth = 2;
 ctx.fillStyle = color;
 ctx.beginPath();
 ctx.moveTo(cx, cy - 12);
 ctx.lineTo(cx + 3, cy + 4);
 ctx.lineTo(cx + 10, cy + 10);
 ctx.lineTo(cx + 2, cy + 4);
 ctx.lineTo(cx, cy + 8);
 ctx.lineTo(cx - 2, cy + 4);
 ctx.lineTo(cx - 10, cy + 10);
 ctx.lineTo(cx - 3, cy + 4);
 ctx.closePath();
 ctx.fill();
 ctx.stroke();
 ctx.beginPath();
 ctx.arc(cx, cy, 14, 0, Math.PI * 2);
 ctx.strokeStyle = `${color}66`;
 ctx.lineWidth = 1;
 ctx.stroke();
 ctx.restore();
 return canvas.toDataURL();
}
function createHistoryStyle(track) {
 const color = getAltitudeColor(track.altitude);
 return new Style({
 stroke: new Stroke({
 color: `${color}88`,
 width: 2
 }),
 zIndex: 3
 });
}
function getPulseSpeed() {
  const speedMap = {
    'CRITICAL': 2.0,
    'HIGH': 1.0,
    'MEDIUM': 0.5,
    'LOW': 0.25
  };
  return speedMap[props.highestSeverity] || speedMap.HIGH;
}

function updateAlertPulseAnimation() {
  if (!alertPulseSource) return;
  
  pulsePhase += 0.016 * getPulseSpeed();
  if (pulsePhase > 1) pulsePhase = 0;
  
  alertPulseFeatures.forEach((feature, trackId) => {
    const track = props.tracks.get(trackId);
    if (track && props.alertTrackIds && props.alertTrackIds.has(trackId)) {
      const style = createAlertPulseStyle(track, pulsePhase);
      if (style) {
        feature.setStyle(style);
      }
    }
  });
  
  animationFrameId = requestAnimationFrame(updateAlertPulseAnimation);
}

function initMap() {
  trackSource = new VectorSource();
  historySource = new VectorSource();
  alertPulseSource = new VectorSource();
  
  trackLayer = new VectorLayer({
    source: trackSource,
    zIndex: 100
  });
  historyLayer = new VectorLayer({
    source: historySource,
    zIndex: 50
  });
  alertPulseLayer = new VectorLayer({
    source: alertPulseSource,
    zIndex: 90
  });
  
  map = new Map({
    target: mapContainer.value,
    layers: [
      new TileLayer({
        source: new OSM({
          url: 'https://webrd0{1-4}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}'
        }),
        zIndex: 1
      }),
      historyLayer,
      alertPulseLayer,
      trackLayer
    ],
    view: new View({
      center: initialCenter,
      zoom: initialZoom,
      minZoom: 3,
      maxZoom: 14
    })
  });
  
  map.on('singleclick', (evt) => {
    const features = map.getFeaturesAtPixel(evt.pixel);
    if (features && features.length > 0) {
      const feature = features[0];
      const trackId = feature.get('trackId');
      if (trackId) {
        emit('track-selected', trackId);
      }
    }
  });
  
  updateAlertPulseAnimation();
}
function updateAlertPulseFeature(track) {
  if (!alertPulseSource) return;
  
  const isAlert = props.alertTrackIds && props.alertTrackIds.has(track.trackId);
  
  if (isAlert) {
    let feature = alertPulseFeatures.get(track.trackId);
    if (!feature) {
      feature = new Feature({});
      feature.set('trackId', track.trackId);
      alertPulseFeatures.set(track.trackId, feature);
      alertPulseSource.addFeature(feature);
    }
    feature.setGeometry(new Point(fromLonLat([track.longitude, track.latitude])));
    const style = createAlertPulseStyle(track, pulsePhase);
    if (style) {
      feature.setStyle(style);
    }
  } else {
    if (alertPulseFeatures.has(track.trackId)) {
      alertPulseSource.removeFeature(alertPulseFeatures.get(track.trackId));
      alertPulseFeatures.delete(track.trackId);
    }
  }
}

function updateTrackFeature(track) {
  if (!trackSource) return;
  
  let feature = trackFeatures.get(track.trackId);
  if (!feature) {
    feature = new Feature({});
    feature.set('trackId', track.trackId);
    trackFeatures.set(track.trackId, feature);
    trackSource.addFeature(feature);
  }
  feature.setGeometry(new Point(fromLonLat([track.longitude, track.latitude])));
  feature.setStyle(createAircraftStyle(track));
  
  updateAlertPulseFeature(track);
}
function updateHistoryFeature(track) {
 if (!historySource || !showHistory.value || !track.history || track.history.length < 2) {
 if (historyFeatures.has(track.trackId)) {
 historySource.removeFeature(historyFeatures.get(track.trackId));
 historyFeatures.delete(track.trackId);
 }
 return;
 }
 const coords = track.history.map(p => fromLonLat([p.longitude, p.latitude]));
 let feature = historyFeatures.get(track.trackId);
 if (!feature) {
 feature = new Feature({});
 feature.set('trackId', track.trackId);
 historyFeatures.set(track.trackId, feature);
 historySource.addFeature(feature);
 }
 feature.setGeometry(new LineString(coords));
 feature.setStyle(createHistoryStyle(track));
}
function removeTrackFeature(trackId) {
  if (trackFeatures.has(trackId)) {
    trackSource.removeFeature(trackFeatures.get(trackId));
    trackFeatures.delete(trackId);
  }
  if (historyFeatures.has(trackId)) {
    historySource.removeFeature(historyFeatures.get(trackId));
    historyFeatures.delete(trackId);
  }
  if (alertPulseFeatures.has(trackId)) {
    alertPulseSource.removeFeature(alertPulseFeatures.get(trackId));
    alertPulseFeatures.delete(trackId);
  }
}
function zoomIn() {
 const view = map.getView();
 view.animate({
 zoom: view.getZoom() + 1,
 duration: 300
 });
}
function zoomOut() {
 const view = map.getView();
 view.animate({
 zoom: view.getZoom() - 1,
 duration: 300
 });
}
function resetView() {
 const view = map.getView();
 view.animate({
 center: initialCenter,
 zoom: initialZoom,
 duration: 500
 });
}
watch(() => props.tracks, (newTracks) => {
 const seenIds = new Set();
 newTracks.forEach((track) => {
 seenIds.add(track.trackId);
 updateTrackFeature(track);
 updateHistoryFeature(track);
 });
 trackFeatures.forEach((_, id) => {
 if (!seenIds.has(id)) {
 removeTrackFeature(id);
 }
 });
}, { deep: true });
watch(showHistory, () => {
 props.tracks.forEach(track => updateHistoryFeature(track));
});
watch(showLabels, () => {
  props.tracks.forEach(track => updateTrackFeature(track));
});

watch(() => props.alertTrackIds, () => {
  props.tracks.forEach(track => {
    updateTrackFeature(track);
  });
}, { deep: true });

watch(() => props.highestSeverity, () => {
  props.tracks.forEach(track => {
    updateTrackFeature(track);
  });
});

onMounted(() => {
  initMap();
});

onUnmounted(() => {
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId);
    animationFrameId = null;
  }
  if (map) {
    map.dispose();
    map = null;
  }
});
</script>

<style scoped>
.radar-map-wrapper {
  width: 100%;
  height: 100%;
  position: relative;
  background: #0a0e14;
}

.map-container-inner {
  width: 100%;
  height: 100%;
}

.map-controls {
  position: absolute;
  top: 16px;
  left: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  z-index: 1000;
}

.control-group {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(15, 21, 32, 0.9);
  border: 1px solid #1e2a3f;
  border-radius: 6px;
  backdrop-filter: blur(8px);
}

.control-group :deep(.el-button) {
  background: #141b2a;
  border-color: #2a3548;
  color: #8892a6;
}

.control-group :deep(.el-button:hover) {
  background: #1a2336;
  border-color: #00d4ff;
  color: #00d4ff;
}

.map-scale {
  position: absolute;
  bottom: 16px;
  left: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: rgba(15, 21, 32, 0.9);
  border: 1px solid #1e2a3f;
  border-radius: 4px;
  backdrop-filter: blur(8px);
  z-index: 1000;
}

.scale-label {
  font-size: 12px;
  color: #8892a6;
}

.scale-value {
  font-size: 12px;
  color: #00d4ff;
  font-family: Consolas, monospace;
}

:deep(.ol-control) {
  display: none;
}
</style>
