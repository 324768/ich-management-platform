<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { register as registerApi } from '@/api/user'

const router = useRouter()
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
  phone: '',
})

const handleRegister = async () => {
  errorMsg.value = ''
  if (!form.username || !form.password || !form.nickname) {
    errorMsg.value = '请填写必要信息'
    return
  }
  if (form.password !== form.confirmPassword) {
    errorMsg.value = '两次密码不一致'
    return
  }
  loading.value = true
  try {
    await registerApi({
      username: form.username,
      password: form.password,
      nickname: form.nickname,
      phone: form.phone || null,
      email: null,
    })
    loading.value = false
    alert('注册成功，请登录')
    router.push('/login')
  } catch (error) {
    loading.value = false
    errorMsg.value = error.message || '注册失败，请重试'
  }
}
</script>

<template>
  <div class="register-page">
    <div class="register-left">
      <div class="fan-container"><div class="fan-body"></div></div>
      <div class="bg-chars">注册</div>
      <div class="left-content">
        <h1 class="left-title">加入我们 🎊</h1>
        <p class="left-subtitle">探索中国非物质文化遗产的无穷魅力</p>
        <div class="features">
          <div class="feature-item"><span class="feature-icon">📜</span><span>浏览1000+非遗项目</span></div>
          <div class="feature-item"><span class="feature-icon">📚</span><span>学习非遗课程</span></div>
          <div class="feature-item"><span class="feature-icon">🛒</span><span>购买文创产品</span></div>
          <div class="feature-item"><span class="feature-icon">💬</span><span>参与社区交流</span></div>
        </div>
      </div>
    </div>
    <div class="register-right">
      <div class="form-container">
        <h2 class="form-title">用户注册</h2>
        <p class="form-subtitle">创建您的非遗平台账号</p>
        <form @submit.prevent="handleRegister" class="reg-form">
          <div class="form-group">
            <label><span class="req">*</span> 用户名</label>
            <input v-model="form.username" placeholder="请输入用户名" />
          </div>
          <div class="form-group">
            <label><span class="req">*</span> 昵称</label>
            <input v-model="form.nickname" placeholder="请输入昵称" />
          </div>
          <div class="form-group">
            <label><span class="req">*</span> 密码</label>
            <input v-model="form.password" type="password" placeholder="请输入密码" />
          </div>
          <div class="form-group">
            <label><span class="req">*</span> 确认密码</label>
            <input v-model="form.confirmPassword" type="password" placeholder="请再次输入密码" />
          </div>
          <div class="form-group">
            <label>手机号</label>
            <input v-model="form.phone" placeholder="请输入手机号（选填）" />
          </div>
          <button type="submit" class="submit-btn" :disabled="loading">
            {{ loading ? '注册中...' : '立即注册' }}
          </button>
          <div class="form-footer">
            <span>已有账号？<router-link to="/login" class="login-link">去登录</router-link></span>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-page { display: flex; min-height: 100vh; width: 100%; }

.register-left {
  position: relative; width: 45%; overflow: hidden;
  background: linear-gradient(160deg, #1a1a1e 0%, #2a1a1a 40%, #1e1a1a 100%);
  display: flex; align-items: center; justify-content: center; padding: 60px 50px;
}
.fan-container { position: absolute; top: 2%; right: -8%; width: 450px; height: 460px; opacity: 0.35; pointer-events: none; }
.fan-body {
  width: 100%; height: 50%;
  background: repeating-conic-gradient(from 180deg at 50% 100%, rgba(210,190,170,0.22) 0deg 1deg, transparent 1deg 5deg);
  border-radius: 230px 230px 0 0;
  border: 1.5px solid rgba(210,190,170,0.18); border-bottom: 3px solid rgba(210,190,170,0.22);
}
.bg-chars {
  position: absolute; top: 10%; left: 5%; font-size: 180px;
  font-family: var(--ich-font-serif); color: rgba(255,255,255,0.015);
  pointer-events: none; user-select: none;
}
.left-content { position: relative; z-index: 2; color: #fff; max-width: 380px; }
.left-title { font-size: 30px; font-weight: 700; margin-bottom: 12px; font-family: var(--ich-font-serif); }
.left-subtitle { font-size: 14px; color: rgba(255,255,255,0.55); margin-bottom: 40px; }
.features { display: flex; flex-direction: column; gap: 18px; }
.feature-item { display: flex; align-items: center; gap: 14px; font-size: 15px; color: rgba(255,255,255,0.75); }
.feature-icon { font-size: 22px; width: 40px; height: 40px; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,0.06); border-radius: 10px; }

.register-right {
  width: 55%; display: flex; align-items: center; justify-content: center;
  background: #fff; padding: 40px 50px;
}
.form-container { width: 100%; max-width: 440px; }
.form-title { font-size: 26px; font-weight: 700; color: var(--ich-text-primary); margin-bottom: 6px; }
.form-subtitle { font-size: 14px; color: var(--ich-text-muted); margin-bottom: 28px; }
.reg-form { display: flex; flex-direction: column; gap: 18px; }
.form-group { display: flex; flex-direction: column; gap: 6px; }
.form-group label { font-size: 14px; color: var(--ich-text-secondary); font-weight: 500; }
.req { color: var(--ich-primary); margin-right: 2px; }
.form-group input {
  height: 44px; border: 1px solid var(--ich-border); border-radius: var(--ich-radius-md);
  padding: 0 14px; font-size: 14px; background: var(--ich-bg-soft);
  outline: none; transition: all 0.3s;
}
.form-group input:focus { border-color: var(--ich-primary); background: #fff; box-shadow: 0 0 0 3px rgba(139,32,32,0.06); }
.submit-btn {
  width: 100%; height: 48px; margin-top: 4px;
  background: linear-gradient(135deg, #A63030, #8B2020); color: #fff;
  border: none; border-radius: 24px; font-size: 16px; font-weight: 600;
  cursor: pointer; transition: all 0.3s; letter-spacing: 4px;
}
.submit-btn:hover { background: linear-gradient(135deg, #B83838, #A02828); box-shadow: 0 6px 20px rgba(139,32,32,0.35); transform: translateY(-1px); }
.submit-btn:disabled { opacity: 0.6; cursor: not-allowed; transform: none; }
.form-footer { text-align: center; font-size: 13px; color: var(--ich-text-muted); }
.login-link { color: var(--ich-primary) !important; font-weight: 500; }

@media (max-width: 768px) {
  .register-page { flex-direction: column; }
  .register-left, .register-right { width: 100%; }
  .register-left { min-height: 260px; padding: 30px; }
  .register-right { padding: 30px; }
}
</style>
