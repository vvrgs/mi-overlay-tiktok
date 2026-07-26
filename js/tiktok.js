/* ============================================================
 * TIKTOK — conexión al Events API de TikFinity
 * (ws://localhost:21213/) con reconexión automática.
 * También acepta ?ws=... en la URL para otro servidor.
 *
 * Formato TikFinity: { event: 'gift'|'chat'|'like'|..., data: {...} }
 * ============================================================ */
const TikTok = (() => {
  let ws = null;
  let likeAccum = 0;

  function wsUrl() {
    const p = new URLSearchParams(location.search);
    return p.get('ws') || CONFIG.ws.url;
  }

  function handleGift(data) {
    const name = String(data.giftName || (data.gift && data.gift.name) || '').toLowerCase();
    if (!name) return;
    // en rachas, TikFinity repite el evento: procesa solo el final de racha
    // (o los regalos sin racha, que llegan con repeat_end true / sin flag)
    const inStreak = data.gift && data.gift.repeat_end === false;
    if (inStreak) return;
    const action = CONFIG.giftMap[name];
    if (!action) return;
    const user = data.nickname || data.uniqueId || null;
    const count = data.repeatCount || 1;
    if (action === 'moveLeft' || action === 'moveRight') {
      Disasters.trigger(action, { user, count });
    } else {
      Disasters.trigger(action, { user });
    }
  }

  function handleChat(data) {
    if (!CONFIG.chatCommands.enabled) return;
    const msg = String(data.comment || '').trim().toLowerCase();
    const action = CONFIG.chatCommands.map[msg];
    if (action) Disasters.trigger(action, { user: data.nickname || data.uniqueId });
  }

  function handleLike(data) {
    if (!CONFIG.likes.enabled) return;
    likeAccum += data.likeCount || 1;
    while (likeAccum >= CONFIG.likes.per) {
      likeAccum -= CONFIG.likes.per;
      Disasters.trigger(CONFIG.likes.action, { user: data.nickname });
    }
  }

  function onMessage(ev) {
    let msg;
    try { msg = JSON.parse(ev.data); } catch (e) { return; }
    const event = msg.event || msg.type;
    const data = msg.data || msg;
    if (event === 'gift') handleGift(data);
    else if (event === 'chat' || event === 'comment') handleChat(data);
    else if (event === 'like') handleLike(data);
  }

  function connect() {
    UI.setConn('connecting');
    try {
      ws = new WebSocket(wsUrl());
    } catch (e) {
      UI.setConn('off');
      setTimeout(connect, CONFIG.ws.reconnectMs);
      return;
    }
    ws.onopen = () => UI.setConn('on');
    ws.onmessage = onMessage;
    ws.onclose = () => {
      UI.setConn('off');
      setTimeout(connect, CONFIG.ws.reconnectMs);
    };
    ws.onerror = () => { try { ws.close(); } catch (e) {} };
  }

  return {
    init() { connect(); },
    /* Prueba desde la consola: TikTok.simulateGift('Rose', 'tester') */
    simulateGift(giftName, user, count) {
      handleGift({ giftName, nickname: user || 'tester', repeatCount: count || 1 });
    },
  };
})();
