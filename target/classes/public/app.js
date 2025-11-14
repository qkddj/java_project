const wsScheme = location.protocol === 'https:' ? 'wss' : 'ws';
const ws = new WebSocket(`${wsScheme}://${location.host}/ws`);
let userId, peerId, roomId, pc, localStream;
let stayMatching = false;
let remoteReadyWatchdog = null;
let pendingRemoteCandidates = [];
let micSender = null, camSender = null;

// 같은 브라우저의 다른 창/탭과 통신하기 위한 BroadcastChannel
const broadcastChannel = typeof BroadcastChannel !== 'undefined' 
  ? new BroadcastChannel('video-call-channel') 
  : null;

// 다른 창에서 닫기 메시지를 받으면 창 닫기
if (broadcastChannel) {
  broadcastChannel.onmessage = (event) => {
    if (event.data === 'close-window') {
      closeWindowAndCleanup();
    }
  };
}

const $ = (id) => document.getElementById(id);
function showRemoteWaiting(show) {
  const ph = document.getElementById('remotePlaceholder');
  if (!ph) return;
  ph.style.display = show ? '' : 'none';
}
function alertOnTrackEvents(track, label) {
  if (!track) return;
  track.addEventListener('ended', () => {
    alert(`${label} 장치 연결이 종료되었습니다.`);
  });
  track.addEventListener('mute', () => {
    alert(`${label}가 꺼졌습니다.`);
  });
  track.addEventListener('unmute', () => {
    alert(`${label}가 켜졌습니다.`);
  });
}
function attachLocalTrackAlerts(stream) {
  try {
    stream.getAudioTracks().forEach(t => alertOnTrackEvents(t, '마이크'));
    stream.getVideoTracks().forEach(t => alertOnTrackEvents(t, '카메라'));
  } catch (_) { }
}
function hideOverlayIfVideoLive() {
  const rv = document.getElementById('remoteVideo');
  if (!rv) return;
  const stream = rv.srcObject;
  if (stream && stream.getVideoTracks && stream.getVideoTracks().some(t => t.readyState === 'live')) {
    showRemoteWaiting(false);
  }
}
function startRemoteReadyWatchdog() {
  const rv = document.getElementById('remoteVideo');
  if (!rv) return;
  if (remoteReadyWatchdog) { clearInterval(remoteReadyWatchdog); remoteReadyWatchdog = null; }
  let attempts = 0;
  remoteReadyWatchdog = setInterval(() => {
    attempts++;
    if ((rv.videoWidth && rv.videoHeight) || (rv.srcObject && rv.srcObject.getVideoTracks && rv.srcObject.getVideoTracks().some(t => t.readyState === 'live'))) {
      showRemoteWaiting(false);
      clearInterval(remoteReadyWatchdog);
      remoteReadyWatchdog = null;
    } else if (attempts > 40) {
      clearInterval(remoteReadyWatchdog);
      remoteReadyWatchdog = null;
    }
  }, 300);
}
const statusEl = $('status');

function setStatus(text) { statusEl.textContent = text; }

function showHangupButton(show) {
  const btnHangup = $('btnHangup');
  if (btnHangup) {
    btnHangup.classList.toggle('hidden', !show);
  }
}

