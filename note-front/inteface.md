docker安装
···
docker run --name pgvector -e POSTGRES_PASSWORD=git_upload_db_password -p 5433:5432 -d ankane/pgvector
请求头格式 除验证码、登录、注册、都需要请求头
Authorization Bearer 281785c-6f8c-44bf-bf13-99ca10a75d29
#### 获取验证码
http://localhost:8888/api/common/captcha
响应
```json
{
"data": {
"captchaId": "305e9b00fc7b4323bd9a9b168da7edf0",
"imageBase64": "iVBORw0KGgoAAAANSUhEUgAAAHgAAAAoCAYAAAA16j4lAAAFYElEQVR4Xu2a208cVRzHGx988cGXPvjii4kJiT4b+wf4YmJ88cmo1XqPMdEWpA1prQaj2KjcSlNQQGxT2kJLgYba2mrR2gssN1FKCxTossuyCyzsLnuZ3f3JGT2HM7/dmZ0zs7NLl/kk34ed32+GZD97zpwzwzawKWi24QM2hYUtuMCxBRc4tuACxxZc4ORMcP8b2xWxyQ05E4zBwrWkVzz1Mj5ko5O8CRaFSLZFi8MEv1D2MEu+GJuMQHP7EpR+7YZ3y5zwesl9+PDgHBysnof2C35wzseyJvrmyUoWQlfRsGqySdGeHYZjhE0hOBBMwLeNXnhl96xmXiuelX8A8XjStGgsOFdgaSIxQt4FS1ISPqv2pMjUSm2LD19GGCy4bvEI6rAGLE0kRsi74Et/BBTyGloX4e50FNbCCUgk/hvdo3fCKSPcMbqGLyUEFkwgkq0WbVaYKHkXvP+7eSbtdI8flxnJJMCR4z7WW3F0AbcIkU4wxUrRmQQ/01iKD5ki74Lf2nefSQuEErisgCyyaO/7B5y4LISWYCvJJJhAJGdLdFrB3s4X+R5LIaKoNP9qHJezzpMPPSYHC47HIjAz1AuDXT9AX1sNDHU3wlTfLxBaNjdTYPQIpmRDdFrBBCKZRg9Pj/SziMDfW491LOOyZfCCY5EQjFz4SXGM5VQVOP++gU83jIhgihnRqoJ5REQTeNmZhE/MRGFnycY0XV7rgWuOEITWtKdrs/ASx3s7UsWieCaysx82IpiiR/RQ1e+Kz7oEU0RHNQULx9KvD4ZgV+mGZBKy5z2wvgA70bUMw2NhiMaSinOKzj3OYgQscLCzAXzTY/JUHZdisDQ3CcPnm1m9v/0wSNEIvowwvGCRiMBLFhLMY0Q0D5Y975WgqtkLO4uVe16aXXvvQ/WPPhibSP2Sedl6hfNyHR1HIRJaxS0gRdbkezLtc48P4BZhsDi9MYphwVp4PvDKMcJKIA5XrgehsskL75RtLMD4kPt2UGMK1yObF6wlzjMxwvpuXz2Ly6rM+CrwIRksTm+MYolgihnRBLL3nZqNwtmLK1D8pUshuewbN8Qk5bQtAi84HFBf3EUCftY3cK4Bl1XRIzgXWCqYQkWblf3bjSC8uXfjXn3+19RpVS+84GRCfXtGarTv1ukaXNYkneSCFMzDi+YXVssr6l8yz83hEDtn3yE3LutGKVh9uk/EJdbX316Hy5psScE8pRVuJouI00M4kmTnkNFsFF5wNM0CixJeXWJ95OGHCFtecFPbEpP1eY1HnoYz8c/6Kpqe81G5C5d1wwsm2yM13OMO1nfnWjcuC7OlBJMFFJVFQoRLcXXL5OEHmZZpf90x468NecFkv5tuj0tGNtlC0b6Fyb9wizBbSjChvnVRIfmTr9zwc+8quDwSRKJJiK5nbj4GPVdX5f/uoH2v7pmFaWcMX043vGDyOHKkpwX8rnvyQw7ysMM3cxsGO79nPQMd9fL92CybWvD2t59lyRZkq/Np5cYrQ70hws3AC77nuKIUnibLril8CUPwgo1EFCHBPLxss9LJKG05sySPSiwS5739Tui9FcSXEIaXF49FYfTSiRSpJH1ttfJo1sNL/lOaIWBhohHFsOB0mJXt9krQeXkFyg97YPcXLvldMdlKfby+mDpUvwCX/wxoPsESgZdIIJJn/39dSPa7Q91N8siOBFfQmak8v8MhB3PX/URKsDDRHC95BP8ZTZhgG/OoicZg6aIQyXpF24IfYKhoLdmy4NaTF1Vj82CgJjrjCMbCcWw2NxkF24hTeaZdzmbAFmwhVHQ+hduCc0g+ZNuCEc9NPpoSK8jV6LYFbxKsEv0vl6NqsXIMZ5cAAAAASUVORK5CYII="
},
"code": 0,
"message": "成功"
}
```
#### 注册

