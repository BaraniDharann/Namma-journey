import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'

// Same-origin by default, matching api.js's '/api' fallback: the page's own host proxies
// /ws/tracking through to the backend. Pointing this at the backend's own port instead would
// make the handshake cross-origin, and SockJS would then be rejected by allowed-origins.
const WS_URL = (import.meta.env.VITE_API_BASE_URL?.replace('/api', '') ?? '') + '/ws/tracking'

export function createStompClient(token) {
  const client = new Client({
    webSocketFactory: () => new SockJS(WS_URL),
    connectHeaders: {
      Authorization: `Bearer ${token}`,
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
  })
  return client
}