ws.addEventListener('open', () => setStatus('서버 연결됨'));
ws.addEventListener('message', async (ev) => {
  const msg = JSON.parse(ev.data);
  switch (msg.type) {
    case 'hello':
      userId = msg.userId;
      break;
    case 'enqueued':
      if (typeof msg.queueSize === 'number') {
        setStatus(`대기중 (${msg.queueSize}명 대기)`);
      } else {
        setStatus(`대기중 (앞선 ${Math.max(0, (msg.position || 1) - 1)}명)`);
      }
      showHangupButton(true);
      break;
    case 'queueUpdate':
      if (typeof msg.queueSize === 'number') {
        setStatus(`대기중 (${msg.queueSize}명 대기)`);
      } else {
        setStatus(`대기중 (앞선 ${msg.ahead || 0}명)`);
      }
      showHangupButton(true);
      break;
    case 'dequeued':
      setStatus('대기 종료');
      showHangupButton(false);
      break;
    case 'matched':
      roomId = msg.roomId;
      peerId = msg.peerId;
      setStatus(`매칭됨 (room ${roomId.substring(0, 8)})`);
      showRemoteWaiting(true);
      startRemoteReadyWatchdog();

      const iAmCaller = (typeof userId === 'string' && typeof peerId === 'string')
        ? (userId.localeCompare(peerId) < 0)
        : true;
      await startWebRTC(iAmCaller);
      break;
    case 'rtc.offer':
      await ensurePc();
      try {
        if (pc.signalingState === 'have-local-offer') {
          await pc.setLocalDescription({ type: 'rollback' });
        }
        await pc.setRemoteDescription(new RTCSessionDescription(msg.data));
        if (pendingRemoteCandidates.length) {
          for (const c of pendingRemoteCandidates) { try { await pc.addIceCandidate(c); } catch (_) { } }
          pendingRemoteCandidates = [];
        }
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        wsSend({ type: 'rtc.answer', roomId, data: pc.localDescription });
        startRemoteReadyWatchdog();
      } catch (e) {
        console.warn('rtc.offer handling failed:', e, 'state=', pc.signalingState);
      }
      break;
    case 'rtc.answer':
      if (!pc) break;
      if (pc.signalingState !== 'have-local-offer') {
        console.warn('Skip unexpected answer in state', pc.signalingState);
        break;
      }
      try {
        await pc.setRemoteDescription(new RTCSessionDescription(msg.data));
        if (pendingRemoteCandidates.length) {
          for (const c of pendingRemoteCandidates) { try { await pc.addIceCandidate(c); } catch (_) { } }
          pendingRemoteCandidates = [];
        }
        startRemoteReadyWatchdog();
      } catch (e) {
        console.warn('rtc.answer setRemoteDescription error:', e, 'state=', pc.signalingState);
      }
      break;
    case 'rtc.ice':
      if (msg.data) {
        if (pc && pc.remoteDescription) {
          try { await pc.addIceCandidate(msg.data); } catch (e) { console.warn('addIceCandidate error', e); }
        } else {
          pendingRemoteCandidates.push(msg.data);
        }
      }
      break;
    case 'callEnded':
      teardown('상대 종료', stayMatching);
      showRemoteWaiting(true);
      if (stayMatching) {
        setStatus('다음 상대 대기중');
        wsSend({ type: 'joinQueue' });
        showHangupButton(true);
      } else {
        showHangupButton(false);
      }
      break;
  }
});

function wsSend(o) { ws.readyState === 1 && ws.send(JSON.stringify(o)); }

async function getMedia() {
  if (localStream) {
    const hasLive = localStream.getTracks().some(t => t.readyState === 'live');
    if (hasLive) return localStream;
    localStream.getTracks().forEach(t => t.stop());
    localStream = null;
  }
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    const hint = !isSecureContext && location.hostname !== 'localhost'
      ? 'HTTPS가 아닌 주소입니다. https 주소(예: ngrok URL)로 접속해야 합니다.'
      : '브라우저가 getUserMedia를 지원하지 않거나 정책에 의해 비활성화되었습니다.';
    throw new Error('mediaDevices_unavailable: ' + hint);
  }
  localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
  $('localVideo').srcObject = localStream;
  micSender = null;
  camSender = null;
  attachLocalTrackAlerts(localStream);
  return localStream;
}