http://localhost:8888/api/common/register
请求
```json
{
"password" : "123456",
"phone" : "15012345678",
"captcha" : "1111",
"roleType" : 2,
"captchaId" : "111111"
}
```
响应
```json
{
"data": null,
"code": 0,
"message": "成功"
}
```
#### 登录
http://localhost:8888/api/common/login
请求
```json
{
  "password" : "123456",
  "phone" : "15012345678",
  "captcha" : "1111",
  "roleType" : 2,
  "captchaId" : "1111"
}
```
响应
```json
{
  "data": "1281785c-6f8c-44bf-bf13-99ca10a75d29",
  "code": 0,
  "message": "成功"
}
```
#### 获取用户信息
请求
http://localhost:8888/api/common/user/info
请求头
Authorization Bearer 281785c-6f8c-44bf-bf13-99ca10a75d29
```json
{
  "data": {
    "id": 1,
    "phone": "15012345678",
    "password": "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92",
    "role_type": 2,
    "username": "Seeker1941371694804353024",
    "avatar_url": "https://www.keaitupian.cn/cjpic/frombd/0/253/2221658670/3422894636.jpg",
    "real_name": "Seeker1941371694804353025",
    "id_card": null,
    "gender": null,
    "birth_date": null,
    "work_years": null,
    "education": null,
    "status": 1,
    "last_login_time": 1751695990761,
    "last_login_ip": "",
    "create_time": 1751694055603,
    "update_time": 1751694055603,
    "company_id": null
  },
  "code": 0,
  "message": "成功"
}
```
#### 退出登录
http://localhost:8888/api/common/logout

#### 文件上传

http://localhost:8888/api/common/file/upload

form-data file
```json
{
  "data": {
    "id": 1,
    "original_filename": "111.txt",
    "file_name": "b22295ef-3abf-4d07-9328-ae076a942ad7.txt",
    "extension": "txt",
    "file_size": 15218,
    "file_path": "uploads/other/20250705",
    "file_url": "http://127.0.0.1:8888/file/local-plus/uploads/other/202507056868c42f08429981c7810fc7.txt",
    "user_id": 1,
    "file_md5": "c4452cfc04332109696316e5d8f860c2",
    "create_time": null,
    "update_time": null,
    "file_type": "OTHER",
    "mime_type": "text/plain",
    "file_hash": null
  },
  "code": 0,
  "message": "成功"
}
```

---

## 文档管理接口

#### 上传文档
http://localhost:8888/api/documents/upload
请求方式：POST
请求头：Authorization Bearer token
请求体：form-data
- file: 文件（支持pdf、txt、docx等格式，最大10MB）

响应：
```json
{
  "data": {
    "id": 1,
    "originalFilename": "测试文档.pdf",
    "fileName": "uuid_filename.pdf",
    "extension": "pdf",
    "fileSize": 2048576,
    "filePath": "/uploads/2024/01/uuid_filename.pdf",
    "fileUrl": "http://127.0.0.1:8888/file/uuid_filename.pdf",
    "userId": 1,
    "fileMd5": "abc123def456",
    "createTime": "2024-01-01T12:00:00",
    "updateTime": "2024-01-01T12:00:00",
    "fileType": "pdf",
    "mimeType": "application/pdf",
    "fileHash": "hash123",
    "documentCount": 15,
    "processingStatus": "SUCCESS",
    "processingMessage": "文档上传并处理成功"
  },
  "code": 0,
  "message": "成功"
}
```

