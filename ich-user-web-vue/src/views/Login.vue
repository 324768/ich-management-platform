<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { login as loginApi, getUserInfo } from '@/api/user'
import { setToken, setUserInfo } from '@/utils/token'

const router = useRouter()
const captchaCanvas = ref(null)
const loading = ref(false)
const captchaCode = ref('')
const errorMsg = ref('')

const form = reactive({
  username: '',
  password: '',
  captcha: ''
})

const generateCaptcha = () => {
  const canvas = captchaCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
  let code = ''

  ctx.fillStyle = '#e8f0e8'
  ctx.fillRect(0, 0, 120, 40)

  for (let i = 0; i < 5; i++) {
    ctx.beginPath()
    ctx.moveTo(Math.random() * 120, Math.random() * 40)
    ctx.lineTo(Math.random() * 120, Math.random() * 40)
    ctx.strokeStyle = `rgba(${Math.random()*180},${Math.random()*180},${Math.random()*180},0.4)`
    ctx.lineWidth = 1
    ctx.stroke()
  }

  for (let i = 0; i < 4; i++) {
    const char = chars[Math.floor(Math.random() * chars.length)]
    code += char
    ctx.font = `${18 + Math.random() * 8}px serif`
    ctx.fillStyle = `rgb(${Math.random()*80},${Math.random()*80},${Math.random()*80})`
    ctx.save()
    ctx.translate(25 * i + 15, 28)
    ctx.rotate((Math.random() - 0.5) * 0.4)
    ctx.fillText(char, 0, 0)
    ctx.restore()
  }

  for (let i = 0; i < 40; i++) {
    ctx.fillStyle = `rgba(${Math.random()*255},${Math.random()*255},${Math.random()*255},0.3)`
    ctx.fillRect(Math.random() * 120, Math.random() * 40, 2, 2)
  }

  captchaCode.value = code.toLowerCase()
}

const handleLogin = async () => {
  errorMsg.value = ''
  if (!form.username || !form.password) {
    errorMsg.value = '请输入账号和密码'
    return
  }
  if (!form.captcha) {
    errorMsg.value = '请输入验证码'
    return
  }
  if (form.captcha.toLowerCase() !== captchaCode.value) {
    errorMsg.value = '验证码错误'
    generateCaptcha()
    form.captcha = ''
    return
  }

  loading.value = true
  try {
    const res = await loginApi({ username: form.username, password: form.password })
    const token = res.data
    setToken(token)

    // 解析token获取用户ID（JWT payload）
    let userId = null
    try {
      const payload = JSON.parse(atob(token.split('.')[1]))
      userId = payload.userId || payload.sub || payload.id
    } catch (e) {
      // token不是JWT格式，尝试直接作为userId使用
    }

    // 获取用户信息
    if (userId) {
      try {
        const userRes = await getUserInfo(userId)
        setUserInfo(userRes.data)
      } catch (e) {
        // 获取用户信息失败，用基本信息
        setUserInfo({ username: form.username, nickname: form.username, avatar: '' })
      }
    } else {
      setUserInfo({ username: form.username, nickname: form.username, avatar: '' })
    }

    loading.value = false
    router.push('/')
  } catch (error) {
    loading.value = false
    errorMsg.value = error.message || '登录失败，请检查账号密码'
    generateCaptcha()
    form.captcha = ''
  }
}

onMounted(() => {
  nextTick(() => { generateCaptcha() })
})
</script>

