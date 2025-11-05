const wsScheme = location.protocol === 'https:' ? 'wss' : 'ws';
const ws = new WebSocket(`${wsScheme}://${location.host}/ws`);
let userId, peerId, roomId, pc, localStream;
let remoteReadyWatchdog = null;
let pendingRemoteCandidates = [];
let micSender = null, camSender = null;

const $ = (id) => document.getElementById(id);
function showRemoteWaiting(show){
  const ph = document.getElementById('remotePlaceholder');
  if (!ph) return;
  ph.style.display = show ? '' : 'none';
}
function alertOnTrackEvents(track, label){
  if (!track) return;
  track.addEventListener('ended', () => {
    alert(`${label} 장치 연결이 종료되었습니다.`);
  });
  track.addEventListener('mute', () => {
    // 신호 중단(권한 차단/장치 문제) 알림
    alert(`${label}가 꺼졌습니다.`);
  });
  track.addEventListener('unmute', () => {
    alert(`${label}가 켜졌습니다.`);
  });
}
function attachLocalTrackAlerts(stream){
  try {
    stream.getAudioTracks().forEach(t => alertOnTrackEvents(t, '마이크'));
    stream.getVideoTracks().forEach(t => alertOnTrackEvents(t, '카메라'));
  } catch (_) {}
}
function hideOverlayIfVideoLive(){
  const rv = document.getElementById('remoteVideo');
  if (!rv) return;
  const stream = rv.srcObject;
  if (stream && stream.getVideoTracks && stream.getVideoTracks().some(t=>t.readyState==='live')) {
    showRemoteWaiting(false);
  }
}
function startRemoteReadyWatchdog(){
  const rv = document.getElementById('remoteVideo');
  if (!rv) return;
  if (remoteReadyWatchdog) { clearInterval(remoteReadyWatchdog); remoteReadyWatchdog = null; }
  let attempts = 0;
  remoteReadyWatchdog = setInterval(() => {
    attempts++;
    // 비디오 픽셀 크기 또는 트랙 live 여부로 판정
    if ((rv.videoWidth && rv.videoHeight) || (rv.srcObject && rv.srcObject.getVideoTracks && rv.srcObject.getVideoTracks().some(t=>t.readyState==='live'))) {
      showRemoteWaiting(false);
      clearInterval(remoteReadyWatchdog);
      remoteReadyWatchdog = null;
    } else if (attempts > 40) { // ~12초 후 중지
      clearInterval(remoteReadyWatchdog);
      remoteReadyWatchdog = null;
    }
  }, 300);
}
const statusEl = $('status');
const chatList = $('chatList');
const chatInput = $('chatInput');

function setStatus(text){ statusEl.textContent = text; }

ws.addEventListener('open', () => setStatus('서버 연결됨'));
ws.addEventListener('message', async (ev) => {
  const msg = JSON.parse(ev.data);
  switch (msg.type) {
    case 'hello':
      userId = msg.userId;
      break;
    case 'enqueued':
      // 총 대기 인원 표기: queueSize(본인 포함)
      if (typeof msg.queueSize === 'number') {
        setStatus(`대기중 (${msg.queueSize}명 대기)`);
      } else {
        setStatus(`대기중 (앞선 ${Math.max(0, (msg.position||1)-1)}명)`);
      }
      break;
    case 'queueUpdate':
      // 서버가 queueSize를 보내므로 이를 우선 사용
      if (typeof msg.queueSize === 'number') {
        setStatus(`대기중 (${msg.queueSize}명 대기)`);
      } else {
        setStatus(`대기중 (앞선 ${msg.ahead||0}명)`);
      }
      break;
    case 'dequeued':
      setStatus('대기 종료');
      break;
    case 'matched':
      roomId = msg.roomId;
      peerId = msg.peerId;
      setStatus(`매칭됨 (room ${roomId.substring(0,8)})`);
      // 상대 연결 대기 표시 유지 (실제 재생되면 자동으로 숨김)
      showRemoteWaiting(true);
      startRemoteReadyWatchdog();
      // 같은 브라우저 두 탭에서의 glare 방지: 한쪽만 초기 offer 생성
      // UUID 문자열 비교로 간단히 호출자 결정(작은 쪽이 caller)
      const iAmCaller = (typeof userId === 'string' && typeof peerId === 'string')
        ? (userId.localeCompare(peerId) < 0)
        : true; // 안전장치: 정보가 없으면 임시로 caller 처리
      await startWebRTC(iAmCaller);
      break;
    case 'rtc.offer':
      await ensurePc();
      try {
        // glare 해결: 로컬 오퍼 중이면 롤백 후 원격 오퍼 수락
        if (pc.signalingState === 'have-local-offer') {
          await pc.setLocalDescription({ type: 'rollback' });
        }
        await pc.setRemoteDescription(new RTCSessionDescription(msg.data));
        if (pendingRemoteCandidates.length) {
          for (const c of pendingRemoteCandidates) { try { await pc.addIceCandidate(c); } catch(_){} }
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
          for (const c of pendingRemoteCandidates) { try { await pc.addIceCandidate(c); } catch(_){} }
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
          try { await pc.addIceCandidate(msg.data); } catch(e){ console.warn('addIceCandidate error', e); }
        } else {
          pendingRemoteCandidates.push(msg.data);
        }
      }
      break;
    case 'callEnded':
      teardown('상대 종료');
      showRemoteWaiting(true);
      break;
  }
});

function wsSend(o){ ws.readyState === 1 && ws.send(JSON.stringify(o)); }