#### 获取文件列表
http://localhost:8888/api/documents/files/list
请求方式：POST
请求头：Authorization Bearer token
请求体：
```json
{
  "page": 1,
  "size": 10,
  "keyword": "测试",
  "fileType": "pdf",
  "sortBy": "create_time",
  "sortOrder": "desc"
}
```

参数说明：
- page: 页码（默认1）
- size: 每页大小（默认10，最大100）
- keyword: 搜索关键词（可选，搜索文件名）
- fileType: 文件类型过滤（可选）
- sortBy: 排序字段（可选，默认create_time）
- sortOrder: 排序方向（可选，desc/asc，默认desc）

可用的排序字段：
- id: 文件ID
- create_time: 创建时间
- update_time: 更新时间
- file_size: 文件大小
- original_filename: 原始文件名
- file_name: 存储文件名
- file_type: 文件类型
- mime_type: MIME类型

响应：
```json
{
  "data": {
    "data": [
      {
        "id": 1,
        "originalFilename": "重要报告.pdf",
        "fileName": "uuid_filename.pdf",
        "extension": "pdf",
        "fileSize": 2048576,
        "filePath": "/uploads/2024/01/uuid_filename.pdf",
        "fileUrl": "http://127.0.0.1:8888/file/uuid_filename.pdf",
        "userId": 1,
        "fileMd5": "abc123def456",
        "createTime": "2024-01-01T12:00:00",
        "updateTime": "2024-01-01T12:00:00",
        "fileType": "pdf",
        "mimeType": "application/pdf",
        "fileHash": "hash123"
      }
    ],
    "total": 25,
    "size": 10,
    "current": 1,
    "pages": 3
  },
  "code": 0,
  "message": "成功"
}
```

#### 删除文件
http://localhost:8888/api/documents/files/{fileId}
请求方式：DELETE
请求头：Authorization Bearer token
路径参数：
- fileId: 文件ID

响应：
```json
{
  "data": null,
  "code": 0,
  "message": "成功"
}
```

---

## 聊天对话接口

#### 流式聊天（稳定速率版本）
http://localhost:8888/api/chat/stream
请求方式：POST
请求头：Authorization Bearer token
Content-Type: application/json
Accept: text/event-stream

请求体：
```json
{
  "message": "请帮我总结一下上传的文档内容",
  "memoryId": "session_123"
}
```

参数说明：
- message: 用户消息内容
- memoryId: 会话ID（用于维持对话上下文）

响应：Server-Sent Events 流式数据（带速率控制）
```
data: 根据您上传的文档，我来为您总结一下主要内容：

data: 1. 文档主要讨论了...

data: 2. 关键要点包括...

data: [DONE]
```

**特性说明：**
- 基础延迟：每个数据块之间最少间隔50ms
- 自适应延迟：根据内容长度动态调整延迟时间
- 平滑传输：使用滑动窗口避免突发性传输
- 背压控制：防止客户端处理不及时导致的问题

#### 流式聊天（快速版本）
http://localhost:8888/api/chat/stream/fast
请求方式：POST
请求头：Authorization Bearer token
Content-Type: application/json
Accept: text/event-stream

请求体：
```json
{
  "message": "请帮我总结一下上传的文档内容",
  "memoryId": "session_123"
}
```

参数说明：
- message: 用户消息内容
- memoryId: 会话ID（用于维持对话上下文）

响应：Server-Sent Events 流式数据（无速率控制）
```
data: 根据您上传的文档，我来为您总结一下主要内容：

data: 1. 文档主要讨论了...

data: 2. 关键要点包括...

data: [DONE]
```

**使用建议：**
- 默认使用 `/stream` 接口，提供更好的用户体验
- 在需要快速响应的场景下使用 `/stream/fast` 接口
- 两个接口功能完全相同，仅在响应速率控制上有差异

