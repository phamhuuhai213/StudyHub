'use strict';

/**
 * video.js (hoàn chỉnh) - WebRTC + STOMP signaling
 * - Tương thích với socket.js hiện tại (đã set window.stompClient)
 * - Subscribe /user/queue/video-call sẽ gọi window.handleVideoSignal(payload)
 * - Có queue ICE candidate (tránh hên xui)
 * - Chặn crash khi getUserMedia fail
 * - Reset state sạch khi kết thúc cuộc gọi
 */

// ======================
// CẤU HÌNH WEBRTC
// ======================
const rtcConfig = {
    iceServers: [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' },
        { urls: 'stun:stun2.l.google.com:19302' },
        { urls: 'stun:stun3.l.google.com:19302' },
        { urls: 'stun:stun4.l.google.com:19302' }
    ],
};

// ======================
// STATE
// ======================
let peerConnection = null;
let localStream = null;

let remoteUsername = null;   // người bên kia (username)
let pendingOffer = null;     // offer chờ accept (string JSON)
let queuedCandidates = [];   // ICE candidate đến sớm

let callActive = false;      // đang trong call / đang ringing

// ======================
// HELPERS: DOM + BOOTSTRAP
// ======================
function $(id) {
    return document.getElementById(id);
}

function showModal(modalId) {
    const el = $(modalId);
    if (!el) return null;
    const modal = new bootstrap.Modal(el);
    modal.show();
    return modal;
}

function hideModal(modalId) {
    const el = $(modalId);
    if (!el) return;
    const inst = bootstrap.Modal.getInstance(el);
    if (inst) inst.hide();
}

// ======================
// HELPERS: STOMP
// ======================
function getStompClient() {
    const c = window.stompClient || window.stompClientGlobal;
    if (c && c.connected) return c;
    return null;
}

function sendSignal(type, data) {
    const client = getStompClient();
    if (!client) {
        console.error('[VideoCall] STOMP chưa connected. Không gửi được signal:', type);
        return;
    }
    if (!remoteUsername) {
        console.error('[VideoCall] Thiếu remoteUsername. Không gửi được signal:', type);
        return;
    }

    const msg = {
        type,
        data,
        recipient: remoteUsername, // backend sẽ map username -> email để route
    };

    console.log('[VideoCall] Send signal:', type, 'to:', remoteUsername);
    client.send('/app/chat.videoCall', {}, JSON.stringify(msg));
}

// ======================
// WEBRTC CORE
// ======================
async function setupLocalStream() {
    try {
        localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
        const localVideo = $('localVideo');
        if (localVideo) localVideo.srcObject = localStream;
        return true;
    } catch (e) {
        console.error('[VideoCall] getUserMedia error:', e);
        alert('Không thể bật Camera/Mic: ' + (e?.message || e));
        return false;
    }
}

function createPeerConnection() {
    peerConnection = new RTCPeerConnection(rtcConfig);

    // add local tracks
    if (localStream) {
        localStream.getTracks().forEach((track) => {
            peerConnection.addTrack(track, localStream);
        });
    }

    // remote stream
    peerConnection.ontrack = (event) => {
        console.log('[VideoCall] Remote track:', event);
        const remoteVideo = $('remoteVideo');
        if (!remoteVideo) return;

        if (event.streams && event.streams[0]) {
            remoteVideo.srcObject = event.streams[0];
        } else {
            // fallback
            if (!remoteVideo.srcObject) remoteVideo.srcObject = new MediaStream();
            remoteVideo.srcObject.addTrack(event.track);
        }
    };

    // local ICE -> send to other
    peerConnection.onicecandidate = (event) => {
        if (event.candidate) {
            sendSignal('candidate', JSON.stringify(event.candidate));
        }
    };

    peerConnection.onconnectionstatechange = () => {
        const st = peerConnection?.connectionState;
        console.log('[VideoCall] connectionState:', st);

        // Nếu failed/disconnected lâu có thể endCall
        if (st === 'failed') {
            console.warn('[VideoCall] Connection failed. Ending call.');
            endCall(false);
            alert('Kết nối cuộc gọi thất bại.');
        }
    };

    peerConnection.oniceconnectionstatechange = () => {
        console.log('[VideoCall] iceConnectionState:', peerConnection?.iceConnectionState);
    };
}

