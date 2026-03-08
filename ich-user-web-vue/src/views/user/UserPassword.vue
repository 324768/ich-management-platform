<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { getUserInfo } from '@/utils/token'
import { updatePassword } from '@/api/user'

const router = useRouter()
const user = getUserInfo()
const form = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const saving = ref(false)
const showOld = ref(false)
const showNew = ref(false)
const showConfirm = ref(false)

const handleSubmit = async () => {
  if (!form.value.oldPassword) { alert('请输入原密码'); return }
  if (!form.value.newPassword) { alert('请输入新密码'); return }
  if (form.value.newPassword.length < 6) { alert('新密码至少6位'); return }
  if (form.value.newPassword !== form.value.confirmPassword) { alert('两次密码不一致'); return }
  saving.value = true
  try {
    await updatePassword(user.id, form.value.oldPassword, form.value.newPassword)
    alert('密码修改成功，请重新登录')
    localStorage.removeItem('ich_user_token')
    localStorage.removeItem('ich_user')
    router.push('/login')
  } catch (e) {
    alert('修改失败: ' + (e.response?.data?.message || e.message))
  }
  saving.value = false
}
</script>

<template>
  <div class="page-container">
    <div class="container">
      <div class="page-header">
        <button class="back-btn" @click="router.push('/user')">← 返回</button>
        <h2>修改密码</h2>
      </div>

      <div class="pwd-card">
        <div class="form-group">
          <label>原密码</label>
          <div class="input-wrap">
            <input :type="showOld ? 'text' : 'password'" v-model="form.oldPassword" placeholder="请输入原密码" />
            <span class="toggle-eye" @click="showOld = !showOld">{{ showOld ? '🙈' : '👁️' }}</span>
          </div>
        </div>
        <div class="form-group">
          <label>新密码</label>
          <div class="input-wrap">
            <input :type="showNew ? 'text' : 'password'" v-model="form.newPassword" placeholder="请输入新密码（至少6位）" />
            <span class="toggle-eye" @click="showNew = !showNew">{{ showNew ? '🙈' : '👁️' }}</span>
          </div>
        </div>
        <div class="form-group">
          <label>确认新密码</label>
          <div class="input-wrap">
            <input :type="showConfirm ? 'text' : 'password'" v-model="form.confirmPassword" placeholder="请再次输入新密码"
                   @keyup.enter="handleSubmit" />
            <span class="toggle-eye" @click="showConfirm = !showConfirm">{{ showConfirm ? '🙈' : '👁️' }}</span>
          </div>
        </div>
        <button class="submit-btn" :disabled="saving" @click="handleSubmit">
          {{ saving ? '提交中...' : '确认修改' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container { padding: 24px 0 60px; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
.page-header h2 { flex: 1; font-size: 20px; font-weight: 700; margin: 0; }
.back-btn { background: none; border: none; font-size: 15px; color: var(--ich-primary, #8B2020); cursor: pointer; }

.pwd-card {
  max-width: 420px; margin: 0 auto; padding: 36px 32px;
  background: #fff; border-radius: 16px; box-shadow: 0 2px 12px rgba(0,0,0,0.06);
}
.form-group { margin-bottom: 24px; }
.form-group label { display: block; font-size: 14px; font-weight: 500; color: #333; margin-bottom: 8px; }
.input-wrap { position: relative; }
.input-wrap input {
  width: 100%; padding: 12px 40px 12px 16px; border: 1px solid #e8e8e8;
  border-radius: 10px; font-size: 14px; box-sizing: border-box; transition: 0.2s;
}
.input-wrap input:focus { outline: none; border-color: var(--ich-primary, #8B2020); box-shadow: 0 0 0 3px rgba(139,32,32,0.08); }
.toggle-eye {
  position: absolute; right: 12px; top: 50%; transform: translateY(-50%);
  cursor: pointer; font-size: 16px; user-select: none;
}
.submit-btn {
  width: 100%; padding: 12px; background: var(--ich-primary, #8B2020); color: #fff;
  border: none; border-radius: 10px; font-size: 16px; font-weight: 600; cursor: pointer; transition: 0.2s;
}
.submit-btn:hover:not(:disabled) { opacity: 0.9; }
.submit-btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