async function getMedia(){
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

async function ensurePc(){
  if (pc) return pc;
  const params = new URLSearchParams(location.search);
  const iceServers = [{ urls: ['stun:stun.l.google.com:19302'] }];
  const turnUrls = params.get('turn');
  const turnUser = params.get('tu');
  const turnPass = params.get('tp');
  // 우선 URL 파라미터 우선 적용
  if (turnUrls) {
    const urls = turnUrls.split(',').map(u => u.trim()).filter(Boolean);
    iceServers.push({ urls, username: turnUser || undefined, credential: turnPass || undefined });
    try {
      localStorage.setItem('turnUrls', turnUrls);
      localStorage.setItem('turnUser', turnUser || '');
      localStorage.setItem('turnPass', turnPass || '');
    } catch (_) {}
  } else {
    // 저장된 TURN 설정이 있으면 사용
    try {
      const savedUrls = localStorage.getItem('turnUrls');
      if (savedUrls) {
        const savedUser = localStorage.getItem('turnUser') || undefined;
        const savedPass = localStorage.getItem('turnPass') || undefined;
        const urls = savedUrls.split(',').map(u => u.trim()).filter(Boolean);
        iceServers.push({ urls, username: savedUser, credential: savedPass });
      }
    } catch (_) {}
  }
  pc = new RTCPeerConnection({ iceServers });
  pc.ontrack = (e) => {
    const rv = $('remoteVideo');
    rv.srcObject = e.streams[0];
    if (rv.muted !== true) rv.muted = true;
    if (!rv.hasAttribute('playsinline')) rv.setAttribute('playsinline','');
    rv.addEventListener('click', () => { try { rv.muted = false; rv.play().catch(()=>{}); } catch(_) {} }, { once:true });
    // iOS 사파리는 사용자 제스처 이후에도 재생을 명시적으로 호출해야 할 때가 있음
    setTimeout(() => { rv.play().catch(()=>{}); hideOverlayIfVideoLive(); }, 0);
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
  pc.onicecandidate = (e) => e.candidate && wsSend({ type:'rtc.ice', roomId, data: e.candidate });
  const stream = await getMedia();
  stream.getAudioTracks().forEach(t => { micSender = pc.addTrack(t, stream); });
  stream.getVideoTracks().forEach(t => { camSender = pc.addTrack(t, stream); });
  return pc;
}

async function startWebRTC(isCaller){
  await ensurePc();
  if (isCaller){
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    wsSend({ type:'rtc.offer', roomId, data: pc.localDescription });
  }
  // 연결이 오래 걸리면(TURN 미설정 가능) 힌트 제공
  try {
    const hasTurn = !!(new URLSearchParams(location.search).get('turn') || localStorage.getItem('turnUrls'));
    setTimeout(() => {
      if (!pc) return;
      const s = pc.iceConnectionState;
      if ((s !== 'connected' && s !== 'completed') && !hasTurn) {
        alert('다른 와이파이/모바일망 간 연결에는 TURN 서버가 필요할 수 있습니다.\n예: https://YOUR_HTTPS_DOMAIN/video-call.html?turn=turn:TURN_HOST:3478&tu=USER&tp=PASS');
      }
    }, 8000);
  } catch(_) {}
}

function teardown(reason){
  setStatus(`종료: ${reason}`);
  if (pc){ pc.getSenders().forEach(s=>s.track&&s.track.stop()); pc.close(); pc = null; }
  roomId = null;
  if (localStream){
    localStream.getTracks().forEach(t => t.stop());
    localStream = null;
  }
  micSender = null;
  camSender = null;
  const lv = $('localVideo');
  const rv = $('remoteVideo');
  if (lv) lv.srcObject = null;
  if (rv) rv.srcObject = null;
  showRemoteWaiting(true);
}

// 비디오 이벤트 기반으로도 오버레이 제어 (브라우저별 ontrack 순서 차이 보완)
(function(){
  const rv = document.getElementById('remoteVideo');
  if (!rv) return;
  ['loadedmetadata','loadeddata','canplay','playing'].forEach(ev => {
    rv.addEventListener(ev, () => showRemoteWaiting(false));
  });
})();

// UI
$('btnStart').onclick = async () => {
  try {
    await getMedia();
    const lv = $('localVideo');
    if (lv) { try { await lv.play(); } catch (_) {} }
  } catch (e) {
    alert(`카메라/마이크 권한을 허용해야 매칭이 가능합니다.\n사유: ${e && e.name ? e.name : 'Unknown'} ${e && e.message ? e.message : ''}`);
    return;
  }
  if (!isSecureContext && location.hostname !== 'localhost') {
    alert('모바일 브라우저는 HTTPS에서만 카메라 권한을 허용합니다. HTTPS 주소로 접속해 주세요.');
    return;
  }
  wsSend({ type:'joinQueue' });
};
$('btnStop').onclick = () => wsSend({ type:'leaveQueue' });
$('btnHangup').onclick = () => { if (roomId) wsSend({ type:'endCall', roomId }); teardown('수동 종료'); };

$('btnMute').onclick = () => {
  const track = (micSender && micSender.track) || (localStream && localStream.getAudioTracks()[0]);
  if (!track) { alert('마이크 트랙이 없습니다.'); return; }
  track.enabled = !track.enabled;
  alert(track.enabled ? '마이크가 켜졌습니다.' : '마이크가 꺼졌습니다.');
};

$('btnCamera').onclick = () => {
  const track = (camSender && camSender.track) || (localStream && localStream.getVideoTracks()[0]);
  if (!track) { alert('카메라 트랙이 없습니다.'); return; }
  track.enabled = !track.enabled;
  alert(track.enabled ? '카메라가 켜졌습니다.' : '카메라가 꺼졌습니다.');
};

// no TURN form events

// chat removed


