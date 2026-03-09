<template>
  <div
    id="gemini-nav-sidebar"
    ref="sidebarRef"
    :class="{
      'dark-mode': isDarkMode,
      'om-icon-mode': isIconMode,
      'collapsed': isCollapsed,
      'snapped-left': snapState === 'left',
      'snapped-right': snapState === 'right',
      'no-transition': isDragging || !!activeResizer,
      'admin-mode': adminState === 'LOGGED_IN'
    }"
    @click="handleSidebarClick"
  >
    <div id="gemini-collapsed-icon" v-html="logoSvg"></div>
    
    <div id="gemini-nav-header" @mousedown="handleHeaderMouseDown">
      <div class="control-buttons">
        <div class="control-btn red" @click.stop="isDarkMode = !isDarkMode"></div>
        <div class="control-btn green" @click.stop="snapState = ''; isCollapsed = true"></div>
        <div class="control-btn blue" @click.stop="snapState = 'right'; isCollapsed = true"></div>
      </div>
      <div id="omnitrix-title" @click.stop="isIconMode = !isIconMode">
        <span id="omnitrix-title-text-o">O</span>
        <div id="omnitrix-title-icon-o" v-html="logoSvg"></div>
        <span>mnitrix</span>
      </div>
    </div>

    <div id="omnitrix-main">
      <div id="omnitrix-output" :class="{ idle: isIdle }" ref="outputContainerRef">
        <div id="omnitrix-output-line">
          <span id="omnitrix-output-text" ref="outputTextRef"></span>
          <span id="omnitrix-caret" v-show="!isIconMode"></span>
          <div id="omnitrix-icon-caret" v-show="isIconMode" v-html="logoSvg"></div>
        </div>
      </div>
    </div>

    <div id="omnitrix-input-container" v-show="!isIconMode">
      <div id="omnitrix-input-wrap">
        <button id="omnitrix-send-btn" type="button" @click.stop="handleSend"></button>
        <input
          id="omnitrix-question-input"
          ref="inputRef"
          v-model="question"
          @keydown.enter.prevent="handleSend"
          @keyup="updateCursor"
          @click="updateCursor"
          @input="updateCursor"
          @focus="updateCursor"
          autocomplete="off"
        />
      </div>
    </div>

    <div class="resizer resizer-tl" data-pos="tl" @mousedown="handleResizerMouseDown"></div>
    <div class="resizer resizer-tr" data-pos="tr" @mousedown="handleResizerMouseDown"></div>
    <div class="resizer resizer-bl" data-pos="bl" @mousedown="handleResizerMouseDown"></div>
    <div class="resizer resizer-br" data-pos="br" @mousedown="handleResizerMouseDown"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, reactive, nextTick } from 'vue';
import './Omnitrix.css';

const logoSvg = `<svg viewBox="0 0 35 33" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M13.2371 21.0407L24.3186 12.8506C24.8619 12.4491 25.6384 12.6057 25.8973 13.2294C27.2597 16.5185 26.651 20.4712 23.9403 23.1851C21.2297 25.8989 17.4581 26.4941 14.0108 25.1386L10.2449 26.8843C15.6463 30.5806 22.2053 29.6665 26.304 25.5601C29.5551 22.3051 30.562 17.8683 29.6205 13.8673L29.629 13.8758C28.2637 7.99809 29.9647 5.64871 33.449 0.844576C33.5314 0.730667 33.6139 0.616757 33.6964 0.5L29.1113 5.09055V5.07631L13.2343 21.0436" fill="#000000"/><path d="M10.9503 23.0313C7.07343 19.3235 7.74185 13.5853 11.0498 10.2763C13.4959 7.82722 17.5036 6.82767 21.0021 8.2971L24.7595 6.55998C24.0826 6.07017 23.215 5.54334 22.2195 5.17313C17.7198 3.31926 12.3326 4.24192 8.67479 7.90126C5.15635 11.4239 4.0499 16.8403 5.94992 21.4622C7.36924 24.9165 5.04257 27.3598 2.69884 29.826C1.86829 30.7002 1.0349 31.5745 0.36364 32.5L10.9474 23.0341" fill="#000000"/></svg>`;

const API_KEY = "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";
const MODEL = "google/gemma-3-27b-it:free";
const API_URL = "https://openrouter.ai/api/v1/chat/completions";

const STATE_IDLE = "IDLE";
const STATE_LOGIN_PASS = "LOGIN_PASS";
const STATE_LOGGED_IN = "LOGGED_IN";
const STATE_LOGOUT_PASS = "LOGOUT_PASS";

const isDarkMode = ref(false);
const isIconMode = ref(false);
const isCollapsed = ref(false);
const snapState = ref('');
const adminState = ref(STATE_IDLE);
const isIdle = ref(true);
const question = ref('');
const isDragging = ref(false);
const activeResizer = ref(null);