<template>
  <div class="login-page">
    <!-- Left Panel -->
    <div class="login-left">
      <div class="fan-container">
        <div class="fan-body"></div>
      </div>
      <div class="bg-chars">非遗</div>
      <div class="bg-chars bg-chars-2">传承</div>

      <div class="left-content">
        <p class="left-badge">非遗文化管理平台</p>
        <h1 class="left-title">非遗文化管理平台 <span class="flag">🇨🇳</span></h1>
        <p class="left-subtitle">传承千年匠心 · 守护文化瑰宝</p>

        <div class="features">
          <div class="feature-item">
            <span class="feature-icon">🎭</span>
            <div class="feature-text">
              <h3>戏曲艺术</h3>
              <p>传统戏曲艺术的保护与传承</p>
            </div>
          </div>
          <div class="feature-item">
            <span class="feature-icon">🎨</span>
            <div class="feature-text">
              <h3>工艺美术</h3>
              <p>匠人手工艺品的技艺传承</p>
            </div>
          </div>
          <div class="feature-item">
            <span class="feature-icon">🏺</span>
            <div class="feature-text">
              <h3>传统技艺</h3>
              <p>古老工艺技术的保护发展</p>
            </div>
          </div>
        </div>

        <div class="stats-row">
          <div class="stat-item">
            <span class="stat-number">1000+</span>
            <span class="stat-label">非遗项目</span>
          </div>
          <div class="stat-item">
            <span class="stat-number">500+</span>
            <span class="stat-label">传承人</span>
          </div>
          <div class="stat-item">
            <span class="stat-number">30+</span>
            <span class="stat-label">省市覆盖</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Right Panel -->
    <div class="login-right">
      <div class="login-form-container">
        <h2 class="form-title">欢迎登录 👋</h2>
        <p class="form-subtitle">非遗文化管理平台</p>

        <form @submit.prevent="handleLogin" class="login-form">
          <div class="form-group">
            <label class="form-label"><span class="req">*</span> 账号</label>
            <div class="input-box">
              <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
              <input v-model="form.username" type="text" placeholder="请输入账号" />
            </div>
          </div>

          <div class="form-group">
            <label class="form-label"><span class="req">*</span> 密码</label>
            <div class="input-box">
              <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
              <input v-model="form.password" type="password" placeholder="请输入密码" />
            </div>
          </div>

          <div class="form-group">
            <label class="form-label"><span class="req">*</span> 验证码</label>
            <div class="captcha-row">
              <div class="input-box captcha-input">
                <input v-model="form.captcha" type="text" placeholder="请输入验证码" />
              </div>
              <canvas ref="captchaCanvas" class="captcha-canvas"
                      width="120" height="40"
                      @click="generateCaptcha"
                      title="点击刷新验证码"></canvas>
            </div>
          </div>

          <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>

          <button type="submit" class="submit-btn" :disabled="loading">
            {{ loading ? '登录中...' : '登录系统' }}
          </button>

          <div class="form-footer">
            <span class="register-text">还没有账号？<router-link to="/register" class="register-link">立即注册</router-link></span>
            <a href="javascript:void(0)" class="forgot-link">忘记密码?</a>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  width: 100%;
}

/* ========== Left Panel ========== */
.login-left {
  position: relative;
  width: 50%;
  background: linear-gradient(160deg, #1a1a1e 0%, #2a1a1a 40%, #1e1a1a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 60px 50px;
}

.fan-container {
  position: absolute;
  top: 2%;
  right: -8%;
  width: 550px;
  height: 560px;
  opacity: 0.4;
  pointer-events: none;
}

.fan-body {
  width: 100%;
  height: 50%;
  background: repeating-conic-gradient(
    from 180deg at 50% 100%,
    rgba(210, 190, 170, 0.22) 0deg 1deg,
    transparent 1deg 5deg
  );
  border-radius: 280px 280px 0 0;
  position: relative;
  border: 1.5px solid rgba(210, 190, 170, 0.18);
  border-bottom: 3px solid rgba(210, 190, 170, 0.22);
}

.fan-body::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: radial-gradient(ellipse at 50% 100%, rgba(180, 150, 120, 0.12) 0%, transparent 70%);
  border-radius: inherit;
}

.fan-body::after {
  content: '';
  position: absolute;
  bottom: -65px;
  left: 50%;
  transform: translateX(-50%);
  width: 6px;
  height: 65px;
  background: linear-gradient(to bottom, rgba(210, 190, 170, 0.25), rgba(210, 190, 170, 0.05));
  border-radius: 0 0 3px 3px;
}

.bg-chars {
  position: absolute;
  top: 8%;
  left: 5%;
  font-size: 220px;
  font-family: var(--ich-font-serif);
  color: rgba(255, 255, 255, 0.015);
  pointer-events: none;
  user-select: none;
  line-height: 1;
  letter-spacing: 10px;
}

