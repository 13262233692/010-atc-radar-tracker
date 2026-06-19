export function createSseClient({ onTrack, onConnect, onDisconnect, onHeartbeat }) {
  let eventSource = null
  let reconnectTimer = null
  let isManualClose = false
  const url = '/api/tracks/stream'

  function connect() {
    isManualClose = false
    if (eventSource) {
      eventSource.close()
    }

    try {
      eventSource = new EventSource(url)

      eventSource.addEventListener('connected', (event) => {
        console.log('[SSE] Connected:', event.data)
        onConnect && onConnect()
      })

      eventSource.addEventListener('track', (event) => {
        try {
          const track = JSON.parse(event.data)
          onTrack && onTrack(track)
        } catch (e) {
          console.error('[SSE] Parse track error:', e)
        }
      })

      eventSource.addEventListener('heartbeat', (event) => {
        onHeartbeat && onHeartbeat(event.data)
      })

      eventSource.onopen = () => {
        console.log('[SSE] Connection opened')
        clearTimeout(reconnectTimer)
      }

      eventSource.onerror = (e) => {
        console.warn('[SSE] Connection error:', e)
        onDisconnect && onDisconnect()
        if (!isManualClose) {
          scheduleReconnect()
        }
      }
    } catch (e) {
      console.error('[SSE] Failed to create EventSource:', e)
      onDisconnect && onDisconnect()
      scheduleReconnect()
    }
  }

  function scheduleReconnect() {
    clearTimeout(reconnectTimer)
    reconnectTimer = setTimeout(() => {
      if (!isManualClose) {
        console.log('[SSE] Reconnecting...')
        connect()
      }
    }, 3000)
  }

  function disconnect() {
    isManualClose = true
    clearTimeout(reconnectTimer)
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  return {
    connect,
    disconnect
  }
}
