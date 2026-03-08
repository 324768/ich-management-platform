<script setup>
import { ref } from 'vue'

const activeCategory = ref('全部')
const searchText = ref('')
const categories = ['全部', '民俗节庆', '传统医药', '民间舞蹈', '戏曲曲艺', '传统手工艺']

const courses = ref([
  { id: 1, name: '少林功夫入门精修班', category: '民俗节庆', hours: 4, students: 0, price: 799, tag: '民俗节庆', color: '#8B4513' },
  { id: 2, name: '川菜非遗技艺大师课', category: '民俗节庆', hours: 4, students: 0, price: 699, tag: '民俗节庆', color: '#C62828' },
  { id: 3, name: '苗族古歌传承人课堂', category: '民俗节庆', hours: 4, students: 0, price: 199, tag: '民俗节庆', color: '#4E342E' },
  { id: 4, name: '泉州南音古乐演奏艺术', category: '戏曲曲艺', hours: 4, students: 0, price: 0, tag: '戏曲曲艺', color: '#1B5E20' },
  { id: 5, name: '西湖绸伞制作技艺传承班', category: '传统手工艺', hours: 4, students: 0, price: 499, tag: '传统手工艺', color: '#4A148C' },
  { id: 6, name: '黄帝陵祭祖大典文化解读', category: '民俗节庆', hours: 4, students: 0, price: 299, tag: '民俗节庆', color: '#BF360C' },
  { id: 7, name: '传统中医针灸技法精讲', category: '传统医药', hours: 4, students: 0, price: 899, tag: '传统医药', color: '#006064' },
  { id: 8, name: '白族三道茶歌舞传承课程', category: '民间舞蹈', hours: 4, students: 0, price: 0, tag: '民间舞蹈', color: '#E65100' },
  { id: 9, name: '昆曲《牡丹亭》表演艺术', category: '戏曲曲艺', hours: 4, students: 0, price: 399, tag: '戏曲曲艺', color: '#880E4F' },
  { id: 10, name: '景德镇手工制瓷技艺全流程', category: '传统手工艺', hours: 4, students: 0, price: 599, tag: '传统手工艺', color: '#33691E' },
])

const filteredCourses = ref(courses.value)

const filterByCategory = (cat) => {
  activeCategory.value = cat
  applyFilter()
}
const applyFilter = () => {
  filteredCourses.value = courses.value.filter(c => {
    const catMatch = activeCategory.value === '全部' || c.category === activeCategory.value
    const searchMatch = !searchText.value || c.name.includes(searchText.value)
    return catMatch && searchMatch
  })
}
</script>

<template>
  <div class="course-page">
    <div class="search-section">
      <div class="container">
        <div class="search-box" style="max-width: 600px; margin: 0 auto;">
          <input v-model="searchText" placeholder="搜索课程..." @input="applyFilter" />
          <button class="search-btn" @click="applyFilter">🔍</button>
        </div>
      </div>
    </div>

    <div class="container">
      <div class="category-tabs">
        <span v-for="cat in categories" :key="cat"
              :class="['tab-item', { active: activeCategory === cat }]"
              @click="filterByCategory(cat)">{{ cat }}</span>
      </div>

      <div class="course-grid">
        <div v-for="course in filteredCourses" :key="course.id" class="course-card card">
          <div class="course-cover" :style="{ background: course.color }">
            <span class="course-tag">{{ course.tag }}</span>
            <span class="cover-text">{{ course.name.substring(0, 4) }}</span>
          </div>
          <div class="course-info">
            <h4 class="course-name">{{ course.name }}</h4>
            <div class="course-meta">
              <span>📖 {{ course.hours }}课时</span>
              <span>👤 {{ course.students }}人学习</span>
            </div>
            <div class="course-price" :class="{ free: course.price === 0 }">
              {{ course.price === 0 ? '免费' : '¥' + course.price }}
            </div>
          </div>
        </div>
      </div>

      <div v-if="filteredCourses.length === 0" class="empty-state">
        <p>暂无匹配的课程</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.course-page { padding-bottom: 40px; }
.search-section {
  background: var(--ich-white);
  padding: 24px 0;
  margin-bottom: 8px;
}
.course-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-top: 8px;
}
.course-card { cursor: pointer; }
.course-cover {
  height: 140px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.course-tag {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 2px 10px;
  background: rgba(0,0,0,0.4);
  color: #fff;
  font-size: 11px;
  border-radius: 3px;
}
.cover-text {
  color: rgba(255,255,255,0.6);
  font-size: 22px;
  font-family: var(--ich-font-serif);
  letter-spacing: 4px;
}
.course-info { padding: 12px; }
.course-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ich-text-primary);
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.course-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--ich-text-muted);
  margin-bottom: 8px;
}
.course-price {
  font-size: 16px;
  font-weight: 700;
  color: var(--ich-primary);
}
.course-price.free {
  color: #4CAF50;
}
.empty-state {
  text-align: center;
  padding: 80px 0;
  color: var(--ich-text-muted);
}
@media (max-width: 1024px) { .course-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 640px) { .course-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