#### 普通聊天
http://localhost:8888/api/chat/simple
请求方式：POST
请求头：Authorization Bearer token
请求体：
```json
{
  "message": "请帮我总结一下上传的文档内容",
  "memoryId": "session_123"
}
```

响应：
```json
{
  "data": "根据您上传的文档，我来为您总结一下主要内容：\n\n1. 文档主要讨论了...\n2. 关键要点包括...",
  "code": 0,
  "message": "成功"
}
```

#### 获取聊天统计
http://localhost:8888/api/chat/stats
请求方式：GET
请求头：Authorization Bearer token

响应：
```json
{
  "data": "用户知识库统计：文档数量15个，总字数约50000字，最近更新时间2024-01-01",
  "code": 0,
  "message": "成功"
}
```

---

## 笔记本管理接口

#### 创建笔记本
http://localhost:8888/api/notebooks
请求方式：POST
请求头：Authorization Bearer token
请求体：
```json
{
  "name": "我的第一个笔记本",
  "description": "这是我的第一个笔记本",
  "cover": "http://example.com/cover.jpg",
  "sortOrder": 1
}
```
响应：
```json
{
  "data": {
    "id": 1,
    "name": "我的第一个笔记本",
    "description": "这是我的第一个笔记本",
    "cover": "http://example.com/cover.jpg",
    "sortOrder": 1,
    "noteCount": 0,
    "createTime": "2024-01-01T10:00:00",
    "updateTime": "2024-01-01T10:00:00"
  },
  "code": 0,
  "message": "成功"
}
```

#### 获取笔记本列表
http://localhost:8888/api/notebooks
请求方式：GET
请求头：Authorization Bearer token
响应：
```json
{
  "data": [
    {
      "id": 1,
      "name": "我的第一个笔记本",
      "description": "这是我的第一个笔记本",
      "cover": "http://example.com/cover.jpg",
      "sortOrder": 1,
      "noteCount": 5,
      "createTime": "2024-01-01T10:00:00",
      "updateTime": "2024-01-01T10:00:00"
    },
    {
      "id": 2,
      "name": "学习笔记",
      "description": "记录学习心得",
      "cover": null,
      "sortOrder": 2,
      "noteCount": 3,
      "createTime": "2024-01-02T10:00:00",
      "updateTime": "2024-01-02T10:00:00"
    }
  ],
  "code": 0,
  "message": "成功"
}
```

#### 获取笔记本详情
http://localhost:8888/api/notebooks/1
请求方式：GET
请求头：Authorization Bearer token
响应：
```json
{
  "data": {
    "id": 1,
    "name": "我的第一个笔记本",
    "description": "这是我的第一个笔记本",
    "cover": "http://example.com/cover.jpg",
    "sortOrder": 1,
    "noteCount": 5,
    "createTime": "2024-01-01T10:00:00",
    "updateTime": "2024-01-01T10:00:00"
  },
  "code": 0,
  "message": "成功"
}
```

#### 更新笔记本
http://localhost:8888/api/notebooks/1
请求方式：PUT
请求头：Authorization Bearer token
请求体：
```json
{
  "name": "更新后的笔记本名称",
  "description": "更新后的描述",
  "cover": "http://example.com/new-cover.jpg",
  "sortOrder": 2
}
```
响应：
```json
{
  "data": {
    "id": 1,
    "name": "更新后的笔记本名称",
    "description": "更新后的描述",
    "cover": "http://example.com/new-cover.jpg",
    "sortOrder": 2,
    "noteCount": 5,
    "createTime": "2024-01-01T10:00:00",
    "updateTime": "2024-01-01T15:30:00"
  },
  "code": 0,
  "message": "成功"
}
```

#### 删除笔记本
http://localhost:8888/api/notebooks/1
请求方式：DELETE
请求头：Authorization Bearer token
响应：
```json
{
  "data": null,
  "code": 0,
  "message": "成功"
}
```

---

## 笔记管理接口

