export function formatAltitude(alt) {
  if (alt == null) return '--'
  const fl = Math.round(alt / 30.48 / 100)
  return `FL${fl.toString().padStart(3, '0')} (${Math.round(alt)}m)`
}

export function formatSpeed(speed) {
  if (speed == null) return '--'
  return `${Math.round(speed)} kt`
}

export function formatHeading(heading) {
  if (heading == null) return '--'
  return `${Math.round(heading)}°`
}

export function formatVerticalRate(vr) {
  if (vr == null) return '--'
  const sign = vr >= 0 ? '+' : ''
  return `${sign}${Math.round(vr)} ft/min`
}

export function formatCallsign(callsign) {
  if (!callsign) return 'N/A'
  return callsign
}

export function formatLatLon(lat, lon) {
  if (lat == null || lon == null) return '--'
  const latDir = lat >= 0 ? 'N' : 'S'
  const lonDir = lon >= 0 ? 'E' : 'W'
  return `${Math.abs(lat).toFixed(4)}°${latDir}, ${Math.abs(lon).toFixed(4)}°${lonDir}`
}

export function getSourceLabel(source) {
  const map = {
    PRIMARY_RADAR: '一次雷达',
    SECONDARY_RADAR: '二次雷达',
    MODE_S: 'S模式',
    ADS_B: 'ADS-B',
    MULTILATERATION: '多点定位',
    FUSED: '融合航迹'
  }
  return map[source] || source || '未知'
}

export function getCategoryLabel(cat) {
  const map = {
    48: 'CAT 048 (单雷达)',
    62: 'CAT 062 (融合)'
  }
  return map[cat] || `CAT ${cat}`
}

export function getAltitudeColor(alt) {
  if (alt == null) return '#888'
  if (alt < 3000) return '#ff9f43'
  if (alt < 7000) return '#ffd93d'
  if (alt < 10000) return '#6bcb77'
  return '#4d96ff'
}