async function flushCandidates() {
    if (!peerConnection?.remoteDescription) return;
    if (!queuedCandidates.length) return;

    const list = [...queuedCandidates];
    queuedCandidates = [];

    for (const c of list) {
        try {
            await peerConnection.addIceCandidate(new RTCIceCandidate(c));
        } catch (e) {
            console.error('[VideoCall] addIceCandidate (flush) error:', e);
        }
    }
}

// ======================
// SIGNAL HANDLER (được socket.js gọi)
// ======================
async function handleVideoSignal(payload) {
    console.log('[VideoCall] 🔥 Received payload from /user/queue/video-call');

    let message;
    try {
        message = JSON.parse(payload.body);
    } catch (e) {
        console.error('[VideoCall] Payload parse error:', e);
        return;
    }

    const { type, sender, data } = message;
    console.log('[VideoCall] Signal:', type, 'from:', sender);

    // Có thể data là string JSON
    // remoteUsername luôn là sender khi nhận offer/answer/candidate/leave
    if (sender) remoteUsername = sender;

    try {
        if (type === 'offer') {
            // Nếu đang trong cuộc gọi/ringing mà có offer mới -> reject để tránh collision
            if (callActive) {
                console.warn('[VideoCall] Đang bận. Tự động reject offer từ:', sender);
                // trả leave để bên kia biết
                sendSignal('leave', 'busy');
                return;
            }

            callActive = true;
            pendingOffer = data; // string JSON của RTCSessionDescription
            queuedCandidates = []; // reset queue cho call mới

            const callerNameEl = $('caller-name');
            if (callerNameEl) callerNameEl.textContent = (sender || 'Ai đó') + ' đang gọi...';

            showModal('incomingCallModal');
            return;
        }

        if (type === 'answer') {
            if (!peerConnection) {
                console.warn('[VideoCall] Nhận answer nhưng peerConnection chưa có.');
                return;
            }
            await peerConnection.setRemoteDescription(
                new RTCSessionDescription(JSON.parse(data))
            );
            await flushCandidates();
            return;
        }

        if (type === 'candidate') {
            if (!peerConnection) {
                // candidate đến trước khi tạo PC (hiếm, nhưng có)
                try {
                    queuedCandidates.push(JSON.parse(data));
                } catch {}
                return;
            }

            const candObj = JSON.parse(data);
            if (peerConnection.remoteDescription) {
                await peerConnection.addIceCandidate(new RTCIceCandidate(candObj));
            } else {
                queuedCandidates.push(candObj);
            }
            return;
        }

        if (type === 'leave') {
            // đối phương kết thúc / từ chối
            endCall(false);
            alert('Cuộc gọi đã kết thúc.');
            return;
        }

        console.warn('[VideoCall] Unknown signal type:', type);
    } catch (e) {
        console.error('[VideoCall] handle signal error:', e);
    }
}

// ======================
// ACTIONS
// ======================
async function startVideoCall(partnerUsername) {
    console.log('[VideoCall] Start call to:', partnerUsername);

    if (!partnerUsername) {
        alert('Lỗi: Không tìm thấy username người nhận!');
        return;
    }

    // Nếu đang call/ringing thì không gọi tiếp
    if (callActive) {
        alert('Bạn đang trong một cuộc gọi hoặc đang có cuộc gọi đến.');
        return;
    }

    remoteUsername = partnerUsername;
    callActive = true;
    pendingOffer = null;
    queuedCandidates = [];

    // Mở modal video ngay khi bắt đầu call
    showModal('videoCallModal');

    const ok = await setupLocalStream();
    if (!ok) {
        // Không có camera/mic -> thoát
        endCall(false);
        return;
    }

    createPeerConnection();

    // Tạo offer
    const offer = await peerConnection.createOffer();
    await peerConnection.setLocalDescription(offer);

    sendSignal('offer', JSON.stringify(offer));
}