#### 创建笔记
http://localhost:8888/api/notes
请求方式：POST
请求头：Authorization Bearer token
请求体：
```json
{
  "notebookId": 1,
  "title": "我的第一篇笔记",
  "contentMd": "# 标题\n\n这是我的第一篇笔记内容...",
  "status": 1,
  "isPinned": false,
  "tags": ["学习", "笔记"]
}
```
响应：
```json
{
  "data": {
    "id": 1,
    "notebookId": 1,
    "title": "我的第一篇笔记",
    "contentMd": "# 标题\n\n这是我的第一篇笔记内容...",
    "contentHtml": "<h1>标题</h1><p>这是我的第一篇笔记内容...</p>",
    "status": 1,
    "isPinned": false,
    "viewCount": 0,
    "wordCount": 15,
    "notebookName": "我的第一个笔记本",
    "tags": ["学习", "笔记"],
    "createTime": "2024-01-01T10:00:00",
    "updateTime": "2024-01-01T10:00:00"
  },
  "code": 0,
  "message": "成功"
}
```

#### 分页查询笔记列表
http://localhost:8888/api/notes?page=1&size=10&notebookId=1&keyword=学习
请求方式：GET
请求头：Authorization Bearer token
参数说明：
- page: 页码（默认1）
- size: 每页大小（默认10）
- notebookId: 笔记本ID（可选）
- keyword: 搜索关键词（可选）

响应：
```json
{
  "data": {
    "records": [
      {
        "id": 1,
        "title": "我的第一篇笔记",
        "status": 1,
        "isPinned": true,
        "viewCount": 10,
        "wordCount": 15,
        "notebookName": "我的第一个笔记本",
        "tags": ["学习", "笔记"],
        "createTime": "2024-01-01T10:00:00",
        "updateTime": "2024-01-01T10:00:00"
      },
      {
        "id": 2,
        "title": "Markdown 语法指南",
        "status": 1,
        "isPinned": false,
        "viewCount": 25,
        "wordCount": 45,
        "notebookName": "我的第一个笔记本",
        "tags": ["Markdown", "语法", "指南"],
        "createTime": "2024-01-01T11:00:00",
        "updateTime": "2024-01-01T11:00:00"
      }
    ],
    "total": 2,
    "current": 1,
    "size": 10
  },
  "code": 0,
  "message": "成功"
}
```

#### 获取笔记详情
http://localhost:8888/api/notes/1
请求方式：GET
请求头：Authorization Bearer token
响应：
```json
{
  "data": {
    "id": 1,
    "notebookId": 1,
    "title": "我的第一篇笔记",
    "contentMd": "# 标题\n\n这是我的第一篇笔记内容...",
    "contentHtml": "<h1>标题</h1><p>这是我的第一篇笔记内容...</p>",
    "status": 1,
    "isPinned": false,
    "viewCount": 11,
    "wordCount": 15,
    "notebookName": "我的第一个笔记本",
    "tags": ["学习", "笔记"],
    "createTime": "2024-01-01T10:00:00",
    "updateTime": "2024-01-01T10:00:00"
  },
  "code": 0,
  "message": "成功"
}
```

#### 更新笔记
http://localhost:8888/api/notes/update
请求方式：POST
请求头：Authorization Bearer token
请求体：
```json
{
  "noteId": 1,
  "title": "更新后的笔记标题",
  "contentMd": "# 更新后的标题\n\n更新后的笔记内容...",
  "status": 1,
  "isPinned": true,
  "tags": ["学习", "笔记", "更新"]
}
```
响应：
```json
{
  "data": {
    "id": 1,
    "notebookId": 1,
    "title": "更新后的笔记标题",
    "contentMd": "# 更新后的标题\n\n更新后的笔记内容...",
    "contentHtml": "<h1>更新后的标题</h1><p>更新后的笔记内容...</p>",
    "status": 1,
    "isPinned": true,
    "viewCount": 11,
    "wordCount": 18,
    "notebookName": "我的第一个笔记本",
    "tags": ["学习", "笔记", "更新"],
    "createTime": "2024-01-01T10:00:00",
    "updateTime": "2024-01-01T15:30:00"
  },
  "code": 0,
  "message": "成功"
}
```

#### 删除笔记
http://localhost:8888/api/notes/1
请求方式：DELETE
请求头：Authorization Bearer token
响应：
```json
{
  "data": null,
  "code": 0,
  "message": "成功"
}
```

