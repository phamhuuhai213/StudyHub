// socket.js
import { state, dom } from './state.js';
import { toggleInputState } from './utils.js';
import { onPresenceMessageReceived, onNotificationReceived, loadFriendList } from './friends.js';
import { loadChatRooms, checkUrlForRedirect, onRoomEventReceived } from './rooms.js';
import { showUnreadDot } from './messaging.js';

export function connect() {
    const client = window.stompClientGlobal;
    if (client && client.connected) {
        state.stompClient = client;
        window.stompClient = client;
        onConnected();
    } else {
        if (dom.connectionStatusEl) {
            dom.connectionStatusEl.style.display = 'block';
            dom.connectionStatusEl.className = "reconnecting";
            dom.connectionStatusEl.innerHTML = '<i class="fa-solid fa-wifi"></i> Đang kết nối...';
        }
        toggleInputState(false);
    }

    // Lắng nghe sự kiện kết nối của global
    document.addEventListener('wsConnected', function(e) {
        state.stompClient = e.detail;
        window.stompClient = e.detail;
        onConnected();
    });

    document.addEventListener('wsDisconnected', function(e) {
        onError(e.detail);
    });
}

async function onConnected() {
    console.log('Đã đồng bộ WebSocket Chat từ Global!');
    state.isConnected = true;

    if (dom.connectionStatusEl) dom.connectionStatusEl.style.display = 'none';

    toggleInputState(true);

    // Hủy các subscribe cũ của trang chat để tránh trùng lặp nhận tin nhắn
    if (state.chatPageSubscriptions) {
        state.chatPageSubscriptions.forEach(sub => {
            try { sub.unsubscribe(); } catch(e){}
        });
    }
    state.chatPageSubscriptions = [];

    // Subscribe
    state.chatPageSubscriptions.push(
        state.stompClient.subscribe('/topic/presence', onPresenceMessageReceived)
    );
    state.chatPageSubscriptions.push(
        state.stompClient.subscribe('/user/queue/notifications', onNotificationReceived)
    );
    state.chatPageSubscriptions.push(
        state.stompClient.subscribe('/user/queue/room-events', onRoomEventReceived)
    );

    state.chatPageSubscriptions.push(
        state.stompClient.subscribe('/user/queue/chat', function(payload) {
            try {
                const messageDto = JSON.parse(payload.body);
                const incomingRoomId = String(messageDto.roomId);
                const currentRoomId = state.currentRoomId ? String(state.currentRoomId) : null;
                if (currentRoomId !== incomingRoomId) {
                    showUnreadDot(incomingRoomId);
                }
            } catch (e) {
                console.error('Lỗi parse chat queue payload:', e);
            }
        })
    );

    // Load Data
    loadFriendList();
    loadChatRooms();

    try {
        const response = await fetch('/api/chat/online-users');
        const onlineUsernames = await response.json();
        onlineUsernames.forEach(username => state.presenceStatus.set(username, "ONLINE"));
    } catch (error) { console.error("Lỗi tải online-users:", error); }

    checkUrlForRedirect();
}

function onError(error) {
    console.error('WebSocket Chat bị ngắt:', error);
    state.isConnected = false;

    if (dom.connectionStatusEl) {
        dom.connectionStatusEl.style.display = 'block';
        dom.connectionStatusEl.className = "reconnecting";
        dom.connectionStatusEl.innerHTML = '<i class="fa-solid fa-wifi"></i> Mất kết nối. Đang thử lại...';
    }

    toggleInputState(false);
}