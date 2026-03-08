<script setup>
import { ref } from 'vue'

const posts = ref([
  { id: 1, title: '凯里 | 没有人能空手走出苗绣集市，太好逛了！', author: '文化爱好者', time: '2025.03.31 22:26', replies: 12, likes: 45, tag: '苗族刺绣' },
  { id: 2, title: '至美国粹——京剧之美不可方物', author: '戏曲迷', time: '2025.03.30 22:26', replies: 8, likes: 67, tag: '京剧' },
  { id: 3, title: '非遗文化 | 走进昆曲的世界', author: '江南水乡', time: '2025.03.29 22:25', replies: 15, likes: 89, tag: '昆曲' },
  { id: 4, title: '【中华非遗技艺——皮影戏】传承千年的光影艺术', author: '光影传承', time: '2025.03.29 22:25', replies: 21, likes: 102, tag: '皮影戏' },
  { id: 5, title: '皮影非遗：指尖上的艺术传承', author: '匠心手艺', time: '2025.03.28 22:25', replies: 6, likes: 34, tag: '皮影戏' },
  { id: 6, title: '古琴雅韵：聆听三千年的旋律', author: '琴韵悠悠', time: '2025.03.27 20:15', replies: 18, likes: 76, tag: '古琴' },
  { id: 7, title: '景德镇制瓷之旅：火与土的艺术', author: '瓷都行者', time: '2025.03.26 18:30', replies: 9, likes: 53, tag: '陶瓷' },
])
</script>

<template>
  <div class="forum-page">
    <div class="page-banner">
      <h1>非遗论坛</h1>
      <p>交流分享 · 传承文化</p>
    </div>

    <div class="container" style="padding: 28px 20px 40px;">
      <div class="forum-list">
        <div v-for="post in posts" :key="post.id" class="forum-item card">
          <div class="post-body">
            <div class="post-header">
              <span class="tag">{{ post.tag }}</span>
              <h3 class="post-title">{{ post.title }}</h3>
            </div>
            <div class="post-meta">
              <span>👤 {{ post.author }}</span>
              <span>🕐 {{ post.time }}</span>
              <span>💬 {{ post.replies }} 回复</span>
              <span>❤️ {{ post.likes }} 点赞</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.forum-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.forum-item {
  padding: 20px 24px;
  cursor: pointer;
  transition: var(--ich-transition);
}

.forum-item:hover {
  border-left: 3px solid var(--ich-primary);
}

.post-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.post-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ich-text-primary);
}

.post-meta {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: var(--ich-text-muted);
}
</style>