async function ensurePc() {
  if (pc) return pc;
  const params = new URLSearchParams(location.search);
  const iceServers = [{ urls: ['stun:stun.l.google.com:19302'] }];
  const turnUrls = params.get('turn');
  const turnUser = params.get('tu');
  const turnPass = params.get('tp');
  if (turnUrls) {
    const urls = turnUrls.split(',').map(u => u.trim()).filter(Boolean);
    iceServers.push({ urls, username: turnUser || undefined, credential: turnPass || undefined });
    try {
      localStorage.setItem('turnUrls', turnUrls);
      localStorage.setItem('turnUser', turnUser || '');
      localStorage.setItem('turnPass', turnPass || '');
    } catch (_) { }
  } else {
    try {
      const savedUrls = localStorage.getItem('turnUrls');
      if (savedUrls) {
        const savedUser = localStorage.getItem('turnUser') || undefined;
        const savedPass = localStorage.getItem('turnPass') || undefined;
        const urls = savedUrls.split(',').map(u => u.trim()).filter(Boolean);
        iceServers.push({ urls, username: savedUser, credential: savedPass });
      }
    } catch (_) { }
  }
  pc = new RTCPeerConnection({ iceServers });
  pc.ontrack = (e) => {
    const rv = $('remoteVideo');
    rv.srcObject = e.streams[0];
    if (rv.muted !== true) rv.muted = true;
    if (!rv.hasAttribute('playsinline')) rv.setAttribute('playsinline', '');
    rv.addEventListener('click', () => { try { rv.muted = false; rv.play().catch(() => { }); } catch (_) { } }, { once: true });
    setTimeout(() => { rv.play().catch(() => { }); hideOverlayIfVideoLive(); }, 0);
  };
  pc.oniceconnectionstatechange = () => {
    const state = pc.iceConnectionState;
    if (state === 'connected' || state === 'completed') hideOverlayIfVideoLive();
    if (state === 'disconnected' || state === 'failed' || state === 'closed') showRemoteWaiting(true);
  };
  pc.onconnectionstatechange = () => {
    const state = pc.connectionState;
    if (state === 'connected') hideOverlayIfVideoLive();
    if (state === 'disconnected' || state === 'failed' || state === 'closed') showRemoteWaiting(true);
  };
  pc.onicecandidate = (e) => e.candidate && wsSend({ type: 'rtc.ice', roomId, data: e.candidate });
  const stream = await getMedia();
  stream.getAudioTracks().forEach(t => { micSender = pc.addTrack(t, stream); });
  stream.getVideoTracks().forEach(t => { camSender = pc.addTrack(t, stream); });
  return pc;
}

async function startWebRTC(isCaller) {
  await ensurePc();
  if (isCaller) {
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    wsSend({ type: 'rtc.offer', roomId, data: pc.localDescription });
  }
  try {
    const hasTurn = !!(new URLSearchParams(location.search).get('turn') || localStorage.getItem('turnUrls'));
    setTimeout(() => {
      if (!pc) return;
      const s = pc.iceConnectionState;
      if ((s !== 'connected' && s !== 'completed') && !hasTurn) {
        alert('다른 와이파이/모바일망 간 연결에는 TURN 서버가 필요할 수 있습니다.\n예: https://YOUR_HTTPS_DOMAIN/video-call.html?turn=turn:TURN_HOST:3478&tu=USER&tp=PASS');
      }
    }, 8000);
  } catch (_) { }
}

function teardown(reason, keepLocal) {
  setStatus(`종료: ${reason}`);
  if (pc) { pc.getSenders().forEach(s => s.track && s.track.stop()); pc.close(); pc = null; }
  roomId = null;
  if (!keepLocal) {
    if (localStream) {
      localStream.getTracks().forEach(t => t.stop());
      localStream = null;
    }
  }
  micSender = null;
  camSender = null;
  const lv = $('localVideo');
  const rv = $('remoteVideo');
  if (!keepLocal) { if (lv) lv.srcObject = null; }
  if (rv) rv.srcObject = null;
  showRemoteWaiting(true);
}