#### 切换笔记置顶状态
http://localhost:8888/api/notes/1/pin
请求方式：POST
请求头：Authorization Bearer token
响应：
```json
{
  "data": null,
  "code": 0,
  "message": "成功"
}
```

#### 自动保存笔记内容
http://localhost:8888/api/notes/autosave
请求方式：POST
请求头：Authorization Bearer token
请求体：
```json
{
  "noteId": 1,
  "contentMd": "# 自动保存的内容\n\n这是编辑器自动保存的内容..."
}
```
响应：
```json
{
  "data": null,
  "code": 0,
  "message": "成功"
}
```

#### 获取用户所有标签
http://localhost:8888/api/notes/tags
请求方式：GET
请求头：Authorization Bearer token
响应：
```json
{
  "data": [
    "学习",
    "笔记",
    "Markdown",
    "语法",
    "指南",
    "更新"
  ],
  "code": 0,
  "message": "成功"
}
```

#### 将笔记添加到个人知识库
http://localhost:8888/api/notes/{noteId}/add-to-knowledge-base
请求方式：POST
请求头：Authorization Bearer token
路径参数：
- noteId: 笔记ID

响应：
```json
{
  "data": {
    "success": true,
    "message": "笔记已成功添加到个人知识库",
    "noteId": 1,
    "noteTitle": "我的学习笔记",
    "fileId": 15,
    "fileName": "我的学习笔记.pdf",
    "fileSize": 2048576,
    "processResult": {
      "success": true,
      "message": "文档处理成功",
      "documentCount": 5
    }
  },
  "code": 0,
  "message": "成功"
}
```

---

## 共享知识库管理接口

#### 创建共享知识库
http://localhost:8888/api/shared-knowledge-base
请求方式：POST
请求头：Authorization Bearer token
请求体：
```json
{
  "name": "团队技术文档库",
  "description": "团队共享的技术文档和学习资料",
  "coverUrl": "http://example.com/cover.jpg",
  "isPublic": true,
  "password": "123456"
}
```

参数说明：
- name: 知识库名称（必填）
- description: 知识库描述（可选）
- coverUrl: 封面图片URL（可选）
- isPublic: 是否公开（默认true）
- password: 访问密码（可选，设置后需要密码才能加入）

响应：
```json
{
  "data": {
    "id": 1,
    "name": "团队技术文档库",
    "description": "团队共享的技术文档和学习资料",
    "coverUrl": "http://example.com/cover.jpg",
    "isPublic": true,
    "hasPassword": true,
    "creatorId": 1,
    "creatorName": "张三",
    "memberCount": 1,
    "fileCount": 0,
    "createTime": "2024-01-01T10:00:00",
    "updateTime": "2024-01-01T10:00:00",
    "userRole": "CREATOR",
    "canEdit": true,
    "canDelete": true,
    "canManageMembers": true
  },
  "code": 0,
  "message": "成功"
}
```

#### 更新共享知识库
http://localhost:8888/api/shared-knowledge-base/{knowledgeBaseId}
请求方式：PUT
请求头：Authorization Bearer token
路径参数：
- knowledgeBaseId: 知识库ID

请求体：
```json
{
  "name": "更新后的知识库名称",
  "description": "更新后的描述",
  "coverUrl": "http://example.com/new-cover.jpg",
  "isPublic": false,
  "password": "newpassword"
}
```

响应：与创建接口相同的数据结构

#### 删除共享知识库
http://localhost:8888/api/shared-knowledge-base/{knowledgeBaseId}
请求方式：DELETE
请求头：Authorization Bearer token
路径参数：
- knowledgeBaseId: 知识库ID

响应：
```json
{
  "data": null,
  "code": 0,
  "message": "成功"
}
```

#### 获取知识库详情
http://localhost:8888/api/shared-knowledge-base/{knowledgeBaseId}
请求方式：GET
请求头：Authorization Bearer token
路径参数：
- knowledgeBaseId: 知识库ID

响应：与创建接口相同的数据结构

#### 获取我创建的知识库列表
http://localhost:8888/api/shared-knowledge-base/my-created
请求方式：GET
请求头：Authorization Bearer token

