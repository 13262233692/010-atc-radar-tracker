import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

export async function fetchAllTracks() {
  const res = await api.get('/tracks')
  return res.data
}

export async function fetchTrack(trackId) {
  const res = await api.get(`/tracks/${trackId}`)
  return res.data
}

export async function fetchStats() {
  const res = await api.get('/tracks/stats')
  return res.data
}

export async function simulateTrack(trackData) {
  const res = await api.post('/tracks/simulate', trackData)
  return res.data
}
