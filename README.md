# Note Dev

一个前后端分离的智能笔记与知识库项目，核心覆盖三类场景：

- 个人笔记与笔记本管理
- 公开博客与后台发布管理
- 共享知识库与 AI 检索对话

## 项目分析

这个仓库当前的主体结构是：

- `note-front`：Vue 3 + Vite 前端
- `initialization-note-side`：Spring Boot 3 后端
- `docker`：Docker Compose / Nginx / 镜像构建文件

后端已经集成 PostgreSQL、Redis、Flyway、Sa-Token、LangChain4j、pgvector 和文件存储，前端已经包含笔记、博客、共享知识库、AI 配置等主要页面。

## 技术栈

### 前端

- Vue 3
- Vite
- Vue Router
- Pinia
- Element Plus
- Axios

### 后端

- Java 17
- Spring Boot 3
- MyBatis-Plus
- Sa-Token
- PostgreSQL + pgvector
- Redis
- Flyway
- LangChain4j
- x-file-storage

### 部署

- Docker Compose
- Nginx

## 目录结构

```text
.
├─ docker/                              Docker 相关文件
├─ initialization-note-side/            Spring Boot 后端
│  ├─ src/main/java/                    业务代码
│  ├─ src/main/resources/db/migration/  Flyway 迁移脚本
│  └─ sql/                              初始化 SQL
├─ note-front/                          Vue 3 前端
│  ├─ src/api/                          前端接口封装
│  ├─ src/components/                   通用组件
│  ├─ src/views/                        页面
│  └─ src/router/                       路由
├─ sql_init.sql                         历史初始化 SQL
└─ sql_upgrade.sql                      历史升级 SQL
```

## 核心功能

- 用户注册、登录、验证码、密码修改
- 笔记本与笔记 CRUD、置顶、标签、自动保存
- Markdown 导入导出、AI 格式优化
- 文档上传、文档解析
- 共享知识库、成员管理、文件复制与上传
- AI 流式对话、知识库问答
- 博客分类、标签、文章发布、公开页与管理页

## 项目优势

- 区别于只依赖向量召回的传统 RAG：这个项目不是把所有文档直接丢进一个向量库里“统一相似度搜索”，而是叠加了分类、归属、共享范围、文件来源、成员关系等结构化条件，先缩小搜索空间，再做召回。
- 区别于重型 LLMWiki：这里没有引入高维护成本的重图谱、重编辑流程或复杂知识建模，而是轻量吸收了 LLMWiki 的知识组织思路，把笔记、文档、博客、共享知识库组合成可持续维护的知识单元。
- 混合式知识库架构：传统 RAG 负责稳定召回，分类与链接关系负责路由和约束，轻量 Wiki 化组织负责沉淀知识结构，三者结合后更适合真实业务场景。
- 更强的命中精度：不是单纯靠 embedding 相似度决定答案，还会结合知识库边界、成员权限、分类归档和上下文路由共同决定检索结果。
- 更低的维护和部署成本：比重型知识图谱和纯 Wiki 推理链路更轻，比纯向量桶更有组织性，适合个人开发者和中小团队落地。
- 更好的可解释性：知识命中路径能回溯到文档分类、共享知识库范围、文件归档和业务上下文，而不是只给出一个“相似度最高”的黑盒结果。

## 本地开发

### 1. 准备配置文件

复制以下示例文件，再根据你的环境替换占位值：

```powershell
Copy-Item initialization-note-side\src\main\resources\application.example.yml initialization-note-side\src\main\resources\application.yml
Copy-Item docker\config\application-docker.example.yml docker\config\application-docker.yml
Copy-Item docker\docker-compose.example.yml docker\docker-compose.yml
Copy-Item docker\.env.example docker\.env
Copy-Item note-front\.env.example note-front\.env.local
```

仓库里的密码、JWT 和模型 Key 已经全部替换成 Git 上传占位值：

- 数据库密码：`git_upload_db_password`
- Redis 密码：`git_upload_redis_password`
- JWT 密钥：`git_upload_jwt_secret`
- 模型 Key：`sk-xxxxxx`

正式运行前请替换成你自己的真实配置。

### 2. 启动基础依赖

本地开发至少需要：

- PostgreSQL
- Redis

默认端口约定：

- 后端：`8889`
- 前端开发：`3001`
- PostgreSQL：`5434`
- Redis：`6379`

### 3. 启动后端

```powershell
mvn -f initialization-note-side\pom.xml spring-boot:run
```

### 4. 启动前端

```powershell
cd note-front
npm install
npm run dev
```

## Docker 部署

当前 Dockerfile 已经改为从源码构建，不需要把 `note-front/dist` 或 `initialization-note-side/target` 一起提交到仓库。

### 1. 准备 Docker 配置

```powershell
Copy-Item docker\docker-compose.example.yml docker\docker-compose.yml
Copy-Item docker\.env.example docker\.env
Copy-Item docker\config\application-docker.example.yml docker\config\application-docker.yml
```

### 2. 启动

```powershell
cd docker
docker compose up --build -d
```

默认访问地址：

- 前端 / Nginx：`http://127.0.0.1:3001`
- 后端 API：`http://127.0.0.1:8889`