响应：
```json
{
  "data": [
    {
      "id": 1,
      "name": "团队技术文档库",
      "description": "团队共享的技术文档和学习资料",
      "coverUrl": "http://example.com/cover.jpg",
      "isPublic": true,
      "hasPassword": true,
      "creatorId": 1,
      "creatorName": "张三",
      "memberCount": 5,
      "fileCount": 15,
      "createTime": "2024-01-01T10:00:00",
      "updateTime": "2024-01-01T10:00:00",
      "userRole": "CREATOR",
      "canEdit": true,
      "canDelete": true,
      "canManageMembers": true
    }
  ],
  "code": 0,
  "message": "成功"
}
```

#### 获取我加入的知识库列表
http://localhost:8888/api/shared-knowledge-base/my-joined
请求方式：GET
请求头：Authorization Bearer token

响应：数据结构与"我创建的知识库"相同，但userRole为"MEMBER"

#### 知识库广场
http://localhost:8888/api/shared-knowledge-base/square
请求方式：GET
请求头：Authorization Bearer token
参数：
- keyword: 搜索关键词（可选）
- sortBy: 排序字段（默认create_time）
- sortOrder: 排序顺序（默认desc）
- page: 页码（默认1）
- size: 每页大小（默认10）

响应：
```json
{
  "data": {
    "data": [
      {
        "id": 1,
        "name": "公开技术文档库",
        "description": "公开的技术文档和教程",
        "coverUrl": "http://example.com/cover.jpg",
        "isPublic": true,
        "hasPassword": false,
        "creatorName": "李四",
        "memberCount": 25,
        "fileCount": 50,
        "createTime": "2024-01-01T10:00:00",
        "isJoined": false
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  },
  "code": 0,
  "message": "成功"
}
```

#### 加入知识库
http://localhost:8888/api/shared-knowledge-base/join
请求方式：POST
请求头：Authorization Bearer token
请求体：
```json
{
  "knowledgeBaseId": 1,
  "password": "123456"
}
```

参数说明：
- knowledgeBaseId: 知识库ID（必填）
- password: 访问密码（知识库设置了密码时必填）

响应：
```json
{
  "data": null,
  "code": 0,
  "message": "成功"
}
```

#### 退出知识库
http://localhost:8888/api/shared-knowledge-base/{knowledgeBaseId}/leave
请求方式：POST
请求头：Authorization Bearer token
路径参数：
- knowledgeBaseId: 知识库ID

响应：
```json
{
  "data": null,
  "code": 0,
  "message": "成功"
}
```

#### 移除成员
http://localhost:8888/api/shared-knowledge-base/{knowledgeBaseId}/members/{memberId}
请求方式：DELETE
请求头：Authorization Bearer token
路径参数：
- knowledgeBaseId: 知识库ID
- memberId: 要移除的成员ID

响应：
```json
{
  "data": null,
  "code": 0,
  "message": "成功"
}
```

---

## 共享知识库文件管理接口

#### 上传文件到知识库
http://localhost:8888/api/shared-knowledge-base/{knowledgeBaseId}/files/upload
请求方式：POST
请求头：Authorization Bearer token
请求体：form-data
- file: 文件（支持pdf、txt、docx等格式）

路径参数：
- knowledgeBaseId: 知识库ID

响应：
```json
{
  "data": {
    "id": 1,
    "knowledgeBaseId": 1,
    "fileId": 15,
    "fileName": "技术文档.pdf",
    "originalFileName": "技术文档.pdf",
    "fileSize": 2048576,
    "fileType": "pdf",
    "uploaderId": 1,
    "uploaderName": "张三",
    "createTime": "2024-01-01T10:00:00",
    "sourceType": 1,
    "processingStatus": "SUCCESS",
    "documentCount": 10
  },
  "code": 0,
  "message": "成功"
}
```

#### 复制文件到知识库
http://localhost:8888/api/shared-knowledge-base/{knowledgeBaseId}/files/copy
请求方式：POST
请求头：Authorization Bearer token
路径参数：
- knowledgeBaseId: 知识库ID

请求体：
```json
{
  "fileIds": [1, 2, 3, 4, 5]
}
```