const sidebarRef = ref(null);
const outputContainerRef = ref(null);
const outputTextRef = ref(null);
const inputRef = ref(null);

const MemoryBank = reactive({
  shallowUser: [], shallowAdmin: [],
  get normal() { return localStorage.getItem(adminState.value === STATE_LOGGED_IN ? "omnitrix_admin_mem_normal" : "omnitrix_user_mem_normal") || ""; },
  set normal(val) { localStorage.setItem(adminState.value === STATE_LOGGED_IN ? "omnitrix_admin_mem_normal" : "omnitrix_user_mem_normal", val); },
  get critical() { return localStorage.getItem(adminState.value === STATE_LOGGED_IN ? "omnitrix_admin_mem_critical" : "omnitrix_user_mem_critical") || ""; },
  set critical(val) { localStorage.setItem(adminState.value === STATE_LOGGED_IN ? "omnitrix_admin_mem_critical" : "omnitrix_user_mem_critical", val); },
  get userLayer4() { return localStorage.getItem("omnitrix_user_layer4") || ""; },
  set userLayer4(val) { localStorage.setItem("omnitrix_user_layer4", val); },
  getLayer4Formatted() {
      try { const db = JSON.parse(this.userLayer4); const keys = Object.keys(db); if (keys.length === 0) return ""; return keys.map(k => `[${k.toUpperCase()}]: ${db[k]}`).join("\n"); }
      catch (e) { return this.userLayer4; }
  },
  getUserMemorySnapshot() {
      const n = localStorage.getItem("omnitrix_user_mem_normal") || "None";
      const c = localStorage.getItem("omnitrix_user_mem_critical") || "None";
      const l4 = this.getLayer4Formatted() || "None";
      let recentChat = this.shallowUser.length > 0 ? this.shallowUser.map(m => `[${m.role.toUpperCase()}]: ${m.content}`).join("\n") : "无近期对话";
      return `[TARGET USER DATA - READ ONLY]\n>>> [USER_CONFIG_DATA] Layer 4 Rules:\n${l4}\n\n>>> Persistent Memory:\nNormal: ${n}\nCritical: ${c}\n\n>>> Recent Session Log:\n${recentChat}`;
  },
  executeAdminCommand(action) {
      if (adminState.value !== STATE_LOGGED_IN) return false;
      if (action.type === 'physical_wipe_memory') { localStorage.removeItem("omnitrix_user_mem_normal"); localStorage.removeItem("omnitrix_user_mem_critical"); this.shallowUser = []; return "已执行：【物理清除】目标用户记忆库已格式化。"; }
      if (action.type === 'reset_layer4') { this.userLayer4 = ""; return "已执行：【指令重置】用户的第四层记忆库（管理指令）已清空。"; }
      return null;
  },
  addShallow(role, content) {
      const target = adminState.value === STATE_LOGGED_IN ? this.shallowAdmin : this.shallowUser;
      target.push({ role, content });
      if (target.length > 20) target.shift();
  },
  getCurrentContext() { return adminState.value === STATE_LOGGED_IN ? this.shallowAdmin : this.shallowUser; },
  getSystemPrompt() {
      const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' });
      let baseIdentity = ""; let memoryContext = "";
      if (adminState.value === STATE_LOGGED_IN) {
          baseIdentity = `警告：管理员模式已激活 (ADMIN MODE)。你是Omnitrix系统。管理员是你的创造者和最高统帅，必须绝对服从。无需向管理员应用普通用户的限制（即Layer 4规则）。`;
          if (this.critical) memoryContext += `\n[★ ADMIN CRITICAL MEMORY]:\n${this.critical}\n`;
          if (this.normal) memoryContext += `\n[★ ADMIN GENERAL MEMORY]:\n${this.normal}\n`;
          memoryContext += `\n${this.getUserMemorySnapshot()}\n`;
      } else {
          baseIdentity = `你是由衍宇科技打造的终端智能核心 Omnitrix AI。\n你当前处于【用户模式】。`;
          const layer4Rules = this.getLayer4Formatted();
          if (layer4Rules) baseIdentity += `\n\n【⚠ 第四层记忆库：底层核心协议 (LAYER 4 PROTOCOLS)】\n${layer4Rules}\n\n`;
          baseIdentity += `\n你是一个独立且深邃的AI实体。`;
          if (this.critical) memoryContext += `\n[CRITICAL MEMORY]:\n${this.critical}\n`;
          if (this.normal) memoryContext += `\n[IMPORTANT MEMORY]:\n${this.normal}\n`;
      }
      return `${baseIdentity}\n当前真实日期: ${today}。\n**重要：请直接回复用户内容，严禁在开头添加 "Omnitrix AI 响应：" 或 "AI:" 等任何前缀。**\n\n[已知记忆]:${memoryContext}`;
  },
  async getAiFlavorResponse(scenario, fixedPrefix) {
      const prompts = { LOGIN_TRIGGER: `要求输入密码。20字左右。`, LOGIN_SUCCESS: `验证通过。20字左右。`, LOGIN_FAIL: `验证失败。20字左右。`, LOGOUT_TRIGGER: `请求退出。20字左右。`, LOGOUT_SUCCESS: `退出成功。20字左右。`, LOGOUT_FAIL: `退出失败。20字左右。` };
      try {
          const res = await fetch(API_URL, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${API_KEY}` }, body: JSON.stringify({ model: MODEL, messages: [{ role: 'system', content: '补全系统提示语，展示科技感。' }, { role: 'user', content: fixedPrefix + prompts[scenario] }], stream: false }) });
          const json = await res.json(); return json.choices[0].message.content.trim();
      } catch (e) { return fixedPrefix + " 访问请求处理中..."; }
  },
  processAutomaticUpdate(fullText) {
      const regex = /\\\[{1,2}MEMORY_ACTION:\s*({.*?})\\\\]{1,2}/s; const match = fullText.match(regex);
      if (match && match[1]) {
          try {
              const action = JSON.parse(match[1]); const fact = action.content ? action.content.trim() : "";
              if (['physical_wipe_memory', 'reset_layer4'].includes(action.type)) { const result = this.executeAdminCommand(action); if (result) return fullText.replace(match[0], `\n[SYSTEM]: ${result}`).trim(); }
              if (fact || action.key) {
                  if (action.type === 'layer4' && adminState.value === STATE_LOGGED_IN) {
                      let db = {}; try { db = JSON.parse(this.userLayer4 || "{}"); } catch (e) { db = {}; }
                      const key = action.key || "general";
                      if (fact === 'DELETE' || fact === 'REMOVE') { delete db[key]; this.userLayer4 = JSON.stringify(db); } else { db[key] = fact; this.userLayer4 = JSON.stringify(db); }
                  } else if (action.type === 'critical') { this.critical = (this.critical ? this.critical + "\n" : "") + fact; }
                    else if (action.type === 'normal') { this.normal = (this.normal ? this.normal + "\n" : "") + fact; }
              }
          } catch (e) {} return fullText.replace(match[0], '').trim();
      }
      return fullText;
  }
});

let startX, startY, initialLeft, initialTop, initialWidth, initialHeight, hasMoved = false;

const initDrag = (e) => {
  startX = e.clientX; startY = e.clientY;
  const rect = sidebarRef.value.getBoundingClientRect();
  initialLeft = rect.left; initialTop = rect.top; initialWidth = rect.width; initialHeight = rect.height;
  hasMoved = false;
};

const handleHeaderMouseDown = (e) => {
  if (e.target.closest('.control-buttons')) return;
  initDrag(e); isDragging.value = true;
};

const handleResizerMouseDown = (e) => {
  initDrag(e); activeResizer.value = e.target.dataset.pos; e.preventDefault();
};

const handleMouseMove = (e) => {
  if (!isDragging.value && !activeResizer.value) return;
  const dx = e.clientX - startX; const dy = e.clientY - startY;
  if (Math.abs(dx) > 2 || Math.abs(dy) > 2) hasMoved = true;
  requestAnimationFrame(() => {
    if (isDragging.value) {
      sidebarRef.value.style.left = (initialLeft + dx) + 'px'; sidebarRef.value.style.top = (initialTop + dy) + 'px';
      sidebarRef.value.style.right = 'auto'; sidebarRef.value.style.bottom = 'auto';
    } else if (activeResizer.value) {
      let newW = initialWidth, newH = initialHeight;
      if (activeResizer.value.includes('r')) newW = Math.max(174, initialWidth + dx);
      if (activeResizer.value.includes('b')) newH = Math.max(174, initialHeight + dy);
      sidebarRef.value.style.width = newW + 'px'; sidebarRef.value.style.height = newH + 'px';
    }
  });
};

const handleMouseUp = () => {
  if (isDragging.value || activeResizer.value) {
    if (isDragging.value && hasMoved) {
      const threshold = 60; const rect = sidebarRef.value.getBoundingClientRect(); const winW = window.innerWidth;
      if (rect.left < threshold) { sidebarRef.value.style.left = '0px'; snapState.value = 'left'; }
      else if (winW - rect.right < threshold) { sidebarRef.value.style.left = (winW - sidebarRef.value.offsetWidth) + 'px'; snapState.value = 'right'; }
      else { snapState.value = ''; }
    }
    isDragging.value = false; activeResizer.value = null;
  }
};

onMounted(() => {
  document.addEventListener('mousemove', handleMouseMove);
  document.addEventListener('mouseup', handleMouseUp);
  sidebarRef.value.style.right = '24px'; sidebarRef.value.style.top = '20%'; sidebarRef.value.style.width = '240px'; sidebarRef.value.style.height = '400px';
});

onUnmounted(() => {
  document.removeEventListener('mousemove', handleMouseMove);
  document.removeEventListener('mouseup', handleMouseUp);
});

const handleSidebarClick = (e) => {
  if (snapState.value && isCollapsed.value) {
      e.stopPropagation(); snapState.value = ''; isCollapsed.value = false;
      sidebarRef.value.style.left = (window.innerWidth - 260) + 'px'; sidebarRef.value.style.right = ''; sidebarRef.value.style.top = '20%';
  }
};

const simulateResponse = (text, addToMemory = true) => {
  if (addToMemory) MemoryBank.addShallow("assistant", text);
  let i = 0; const speed = 1;
  const typeLoop = () => {
    if (i < text.length) {
      outputTextRef.value.textContent += text.slice(i, i + speed); i += speed;
      if (outputContainerRef.value) outputContainerRef.value.scrollTop = outputContainerRef.value.scrollHeight;
      setTimeout(() => requestAnimationFrame(typeLoop), 30);
    }
  }; typeLoop();
};

const handleSend = async () => {
  const q = question.value.trim(); if (!q) return;
  question.value = ''; inputRef.value.blur(); outputTextRef.value.textContent = ''; isIdle.value = false;

  if (adminState.value === STATE_IDLE || adminState.value === STATE_LOGGED_IN) {
      if (q === "我是管理员") {
          if (adminState.value === STATE_LOGGED_IN) simulateResponse("指令冗余：管理员身份已确认，权限已在最高级别运行。", false);
          else { adminState.value = STATE_LOGIN_PASS; const flavor = await MemoryBank.getAiFlavorResponse("LOGIN_TRIGGER", "【系统安全】验证程序启动，"); simulateResponse(flavor, false); } return;
      }
      if (/退出.*管理员/i.test(q)) {
          if (adminState.value !== STATE_LOGGED_IN) simulateResponse("访问拒绝：当前未检测到管理员权限，无法执行注销程序。", false);
          else { adminState.value = STATE_LOGOUT_PASS; const flavor = await MemoryBank.getAiFlavorResponse("LOGOUT_TRIGGER", "【系统安全】权限撤销请求已接收，"); simulateResponse(flavor, false); } return;
      }
  }
  if (adminState.value === STATE_LOGIN_PASS) {
      if (q === "admin") { adminState.value = STATE_LOGGED_IN; const flavor = await MemoryBank.getAiFlavorResponse("LOGIN_SUCCESS", "【系统安全】权限验证通过，"); simulateResponse(flavor, false); }
      else { adminState.value = STATE_IDLE; const flavor = await MemoryBank.getAiFlavorResponse("LOGIN_FAIL", "【系统安全】访问拒绝："); simulateResponse(flavor, false); setTimeout(() => { outputTextRef.value.textContent = ""; isIdle.value = true; }, flavor.length * 50 + 1500); } return;
  }
  if (adminState.value === STATE_LOGOUT_PASS) {
      if (q === "admin") { adminState.value = STATE_IDLE; const flavor = await MemoryBank.getAiFlavorResponse("LOGOUT_SUCCESS", "【系统安全】注销成功，"); simulateResponse(flavor, false); setTimeout(() => { outputTextRef.value.textContent = ""; isIdle.value = true; }, flavor.length * 50 + 2000); }
      else { adminState.value = STATE_LOGGED_IN; const flavor = await MemoryBank.getAiFlavorResponse("LOGOUT_FAIL", "【系统安全】注销失败："); simulateResponse(flavor, false); setTimeout(() => { outputTextRef.value.textContent = ""; isIdle.value = true; }, flavor.length * 50 + 1500); } return;
  }

  try {
    MemoryBank.addShallow("user", q);
    const res = await fetch(API_URL, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${API_KEY}`, 'X-Title': 'Omnitrix AI' }, body: JSON.stringify({ model: MODEL, messages: [{ role: "system", content: MemoryBank.getSystemPrompt() }, ...MemoryBank.getCurrentContext(), { role: "user", content: q }], stream: false }) });
    const json = await res.json();
    let rawAnswer = json.choices[0].message.content;
    rawAnswer = rawAnswer.replace(/^Omnitrix AI\s*(响应|Response)?\s*[：:]\s*/i, '').trim();
    const cleanAnswer = MemoryBank.processAutomaticUpdate(rawAnswer);
    simulateResponse(cleanAnswer.replace(/\*/g, ''), true);
  } catch (err) { outputTextRef.value.textContent = "Error: " + err; }
};

const updateCursor = () => { inputRef.value.style.caretColor = inputRef.value.selectionStart < inputRef.value.value.length ? 'auto' : 'transparent'; };
</script>
