import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

export async function fetchAllAlerts() {
  const res = await api.get('/alerts')
  return res.data
}

export async function acknowledgeAlert(alertId) {
  const res = await api.post(`/alerts/${alertId}/acknowledge`)
  return res.data
}

export async function resolveAlert(alertId) {
  const res = await api.post(`/alerts/${alertId}/resolve`)
  return res.data
}

export async function triggerAlertScan() {
  const res = await api.post('/alerts/scan')
  return res.data
}

export async function fetchAlertStats() {
  const res = await api.get('/alerts/stats')
  return res.data
}