(function () {
  const rv = document.getElementById('remoteVideo');
  if (!rv) return;
  ['loadedmetadata', 'loadeddata', 'canplay', 'playing'].forEach(ev => {
    rv.addEventListener(ev, () => showRemoteWaiting(false));
  });
})();

function setupButtons() {
  const btnStart = $('btnStart');
  const btnStop = $('btnStop');
  const btnHangup = $('btnHangup');
  const btnMute = $('btnMute');
  const btnCamera = $('btnCamera');
  const btnBack = $('btnBack');

  if (btnStart) {
    btnStart.onclick = async () => {
      try {
        await getMedia();
        const lv = $('localVideo');
        if (lv) { try { await lv.play(); } catch (_) { } }
      } catch (e) {
        alert(`카메라/마이크 권한을 허용해야 매칭이 가능합니다.\n사유: ${e && e.name ? e.name : 'Unknown'} ${e && e.message ? e.message : ''}`);
        return;
      }
      if (!isSecureContext && location.hostname !== 'localhost') {
        alert('모바일 브라우저는 HTTPS에서만 카메라 권한을 허용합니다. HTTPS 주소로 접속해 주세요.');
        return;
      }
      stayMatching = true;
      wsSend({ type: 'joinQueue' });
    };
  }

  if (btnStop) {
    btnStop.onclick = () => { 
      stayMatching = false; 
      wsSend({ type: 'leaveQueue' }); 
      showHangupButton(false);
    };
  }

  if (btnHangup) {
    btnHangup.onclick = () => { 
      stayMatching = false;
      if (roomId) {
        wsSend({ type: 'endCall', roomId }); 
      }
      wsSend({ type: 'leaveQueue' });
      teardown('수동 종료', false);
      showHangupButton(false);
    };
  }

  if (btnMute) {
    btnMute.onclick = () => {
      const track = (micSender && micSender.track) || (localStream && localStream.getAudioTracks()[0]);
      if (!track) { alert('마이크 트랙이 없습니다.'); return; }
      track.enabled = !track.enabled;
      alert(track.enabled ? '마이크가 켜졌습니다.' : '마이크가 꺼졌습니다.');
    };
  }

  if (btnCamera) {
    btnCamera.onclick = () => {
      const track = (camSender && camSender.track) || (localStream && localStream.getVideoTracks()[0]);
      if (!track) { alert('카메라 트랙이 없습니다.'); return; }
      track.enabled = !track.enabled;
      alert(track.enabled ? '카메라가 켜졌습니다.' : '카메라가 꺼졌습니다.');
    };
  }

function closeWindowAndCleanup() {
  // 통화 중이면 통화 종료
  if (roomId) {
    wsSend({ type: 'endCall', roomId });
  }
  
  // 매칭 중이면 대기열에서 제거
  if (stayMatching) {
    wsSend({ type: 'leaveQueue' });
  }
  stayMatching = false;
  
  // 모든 리소스 정리
  teardown('메인으로 나가기', false);
  showHangupButton(false);
  
  // WebSocket 연결 종료
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.close();
  }
  
  // BroadcastChannel 닫기
  if (broadcastChannel) {
    broadcastChannel.close();
  }
  
  // 창 닫기 시도
  setTimeout(() => {
    window.close();
    // 창이 닫히지 않으면 페이지를 빈 페이지로 변경
    setTimeout(() => {
      if (!document.hidden) {
        document.body.innerHTML = '<div style="padding:20px;text-align:center;"><h2>영상통화가 종료되었습니다.</h2><p>이 창을 닫아주세요.</p></div>';
      }
    }, 200);
  }, 100);
}

  if (btnBack) {
    btnBack.onclick = () => {
      // 다른 모든 창에 닫기 메시지 전송
      if (broadcastChannel) {
        broadcastChannel.postMessage('close-window');
      }
      
      // 현재 창도 닫기
      closeWindowAndCleanup();
    };
  }
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', setupButtons);
} else {
  setupButtons();
}

