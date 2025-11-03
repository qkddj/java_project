const wsScheme = location.protocol === 'https:' ? 'wss' : 'ws';
const ws = new WebSocket(`${wsScheme}://${location.host}/ws`);
let userId, roomId, pc, localStream;
let micSender = null, camSender = null;

const $ = (id) => document.getElementById(id);
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
      setStatus(`대기중 (앞선 ${Math.max(0, (msg.position||1)-1)}명)`);
      break;
    case 'queueUpdate':
      setStatus(`대기중 (앞선 ${msg.ahead||0}명)`);
      break;
    case 'dequeued':
      setStatus('대기 종료');
      break;
    case 'matched':
      roomId = msg.roomId;
      setStatus(`매칭됨 (room ${roomId.substring(0,8)})`);
      // 상대 영상 연결 준비중: 대기 오버레이는 숨김
      (function(){ const ph = document.getElementById('remotePlaceholder'); if (ph) ph.style.display = 'none'; })();
      await startWebRTC(true);
      break;
    case 'rtc.offer':
      await ensurePc();
      await pc.setRemoteDescription(new RTCSessionDescription(msg.data));
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      wsSend({ type: 'rtc.answer', roomId, data: pc.localDescription });
      break;
    case 'rtc.answer':
      await pc.setRemoteDescription(new RTCSessionDescription(msg.data));
      break;
    case 'rtc.ice':
      if (msg.data) await pc.addIceCandidate(msg.data);
      break;
    case 'callEnded':
      teardown('상대 종료');
      (function(){ const ph = document.getElementById('remotePlaceholder'); if (ph) ph.style.display = ''; })();
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
  return localStream;
}

async function ensurePc(){
  if (pc) return pc;
  const params = new URLSearchParams(location.search);
  const iceServers = [{ urls: ['stun:stun.l.google.com:19302'] }];
  const turnUrls = params.get('turn');
  const turnUser = params.get('tu');
  const turnPass = params.get('tp');
  if (turnUrls) {
    const urls = turnUrls.split(',').map(u => u.trim()).filter(Boolean);
    iceServers.push({ urls, username: turnUser || undefined, credential: turnPass || undefined });
  }
  pc = new RTCPeerConnection({ iceServers });
  pc.ontrack = (e) => {
    const rv = $('remoteVideo');
    rv.srcObject = e.streams[0];
    // iOS 사파리는 사용자 제스처 이후에도 재생을 명시적으로 호출해야 할 때가 있음
    setTimeout(() => { rv.play().catch(()=>{}); }, 0);
    const ph = document.getElementById('remotePlaceholder');
    if (ph) ph.style.display = 'none';
  };
  pc.oniceconnectionstatechange = () => {
    const state = pc.iceConnectionState;
    const ph = document.getElementById('remotePlaceholder');
    if (!ph) return;
    if (state === 'connected' || state === 'completed') ph.style.display = 'none';
    if (state === 'disconnected' || state === 'failed' || state === 'closed') ph.style.display = '';
  };
  pc.onconnectionstatechange = () => {
    const state = pc.connectionState;
    const ph = document.getElementById('remotePlaceholder');
    if (!ph) return;
    if (state === 'connected') ph.style.display = 'none';
    if (state === 'disconnected' || state === 'failed' || state === 'closed') ph.style.display = '';
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
  const ph = document.getElementById('remotePlaceholder');
  if (ph) ph.style.display = '';
}

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
};

$('btnCamera').onclick = () => {
  const track = (camSender && camSender.track) || (localStream && localStream.getVideoTracks()[0]);
  if (!track) { alert('카메라 트랙이 없습니다.'); return; }
  track.enabled = !track.enabled;
};

// no TURN form events

// chat removed


