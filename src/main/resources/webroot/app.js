// ── Constants ────────────────────────────────────────────────────────────────

const API_MESSAGES   = '/api/messages';
const API_MESSAGE    = '/api/message';   // + /:id
const EVENTBUS_URL   = '/eventbus';
const EB_NEW_MSG     = 'ws.newMessage';
const EB_UPDATED_MSG = 'ws.updatedMessage';
const EB_DELETED_MSG = 'ws.deletedMessage';

// ── DOM references ───────────────────────────────────────────────────────────

const container   = document.getElementById('messages-container');
const form        = document.getElementById('send-form');
const usernameEl  = document.getElementById('username');
const contentEl   = document.getElementById('content');
const statusBadge = document.getElementById('status-badge');
const editModal   = document.getElementById('edit-modal');
const editContent = document.getElementById('edit-content');

// ── State ─────────────────────────────────────────────────────────────────────

let currentEditId = null;   // id of the message being edited

// ── Restore username ──────────────────────────────────────────────────────────

usernameEl.value = localStorage.getItem('hirzam_username') || '';

// ── Helpers ───────────────────────────────────────────────────────────────────

function escHtml(str) {
  return str
    .replace(/&/g,  '&amp;')
    .replace(/</g,  '&lt;')
    .replace(/>/g,  '&gt;')
    .replace(/"/g,  '&quot;')
    .replace(/'/g,  '&#39;');
}

function formatDate(iso) {
  const d = new Date(iso);
  return d.toLocaleDateString('fr-FR', {
    day: '2-digit', month: '2-digit', year: 'numeric'
  }) + ' ' + d.toLocaleTimeString('fr-FR', {
    hour: '2-digit', minute: '2-digit'
  });
}

function scrollBottom() {
  container.scrollTop = container.scrollHeight;
}

// ── Build a message element ───────────────────────────────────────────────────

function buildMessageEl(msg) {
  const el = document.createElement('div');
  el.className  = 'message';
  el.dataset.id = msg.id;

  // Badge shown when the message was edited
  const editedBadge = msg.updated
    ? '<span class="msg-badge">[edited]</span>'
    : '';

  el.innerHTML = `
    <div class="message-header">
      <span class="message-username">${escHtml(msg.username)}</span>
      <span class="message-time">${formatDate(msg.created_at)}</span>
      ${editedBadge}
      <span class="message-actions">
        <button class="btn-edit"  data-id="${msg.id}" title="Edit">✏</button>
        <button class="btn-delete" data-id="${msg.id}" title="Delete">✕</button>
      </span>
    </div>
    <div class="message-content">${escHtml(msg.content)}</div>
  `;

  // Attach button listeners directly on the element
  el.querySelector('.btn-edit')
    .addEventListener('click', () => openEditModal(msg.id, msg.content));

  el.querySelector('.btn-delete')
    .addEventListener('click', () => handleDelete(msg.id));

  return el;
}

// ── Replace an existing message element in the DOM ────────────────────────────

function replaceMessageEl(msg) {
  const existing = document.querySelector(`.message[data-id="${msg.id}"]`);
  if (existing) {
    existing.replaceWith(buildMessageEl(msg));
  }
}

// ── Remove a message element from the DOM ────────────────────────────────────

function removeMessageEl(id) {
  const existing = document.querySelector(`.message[data-id="${id}"]`);
  if (existing) existing.remove();
}

// ── Load initial messages ─────────────────────────────────────────────────────

async function loadMessages() {
  try {
    const res      = await fetch(API_MESSAGES);
    const messages = await res.json();
    container.innerHTML = '';
    messages.forEach(msg => container.appendChild(buildMessageEl(msg)));
    scrollBottom();
  } catch (err) {
    console.error('Failed to load messages:', err);
  }
}

loadMessages();

// ── Send a new message ────────────────────────────────────────────────────────

form.addEventListener('submit', async (e) => {
  e.preventDefault();

  const username = usernameEl.value.trim();
  const content  = contentEl.value.trim();
  if (!username || !content) return;

  localStorage.setItem('hirzam_username', username);

  const btn    = form.querySelector('button');
  btn.disabled = true;

  try {
    const res = await fetch(API_MESSAGES, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ username, content })
    });

    if (!res.ok) {
      const err = await res.json();
      alert('Erreur : ' + err.error);
      return;
    }

    contentEl.value = '';
    // Message will appear via SockJS broadcast
  } catch (err) {
    console.error('Failed to send message:', err);
    alert('Failed to send message.');
  } finally {
    btn.disabled = false;
    contentEl.focus();
  }
});

// ── Edit modal ────────────────────────────────────────────────────────────────

function openEditModal(id, currentContent) {
  currentEditId     = id;
  editContent.value = currentContent;
  editModal.classList.remove('hidden');
  editContent.focus();
}

function closeEditModal() {
  currentEditId = null;
  editModal.classList.add('hidden');
}

document.getElementById('btn-cancel-edit')
  .addEventListener('click', closeEditModal);

// Close modal when clicking the overlay outside the box
editModal.addEventListener('click', (e) => {
  if (e.target === editModal) closeEditModal();
});

document.getElementById('btn-confirm-edit')
  .addEventListener('click', async () => {
    const newContent = editContent.value.trim();
    if (!newContent || currentEditId === null) return;

    try {
      const res = await fetch(`${API_MESSAGE}/${currentEditId}`, {
        method:  'PUT',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({ content: newContent })
      });

      if (!res.ok) {
        const err = await res.json();
        alert('Erreur : ' + err.error);
        return;
      }

      closeEditModal();
      // UI update will come via ws.updatedMessage broadcast

    } catch (err) {
      console.error('Failed to update message:', err);
      alert('Failed to update message.');
    }
  });

// ── Delete ────────────────────────────────────────────────────────────────────

async function handleDelete(id) {
  if (!confirm('Delete this message ?')) return;

  try {
    const res = await fetch(`${API_MESSAGE}/${id}`, {
      method: 'DELETE'
    });

    if (!res.ok) {
      const err = await res.json();
      alert('Error : ' + err.error);
    }
    // UI update will come via ws.deletedMessage broadcast

  } catch (err) {
    console.error('Failed to delete message:', err);
    alert('Failed to delete message.');
  }
}

// ── SockJS / EventBus ─────────────────────────────────────────────────────────

function connectEventBus() {
  const eb = new EventBus(EVENTBUS_URL);

  eb.onopen = () => {
    statusBadge.textContent = 'Connected';
    statusBadge.className   = 'badge connected';

    // New message
    eb.registerHandler(EB_NEW_MSG, (err, msg) => {
      if (err) return;
      const data = JSON.parse(msg.body);
      if (document.querySelector(`.message[data-id="${data.id}"]`)) return;
      container.appendChild(buildMessageEl(data));
      scrollBottom();
    });

    // Message was edited
    eb.registerHandler(EB_UPDATED_MSG, (err, msg) => {
      if (err) return;
      const data = JSON.parse(msg.body);
      replaceMessageEl(data);
    });

    // Message was deleted
    eb.registerHandler(EB_DELETED_MSG, (err, msg) => {
      if (err) return;
      const data = JSON.parse(msg.body);
      removeMessageEl(data.id);
    });
  };

  eb.onclose = () => {
    statusBadge.textContent = 'Disconnected';
    statusBadge.className   = 'badge disconnected';
    setTimeout(connectEventBus, 3000);
  };
}

connectEventBus();