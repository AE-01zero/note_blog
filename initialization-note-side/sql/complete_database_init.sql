CREATE EXTENSION vector;
create table if not exists public.documents
(
    embedding_id        uuid         default gen_random_uuid() not null
    primary key,
    user_id             varchar(100),
    text                text                                   not null,
    metadata            jsonb,
    embedding           vector,
    created_at          timestamp(6) default CURRENT_TIMESTAMP,
    file_id             bigint,
    knowledge_base_id   bigint,
    knowledge_base_type smallint     default 1
    );

comment on column public.documents.file_id is '关联的文件ID，用于文件删除时清理向量数据';

comment on column public.documents.knowledge_base_id is '知识库ID（个人知识库为用户ID，共享知识库为共享知识库ID）';

comment on column public.documents.knowledge_base_type is '知识库类型：1-个人知识库 2-共享知识库';

alter table public.documents
    owner to postgres;

create index if not exists documents_created_at_idx
    on public.documents (created_at);

create index if not exists documents_embedding_id_idx
    on public.documents (embedding_id);

create index if not exists documents_metadata_idx
    on public.documents using gin (metadata);

create index if not exists documents_user_id_created_at_idx
    on public.documents (user_id, created_at);

create index if not exists documents_user_id_idx
    on public.documents (user_id);

create index if not exists idx_documents_file_id
    on public.documents (file_id);

create index if not exists idx_documents_file_user
    on public.documents (file_id, user_id);

create index if not exists idx_documents_kb_type
    on public.documents (knowledge_base_type);

create index if not exists idx_documents_knowledge_base
    on public.documents (knowledge_base_id, knowledge_base_type);

create table if not exists public.t_user
(
    id              bigserial
    primary key,
    phone           varchar(11) not null
    unique,
    password        varchar(100),
    role_type       smallint    not null,
    username        varchar(50),
    avatar_url      varchar(255),
    real_name       varchar(50),
    id_card         varchar(18),
    gender          smallint,
    birth_date      date,
    work_years      smallint,
    education       smallint,
    status          smallint  default 1,
    last_login_time timestamp,
    last_login_ip   varchar(50),
    create_time     timestamp default CURRENT_TIMESTAMP,
    update_time     timestamp default CURRENT_TIMESTAMP
    );

alter table public.t_user
    owner to postgres;

create table if not exists public.t_notebook
(
    id          bigserial
    primary key,
    name        varchar(128) not null,
    description text,
    color       varchar(7),
    user_id     bigint       not null,
    create_time timestamp default CURRENT_TIMESTAMP,
    update_time timestamp default CURRENT_TIMESTAMP,
    sort_order  integer
    );

comment on column public.t_notebook.sort_order is '排序';

alter table public.t_notebook
    owner to postgres;

create table if not exists public.t_note
(
    id           bigserial
    primary key,
    title        varchar(255) not null,
    content_md   text,
    content_html text,
    notebook_id  bigint,
    user_id      bigint       not null,
    is_pinned    boolean   default false,
    status       smallint  default 1,
    create_time  timestamp default CURRENT_TIMESTAMP,
    update_time  timestamp default CURRENT_TIMESTAMP,
    view_count   integer,
    word_count   integer
    );

alter table public.t_note
    owner to postgres;

create table if not exists public.t_note_tag
(
    id          bigserial
    primary key,
    note_id     bigint      not null,
    tag_name    varchar(50) not null,
    create_time timestamp default CURRENT_TIMESTAMP,
    constraint uk_note_tag
    unique (note_id, tag_name)
    );

alter table public.t_note_tag
    owner to postgres;

create table if not exists public.t_shared_knowledge_base
(
    id           bigserial
    primary key,
    name         varchar(128) not null,
    description  text,
    cover_url    varchar(255),
    password     varchar(100),
    creator_id   bigint       not null,
    is_public    boolean   default true,
    member_count integer   default 1
    constraint chk_shared_kb_member_count
    check (member_count >= 0),
    file_count   integer   default 0
    constraint chk_shared_kb_file_count
    check (file_count >= 0),
    status       smallint  default 1
    constraint chk_shared_kb_status
    check (status = ANY (ARRAY [0, 1])),
    create_time  timestamp default CURRENT_TIMESTAMP,
    update_time  timestamp default CURRENT_TIMESTAMP
    );

alter table public.t_shared_knowledge_base
    owner to postgres;

create table if not exists public.t_knowledge_base_member
(
    id                bigserial
    primary key,
    knowledge_base_id bigint not null,
    user_id           bigint not null,
    role              smallint  default 2
    constraint chk_kb_member_role
    check (role = ANY (ARRAY [1, 2])),
    join_time         timestamp default CURRENT_TIMESTAMP,
    constraint uk_kb_member
    unique (knowledge_base_id, user_id)
    );

alter table public.t_knowledge_base_member
    owner to postgres;

create table if not exists public.t_knowledge_base_file
(
    id                bigserial
    primary key,
    knowledge_base_id bigint not null,
    file_id           bigint not null,
    uploader_id       bigint not null,
    source_type       smallint  default 1
    constraint chk_kb_file_source_type
    check (source_type = ANY (ARRAY [1, 2])),
    upload_time       timestamp default CURRENT_TIMESTAMP,
    constraint uk_kb_file
    unique (knowledge_base_id, file_id)
    );

alter table public.t_knowledge_base_file
    owner to postgres;