async function acceptCall() {
    // Người nhận bấm accept khi đang có pendingOffer
    if (!pendingOffer) {
        console.warn('[VideoCall] acceptCall nhưng pendingOffer rỗng');
        return;
    }

    hideModal('incomingCallModal');
    showModal('videoCallModal');

    const ok = await setupLocalStream();
    if (!ok) {
        // fail camera/mic -> kết thúc, báo bên kia
        sendSignal('leave', 'no-media');
        endCall(false);
        return;
    }

    createPeerConnection();

    // Set Remote (offer)
    await peerConnection.setRemoteDescription(
        new RTCSessionDescription(JSON.parse(pendingOffer))
    );
    await flushCandidates();

    // Tạo answer
    const answer = await peerConnection.createAnswer();
    await peerConnection.setLocalDescription(answer);

    sendSignal('answer', JSON.stringify(answer));

    // Sau khi accept, offer đã xử lý xong
    pendingOffer = null;
}

function rejectCall() {
    hideModal('incomingCallModal');

    // Báo bên kia biết từ chối
    if (remoteUsername) {
        sendSignal('leave', 'rejected');
    }

    // Reset state
    endCall(false);
}

function endCall(isInitiator) {
    // Nếu mình chủ động kết thúc thì báo bên kia
    if (isInitiator && remoteUsername) {
        sendSignal('leave', 'ended');
    }

    // close pc
    try {
        if (peerConnection) {
            peerConnection.ontrack = null;
            peerConnection.onicecandidate = null;
            peerConnection.onconnectionstatechange = null;
            peerConnection.oniceconnectionstatechange = null;
            peerConnection.close();
        }
    } catch (e) {
        console.warn('[VideoCall] peerConnection close error:', e);
    }
    peerConnection = null;

    // stop local stream
    try {
        if (localStream) {
            localStream.getTracks().forEach((t) => t.stop());
        }
    } catch (e) {
        console.warn('[VideoCall] localStream stop error:', e);
    }
    localStream = null;

    // clear video elements
    const localVideo = $('localVideo');
    const remoteVideo = $('remoteVideo');
    if (localVideo) localVideo.srcObject = null;
    if (remoteVideo) remoteVideo.srcObject = null;

    // hide modal video
    hideModal('videoCallModal');

    // reset state
    callActive = false;
    pendingOffer = null;
    queuedCandidates = [];
    remoteUsername = null;
}

// ======================
// BIND EVENTS
// ======================
document.addEventListener('DOMContentLoaded', () => {
    const btnAccept = $('btn-accept-call');
    if (btnAccept) btnAccept.addEventListener('click', acceptCall);

    const btnReject = $('btn-reject-call');
    if (btnReject) btnReject.addEventListener('click', rejectCall);

    const btnEnd = $('btn-end-call');
    if (btnEnd) btnEnd.addEventListener('click', () => endCall(true));

    // Nếu user đóng modal video bằng nút X thì cũng endCall (tránh rò rỉ camera)
    const videoModalEl = $('videoCallModal');
    if (videoModalEl) {
        videoModalEl.addEventListener('hidden.bs.modal', () => {
            // Nếu modal bị đóng mà call vẫn active -> endCall
            if (callActive) endCall(true);
        });
    }
});

// ======================
// EXPOSE GLOBAL (để socket.js gọi được)
// ======================
window.handleVideoSignal = handleVideoSignal;
window.startVideoCall = startVideoCall;
window.acceptCall = acceptCall;
window.rejectCall = rejectCall;
window.endCall = endCall;