.bg-chars-2 {
  top: auto;
  bottom: 5%;
  left: auto;
  right: 5%;
  font-size: 180px;
}

.left-content {
  position: relative;
  z-index: 2;
  color: #fff;
  width: 100%;
  max-width: 460px;
}

.left-badge {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  margin-bottom: 16px;
  letter-spacing: 3px;
  text-transform: uppercase;
}

.left-title {
  font-size: 34px;
  font-weight: 700;
  margin-bottom: 12px;
  letter-spacing: 2px;
  line-height: 1.3;
  font-family: var(--ich-font-serif);
}

.flag { font-size: 28px; vertical-align: middle; }

.left-subtitle {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.55);
  margin-bottom: 48px;
  letter-spacing: 1px;
}

.features {
  display: flex;
  flex-direction: column;
  gap: 22px;
  margin-bottom: 56px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 16px;
}

.feature-icon {
  font-size: 26px;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.feature-text h3 {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 2px;
  color: #fff;
}

.feature-text p {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
}

.stats-row {
  display: flex;
  gap: 48px;
  padding-top: 36px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-number {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 1px;
}

.stat-label {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.45);
}

/* ========== Right Panel ========== */
.login-right {
  width: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  padding: 60px 50px;
}

.login-form-container {
  width: 100%;
  max-width: 400px;
}

.form-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--ich-text-primary);
  margin-bottom: 8px;
}

.form-subtitle {
  font-size: 14px;
  color: var(--ich-text-muted);
  margin-bottom: 36px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 14px;
  color: var(--ich-text-secondary);
  font-weight: 500;
}

.req {
  color: var(--ich-primary);
  margin-right: 2px;
}

.input-box {
  position: relative;
  display: flex;
  align-items: center;
  border: 1px solid var(--ich-border);
  border-radius: var(--ich-radius-md);
  background: var(--ich-bg-soft);
  transition: all 0.3s;
  padding: 0 14px;
  height: 46px;
}

.input-box:focus-within {
  border-color: var(--ich-primary);
  background: #fff;
  box-shadow: 0 0 0 3px rgba(139, 32, 32, 0.06);
}

.input-icon {
  width: 18px;
  height: 18px;
  color: var(--ich-text-muted);
  flex-shrink: 0;
  margin-right: 10px;
}

.input-box input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--ich-text-primary);
  height: 100%;
}

.input-box input::placeholder {
  color: #c0c0c0;
}

.captcha-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.captcha-input { flex: 1; }

.captcha-canvas {
  height: 46px;
  border-radius: var(--ich-radius-md);
  cursor: pointer;
  border: 1px solid var(--ich-border);
  flex-shrink: 0;
}

.error-msg {
  color: var(--ich-primary);
  font-size: 13px;
  text-align: center;
  margin: -8px 0 0;
}

.submit-btn {
  width: 100%;
  height: 48px;
  background: linear-gradient(135deg, #A63030, #8B2020);
  color: #fff;
  border: none;
  border-radius: 24px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  letter-spacing: 4px;
  margin-top: 4px;
}

.submit-btn:hover {
  background: linear-gradient(135deg, #B83838, #A02828);
  box-shadow: 0 6px 20px rgba(139, 32, 32, 0.35);
  transform: translateY(-1px);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.form-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--ich-text-muted);
}

.register-link {
  color: var(--ich-primary) !important;
  font-weight: 500;
}

.forgot-link {
  color: var(--ich-text-muted) !important;
  font-size: 13px;
}
.forgot-link:hover {
  color: var(--ich-primary) !important;
}

/* ========== Responsive ========== */
@media (max-width: 968px) {
  .login-page { flex-direction: column; }
  .login-left, .login-right { width: 100%; }
  .login-left {
    min-height: 360px;
    padding: 40px 30px;
  }
  .login-right { padding: 40px 30px; }
  .stats-row { gap: 32px; }
  .left-title { font-size: 26px; }
  .fan-container { width: 350px; height: 360px; }
}
</style>