create table if not exists public.t_file_info
(
    id                bigserial
    primary key,
    original_filename varchar(255),
    file_name         varchar(255),
    file_extension    varchar(50),
    file_size         bigint,
    file_path         text,
    file_url          text,
    user_id           bigint,
    file_md5          varchar(32),
    create_time       timestamp default CURRENT_TIMESTAMP,
    update_time       timestamp default CURRENT_TIMESTAMP,
    file_type         varchar(50),
    mime_type         varchar(100),
    file_hash         varchar(64),
    source_type       smallint  default 1,
    source_note_id    bigint,
    source_note_title varchar(255),
    category          varchar(64)
    );

comment on table public.t_file_info is '文件上传信息表';

comment on column public.t_file_info.id is '主键ID';

comment on column public.t_file_info.original_filename is '原始文件名';

comment on column public.t_file_info.file_name is '存储在服务器上的文件名';

comment on column public.t_file_info.file_extension is '文件扩展名';

comment on column public.t_file_info.file_size is '文件大小（以字节为单位）';

comment on column public.t_file_info.file_path is '文件在服务器上的物理路径';

comment on column public.t_file_info.file_url is '文件的可访问URL';

comment on column public.t_file_info.user_id is '上传文件的用户ID';

comment on column public.t_file_info.file_md5 is '文件的MD5哈希值';

comment on column public.t_file_info.create_time is '记录创建时间';

comment on column public.t_file_info.update_time is '记录最后更新时间';

comment on column public.t_file_info.file_type is '文件类型（自定义分类，如文档、图片等）';

comment on column public.t_file_info.mime_type is '文件的MIME类型';

comment on column public.t_file_info.file_hash is '文件的哈希值（如SHA-256），用于校验完整性';

comment on column public.t_file_info.category is '知识库分类';

alter table public.t_file_info
    owner to postgres;

create index if not exists idx_file_info_category
    on public.t_file_info (category);

create index if not exists idx_file_info_user_category
    on public.t_file_info (user_id, category);

-- =====================
-- 博客分类表
-- =====================
create table if not exists public.t_blog_category
(
    id          bigserial primary key,
    name        varchar(50)  not null,
    description varchar(255),
    sort_order  integer   default 0,
    user_id     bigint       not null,
    create_time timestamp default CURRENT_TIMESTAMP,
    update_time timestamp default CURRENT_TIMESTAMP,
    constraint uk_blog_category_name_user unique (name, user_id)
);

comment on table public.t_blog_category is '博客分类表';
alter table public.t_blog_category owner to postgres;

-- =====================
-- 博客标签表
-- =====================
create table if not exists public.t_blog_tag
(
    id          bigserial primary key,
    name        varchar(50) not null,
    user_id     bigint      not null,
    create_time timestamp default CURRENT_TIMESTAMP,
    constraint uk_blog_tag_name_user unique (name, user_id)
);

comment on table public.t_blog_tag is '博客标签表';
alter table public.t_blog_tag owner to postgres;

-- =====================
-- 博客文章表
-- =====================
create table if not exists public.t_blog_post
(
    id           bigserial primary key,
    user_id      bigint       not null,
    title        varchar(255) not null,
    summary      varchar(1000),
    content_md   text,
    content_html text,
    cover_url    varchar(255),
    category_id  bigint references public.t_blog_category(id) on delete set null,
    status       smallint  default 0,
    is_top       boolean   default false,
    view_count   integer   default 0,
    source_note_id bigint,
    publish_time timestamp,
    create_time  timestamp default CURRENT_TIMESTAMP,
    update_time  timestamp default CURRENT_TIMESTAMP
);

comment on table public.t_blog_post is '博客文章表';
comment on column public.t_blog_post.status is '0-草稿 1-已发布';
comment on column public.t_blog_post.source_note_id is '来源笔记ID（笔记转博客时记录）';

create index if not exists idx_blog_user_id on public.t_blog_post(user_id);
create index if not exists idx_blog_status on public.t_blog_post(status);
create index if not exists idx_blog_publish_time on public.t_blog_post(publish_time);
create index if not exists idx_blog_category_id on public.t_blog_post(category_id);

alter table public.t_blog_post owner to postgres;

create table if not exists public.t_register_setting
(
    id               bigserial primary key,
    register_enabled boolean   not null default false,
    invite_code      varchar(64),
    create_time      timestamp default CURRENT_TIMESTAMP,
    update_time      timestamp default CURRENT_TIMESTAMP
);

alter table public.t_register_setting owner to postgres;

insert into public.t_register_setting (id, register_enabled, invite_code, create_time, update_time)
select 1, false, null, current_timestamp, current_timestamp
where not exists (
    select 1 from public.t_register_setting where id = 1
);

insert into public.t_user (phone, password, role_type, username, status, create_time, update_time)
select '13800000000',
       '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9',
       1,
       'admin',
       1,
       current_timestamp,
       current_timestamp
where not exists (
    select 1 from public.t_user where phone = '13800000000'
);

update public.t_user
set role_type = case when phone = '13800000000' then 1 else 2 end,
    update_time = current_timestamp;

-- =====================
-- 博客文章-标签关联表（多对多）
-- =====================
create table if not exists public.t_blog_post_tag
(
    id      bigserial primary key,
    post_id bigint not null references public.t_blog_post(id) on delete cascade,
    tag_id  bigint not null references public.t_blog_tag(id) on delete cascade,
    constraint uk_blog_post_tag unique (post_id, tag_id)
);

comment on table public.t_blog_post_tag is '博客文章-标签关联表';
create index if not exists idx_blog_post_tag_post on public.t_blog_post_tag(post_id);
create index if not exists idx_blog_post_tag_tag on public.t_blog_post_tag(tag_id);

alter table public.t_blog_post_tag owner to postgres;
