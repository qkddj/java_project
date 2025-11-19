const wsScheme = location.protocol === 'https:' ? 'wss' : 'ws';
const ws = new WebSocket(`${wsScheme}://${location.host}/ws`);
const isSecureContext = window.isSecureContext || (location.protocol === 'https:' || location.hostname === 'localhost' || location.hostname === '127.0.0.1');
let userId, peerId, roomId, pc, localStream;
let stayMatching = false;
let remoteReadyWatchdog = null;
let pendingRemoteCandidates = [];
let micSender = null, camSender = null;
let partnerUsername = null; // 상대방 username 저장
let currentUsername = null; // 현재 유저의 username

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

// URL에서 username 가져오기
const urlParams = new URLSearchParams(location.search);
currentUsername = urlParams.get('username') || 'unknown';

ws.addEventListener('open', () => {
  setStatus('서버 연결됨');
  console.log('WebSocket 연결됨, username:', currentUsername);
  // WebSocket 연결 시 username 등록
  if (currentUsername && currentUsername !== 'unknown') {
    console.log('Username 등록 전송:', currentUsername);
    wsSend({ type: 'registerUsername', username: currentUsername });
  }
});
ws.addEventListener('message', async (ev) => {
  const msg = JSON.parse(ev.data);
  switch (msg.type) {
    case 'hello':
      userId = msg.userId;
      break;
    case 'enqueued':
      if (typeof msg.queueSize === 'number') {
        if (msg.queueSize === 0) {
          setStatus('대기중');
        } else {
          setStatus(`대기중 (${msg.queueSize}명 대기)`);
        }
      } else {
        setStatus('대기중');
      }
      showHangupButton(true);
      break;
    case 'queueUpdate':
      if (typeof msg.queueSize === 'number') {
        if (msg.queueSize === 0) {
          setStatus('대기중');
        } else {
          setStatus(`대기중 (${msg.queueSize}명 대기)`);
        }
      } else {
        setStatus('대기중');
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
      partnerUsername = msg.partnerUsername || null; // 상대방 username 저장 (평점용, UI에는 표시 안함)
      console.log('매칭 완료! roomId:', roomId);
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
      // 즉시 원격 비디오 정리
      const rv = $('remoteVideo');
      if (rv && rv.srcObject) {
        const remoteStream = rv.srcObject;
        remoteStream.getTracks().forEach(track => {
          track.stop();
          track.enabled = false;
        });
        rv.srcObject = null;
        rv.pause();
      }
      
      // 평점 입력 (통화가 있었던 경우)
      if (partnerUsername && roomId) {
        showRatingDialog();
      }
      
      // 상대방이 종료하면 자동으로 대기 상태로 복귀
      // 로컬 스트림은 유지 (keepLocal = true)
      teardown('상대 종료', true);
      showRemoteWaiting(true);
      
      // 로컬 비디오가 계속 재생되도록 확인
      const lv = $('localVideo');
      if (lv && localStream) {
        lv.srcObject = localStream;
        lv.play().catch(() => {});
      }
      
      // 자동으로 대기 상태로 복귀
      stayMatching = true;
      setStatus('다음 상대 대기중');
      wsSend({ type: 'joinQueue' });
      showHangupButton(true);
      
      // 다음 매칭을 위해 partnerUsername 초기화
      partnerUsername = null;
      break;
  }
});

function wsSend(o) { ws.readyState === 1 && ws.send(JSON.stringify(o)); }

async function getMedia() {
  // 기존 스트림이 있고 live 상태인지 확인
  if (localStream) {
    const hasLive = localStream.getTracks().some(t => t.readyState === 'live');
    if (hasLive) return localStream;
    // 스트림이 있지만 live가 아니면 정리
    localStream.getTracks().forEach(t => t.stop());
    localStream = null;
  }
  
  // mediaDevices 지원 확인
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    let hint = '브라우저가 getUserMedia를 지원하지 않거나 정책에 의해 비활성화되었습니다.';
    if (!isSecureContext && location.hostname !== 'localhost' && location.hostname !== '127.0.0.1') {
      hint = 'HTTPS가 필요합니다!\n\n' +
             '다른 디바이스에서 카메라/마이크를 사용하려면 HTTPS가 필요합니다.\n\n' +
             '해결 방법:\n' +
             '1. ngrok 사용 (권장):\n' +
             '   - ngrok 설치: https://ngrok.com/\n' +
             '   - 터미널에서: ngrok http 8080\n' +
             '   - 표시된 https://xxx.ngrok.io URL 사용\n\n' +
             '2. 또는 서버 컴퓨터에서 localhost로 접속 (카메라 사용 가능)';
    }
    throw new Error('mediaDevices_unavailable: ' + hint);
  }
  
  try {
    // 명시적으로 권한 요청 (video와 audio 모두)
    localStream = await navigator.mediaDevices.getUserMedia({ 
      video: { 
        width: { ideal: 1280 },
        height: { ideal: 720 },
        facingMode: 'user'
      }, 
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true
      }
    });
    
    // 비디오 요소에 스트림 연결
    const lv = $('localVideo');
    if (lv) {
      lv.srcObject = localStream;
      // 재생 시도
      try {
        await lv.play();
      } catch (playErr) {
        console.warn('로컬 비디오 재생 실패:', playErr);
      }
    }
    
    micSender = null;
    camSender = null;
    attachLocalTrackAlerts(localStream);
    return localStream;
  } catch (error) {
    // 에러 발생 시 스트림 정리
    if (localStream) {
      localStream.getTracks().forEach(t => t.stop());
      localStream = null;
    }
    
    // 사용자 친화적인 에러 메시지
    let errorMessage = '카메라/마이크 권한을 허용해주세요.';
    if (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError') {
      errorMessage = '카메라/마이크 권한이 거부되었습니다.\n\n브라우저 주소창의 자물쇠 아이콘을 클릭하여 권한을 허용해주세요.';
    } else if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') {
      errorMessage = '카메라나 마이크를 찾을 수 없습니다.\n\n장치가 연결되어 있는지 확인해주세요.';
    } else if (error.name === 'NotReadableError' || error.name === 'TrackStartError') {
      errorMessage = '카메라나 마이크에 접근할 수 없습니다.\n\n다른 프로그램에서 사용 중일 수 있습니다.';
    } else if (error.message) {
      errorMessage = error.message;
    }
    
    throw new Error(errorMessage);
  }
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
  
  // 원격 비디오 스트림 정리
  const rv = $('remoteVideo');
  if (rv && rv.srcObject) {
    const remoteStream = rv.srcObject;
    remoteStream.getTracks().forEach(track => {
      track.stop();
      track.enabled = false;
    });
    rv.srcObject = null;
    rv.pause();
  }
  
  // PeerConnection 정리
  if (pc) {
    // 모든 트랙 중지
    pc.getSenders().forEach(sender => {
      if (sender.track) {
        sender.track.stop();
      }
    });
    // 원격 트랙도 정리
    pc.getReceivers().forEach(receiver => {
      if (receiver.track) {
        receiver.track.stop();
      }
    });
    pc.close();
    pc = null;
  }
  
  roomId = null;
  if (!keepLocal) {
    if (localStream) {
      localStream.getTracks().forEach(t => t.stop());
      localStream = null;
    }
    const lv = $('localVideo');
    if (lv) {
      lv.srcObject = null;
      lv.pause();
    }
  }
  micSender = null;
  camSender = null;
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
      // 버튼 비활성화하여 중복 클릭 방지
      btnStart.disabled = true;
      btnStart.textContent = '권한 요청 중...';
      
      try {
        // 권한 요청 (명시적으로 호출)
        await getMedia();
        
        // 로컬 비디오 재생 확인
        const lv = $('localVideo');
        if (lv && lv.srcObject) {
          try {
            await lv.play();
            console.log('로컬 비디오 재생 성공');
          } catch (playErr) {
            console.warn('로컬 비디오 재생 실패:', playErr);
          }
        }
        
        // HTTPS 확인 (localhost 제외)
        if (!isSecureContext && location.hostname !== 'localhost' && location.hostname !== '127.0.0.1') {
          alert('모바일 브라우저는 HTTPS에서만 카메라 권한을 허용합니다.\n\nHTTPS 주소로 접속해 주세요.');
          btnStart.disabled = false;
          btnStart.textContent = '매칭 시작';
          return;
        }
        
        // 매칭 시작
        stayMatching = true;
        console.log('joinQueue 전송');
        wsSend({ type: 'joinQueue' });
        
        // 버튼 텍스트 변경
        btnStart.textContent = '매칭 중...';
      } catch (e) {
        // 에러 메시지 표시
        const errorMsg = e && e.message ? e.message : 
                        (e && e.name ? `${e.name}: 권한을 허용해주세요.` : '알 수 없는 오류가 발생했습니다.');
        alert(`카메라/마이크 권한을 허용해야 매칭이 가능합니다.\n\n${errorMsg}`);
        
        // 버튼 복원
        btnStart.disabled = false;
        btnStart.textContent = '매칭 시작';
        console.error('getMedia 오류:', e);
      }
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
      
      // 즉시 원격 비디오 정리
      const rv = $('remoteVideo');
      if (rv && rv.srcObject) {
        const remoteStream = rv.srcObject;
        remoteStream.getTracks().forEach(track => {
          track.stop();
          track.enabled = false;
        });
        rv.srcObject = null;
        rv.pause();
      }
      
      if (roomId) {
        wsSend({ type: 'endCall', roomId }); 
      }
      
      // 평점 입력
      if (partnerUsername) {
        showRatingDialog();
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

function showRatingDialog() {
  const dialog = document.getElementById('ratingDialog');
  const ratingButtons = dialog.querySelectorAll('.rating-btn');
  const submitBtn = document.getElementById('ratingSubmitBtn');
  const skipBtn = document.getElementById('ratingSkipBtn');
  let selectedRating = null;
  
  // 다이얼로그 표시
  dialog.classList.remove('hidden');
  
  // 평점 버튼 클릭 이벤트
  ratingButtons.forEach(btn => {
    btn.classList.remove('selected');
    btn.onclick = () => {
      // 기존 선택 해제
      ratingButtons.forEach(b => b.classList.remove('selected'));
      // 새 선택
      btn.classList.add('selected');
      selectedRating = parseInt(btn.dataset.rating);
      submitBtn.disabled = false;
    };
  });
  
  // 제출 버튼
  submitBtn.disabled = true;
  submitBtn.onclick = () => {
    if (selectedRating && selectedRating >= 1 && selectedRating <= 5) {
      submitRating(selectedRating);
      dialog.classList.add('hidden');
    }
  };
  
  // 건너뛰기 버튼
  skipBtn.onclick = () => {
    dialog.classList.add('hidden');
    console.log('평가 건너뛰기');
  };
}

function submitRating(rating) {
  if (partnerUsername && ws && ws.readyState === WebSocket.OPEN) {
    wsSend({
      type: 'submitRating',
      partnerUsername: partnerUsername,
      rating: rating,
      serviceType: 'video'
    });
    console.log('평점 전송: ' + rating + '점');
  }
}

function closeWindowAndCleanup() {
  // 즉시 원격 비디오 정리
  const rv = $('remoteVideo');
  if (rv && rv.srcObject) {
    const remoteStream = rv.srcObject;
    remoteStream.getTracks().forEach(track => {
      track.stop();
      track.enabled = false;
    });
    rv.srcObject = null;
    rv.pause();
  }
  
  // 통화 중이면 통화 종료
  if (roomId) {
    wsSend({ type: 'endCall', roomId });
  }
  
  // 평점 입력 (통화가 있었던 경우)
  if (partnerUsername && roomId) {
    showRatingDialog();
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

