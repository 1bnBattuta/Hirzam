// ── Constants ────────────────────────────────────────────────────────────────

const API_URL       = '/api/messages';
const EVENTBUS_URL  = '/eventbus';
const EB_NEW_MSG    = 'ws.newMessage';

// ── DOM references ───────────────────────────────────────────────────────────

const container   = document.getElementById('messages-container');
const form        = document.getElementById('send-form');
const usernameEl  = document.getElementById('username');
const contentEl   = document.getElementById('content');
const statusBadge = document.getElementById('status-badge');

// ── Restore username from localStorage ───────────────────────────────────────

usernameEl.value = localStorage.getItem('hirzam_username') || '';

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Escapes HTML special characters to prevent XSS.
 * Never inject user content directly into innerHTML without this.
 */
function escHtml(str) {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Formats an ISO datetime string into a readable string.
 * Example: "2026-02-14T10:30:00" → "14/02/2026 10:30"
 */
function formatDate(iso) {
  const d = new Date(iso);
  return d.toLocaleDateString('fr-FR', {
    day:   '2-digit',
    month: '2-digit',
    year:  'numeric'
  }) + ' ' + d.toLocaleTimeString('fr-FR', {
    hour:   '2-digit',
    minute: '2-digit'
  });
}

/**
 * Builds a DOM element for a single message.
 */
function buildMessageEl(msg) {
  const el = document.createElement('div');
  el.className  = 'message';
  el.dataset.id = msg.id;

  el.innerHTML = `
    <div class="message-header">
      <span class="message-username">${escHtml(msg.username)}</span>
      <span class="message-time">${formatDate(msg.created_at)}</span>
    </div>
    <div class="message-content">${escHtml(msg.content)}</div>
  `;

  return el;
}

/**
 * Scrolls the message container to the bottom.
 */
function scrollBottom() {
  container.scrollTop = container.scrollHeight;
}

// ── Load initial messages ────────────────────────────────────────────────────

async function loadMessages() {
  try {
    const res      = await fetch(API_URL);
    const messages = await res.json();

    container.innerHTML = '';
    messages.forEach(msg => container.appendChild(buildMessageEl(msg)));
    scrollBottom();
  } catch (err) {
    console.error('Failed to load messages:', err);
  }
}

loadMessages();

// ── Send a message ───────────────────────────────────────────────────────────

form.addEventListener('submit', async (e) => {
  e.preventDefault();

  const username = usernameEl.value.trim();
  const content  = contentEl.value.trim();

  if (!username || !content) return;

  // Persist username so the user doesn't have to retype it
  localStorage.setItem('hirzam_username', username);

  const btn     = form.querySelector('button');
  btn.disabled  = true;

  try {
    const res = await fetch(API_URL, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ username, content })
    });

    if (!res.ok) {
      const err = await res.json();
      alert('Erreur : ' + err.error);
      return;
    }

    // Clear the content field, keep the username
    contentEl.value = '';

    // The message will appear via SockJS broadcast,
    // not from the POST response — avoids duplicates

  } catch (err) {
    console.error('Failed to send message:', err);
    alert('Impossible d\'envoyer le message.');
  } finally {
    btn.disabled = false;
    contentEl.focus();
  }
});

// ── SockJS / EventBus connection ─────────────────────────────────────────────

function connectEventBus() {
  const eb = new EventBus(EVENTBUS_URL);

  eb.onopen = () => {
    statusBadge.textContent = 'Connected';
    statusBadge.className   = 'badge connected';

    // Listen for new messages broadcast by the server
    eb.registerHandler(EB_NEW_MSG, (err, msg) => {
      if (err) {
        console.error('EventBus error:', err);
        return;
      }

      // msg.body is a JSON string sent by DatabaseVerticle via HttpVerticle
      const data = JSON.parse(msg.body);

      // Avoid duplicates: the sender also receives the broadcast
      if (document.querySelector(`.message[data-id="${data.id}"]`)) return;

      container.appendChild(buildMessageEl(data));
      scrollBottom();
    });
  };

  eb.onclose = () => {
    statusBadge.textContent = 'Disconnected';
    statusBadge.className   = 'badge disconnected';

    // Reconnect automatically after 3 seconds
    setTimeout(connectEventBus, 3000);
  };
}

connectEventBus();