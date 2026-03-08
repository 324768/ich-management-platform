import request from './index'

export function uploadFile(file, bizType = 'common') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', bizType)
  return request.post('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

export function uploadFiles(files, bizType = 'common') {
  const formData = new FormData()
  for (const file of files) {
    formData.append('files', file)
  }
  formData.append('bizType', bizType)
  return request.post('/file/upload/batch', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}