参数说明：
- fileIds: 要复制的文件ID列表（从个人知识库复制）

响应：
```json
{
  "data": {
    "totalCount": 5,
    "successCount": 4,
    "failCount": 1,
    "successFiles": [
      {
        "id": 1,
        "fileName": "文档1.pdf",
        "status": "SUCCESS"
      },
      {
        "id": 2,
        "fileName": "文档2.pdf",
        "status": "SUCCESS"
      }
    ],
    "failFiles": [
      {
        "id": 3,
        "fileName": "文档3.pdf",
        "status": "FAILED",
        "reason": "文件已存在"
      }
    ]
  },
  "code": 0,
  "message": "成功"
}
```

#### 删除知识库文件
http://localhost:8888/api/shared-knowledge-base/{knowledgeBaseId}/files/{fileId}
请求方式：DELETE
请求头：Authorization Bearer token
路径参数：
- knowledgeBaseId: 知识库ID
- fileId: 文件ID

响应：
```json
{
  "data": null,
  "code": 0,
  "message": "成功"
}
```

#### 获取知识库文件列表
http://localhost:8888/api/shared-knowledge-base/{knowledgeBaseId}/files
请求方式：GET
请求头：Authorization Bearer token
路径参数：
- knowledgeBaseId: 知识库ID

参数：
- page: 页码（默认1）
- size: 每页大小（默认10）
- keyword: 搜索关键词（可选）

响应：
```json
{
  "data": {
    "data": [
      {
        "id": 1,
        "knowledgeBaseId": 1,
        "fileId": 15,
        "fileName": "技术文档.pdf",
        "originalFileName": "技术文档.pdf",
        "fileSize": 2048576,
        "fileType": "pdf",
        "uploaderId": 1,
        "uploaderName": "张三",
        "createTime": "2024-01-01T10:00:00",
        "sourceType": 1,
        "processingStatus": "SUCCESS",
        "documentCount": 10
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  },
  "code": 0,
  "message": "成功"
}
```

#### 获取个人文件列表
http://localhost:8888/api/shared-knowledge-base/personal-files
请求方式：GET
请求头：Authorization Bearer token

响应：
```json
{
  "data": [
    {
      "id": 1,
      "fileName": "个人文档.pdf",
      "originalFileName": "个人文档.pdf",
      "fileSize": 1024000,
      "fileType": "pdf",
      "createTime": "2024-01-01T09:00:00",
      "sourceType": 1,
      "sourceNoteId": null,
      "sourceNoteTitle": null
    },
    {
      "id": 2,
      "fileName": "学习笔记.pdf",
      "originalFileName": "学习笔记.pdf",
      "fileSize": 512000,
      "fileType": "pdf",
      "createTime": "2024-01-01T11:00:00",
      "sourceType": 2,
      "sourceNoteId": 5,
      "sourceNoteTitle": "我的学习笔记"
    }
  ],
  "code": 0,
  "message": "成功"
}
```

---

## 共享知识库AI对话接口


#### 共享知识库AI对话（流式）
http://localhost:8888/api/shared-knowledge-base/chat/stream
请求方式：POST
请求头：Authorization Bearer token
Content-Type: application/json
Accept: text/event-stream

请求体：
```json
{
  "knowledgeBaseId": 1,
  "message": "请帮我总结一下这个知识库中关于机器学习的内容",
  "memoryId": "session_kb_123"
}
```

参数说明：
- knowledgeBaseId: 知识库ID
- message: 用户消息内容
- memoryId: 会话ID（用于维持对话上下文）

响应：Server-Sent Events 流式数据
```
data: 根据知识库中的内容，我来为您总结机器学习相关的知识点：

data: 1. 机器学习基础概念...

data: 2. 监督学习算法...

data: [DONE]
```

#### 获取知识库统计信息
http://localhost:8888/api/shared-knowledge-base/{knowledgeBaseId}/stats
请求方式：GET
请求头：Authorization Bearer token
路径参数：
- knowledgeBaseId: 知识库ID

响应：
```json
{
  "data": {
    "message": "统计信息功能待实现",
    "knowledgeBaseId": 1
  },
  "code": 0,
  "message": "成功"
}
```
