import request from './index'

// ========== 非遗动态/笔记 ==========
export function getPostList(params) {
  return request.get('/content/post/list', { params })
}

export function getPost(id) {
  return request.get(`/content/post/${id}`)
}

export function addPost(data) {
  return request.post('/content/post/add', data)
}

export function likePost(postId, userId) {
  return request.post('/content/post/like', null, { params: { postId, userId } })
}

export function unlikePost(postId, userId) {
  return request.post('/content/post/unlike', null, { params: { postId, userId } })
}

export function favoritePost(postId, userId) {
  return request.post('/content/post/favorite', null, { params: { postId, userId } })
}

export function unfavoritePost(postId, userId) {
  return request.post('/content/post/unfavorite', null, { params: { postId, userId } })
}

export function hasLikedPost(postId, userId) {
  return request.get('/content/post/hasLiked', { params: { postId, userId } })
}

export function hasFavoritedPost(postId, userId) {
  return request.get('/content/post/hasFavorited', { params: { postId, userId } })
}

export function getUserPosts(userId, params) {
  return request.get('/content/post/user/posts', { params: { userId, ...params } })
}

export function getUserLikedPosts(userId, params) {
  return request.get('/content/post/user/liked', { params: { userId, ...params } })
}

export function getUserFavoritedPosts(userId, params) {
  return request.get('/content/post/user/favorited', { params: { userId, ...params } })
}

// ========== 非遗动态评论 ==========
export function getPostComments(postId, params) {
  return request.get('/content/post/comment/list', { params: { postId, ...params } })
}

export function addPostComment(data) {
  return request.post('/content/post/comment/add', data)
}
