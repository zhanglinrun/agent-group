create extension if not exists vector;

create table if not exists knowledge_embedding (
  id bigserial primary key,
  fragment_id varchar(32) not null,
  document_id varchar(32) not null,
  goods_id varchar(32) not null,
  knowledge_version varchar(32) not null,
  content text not null,
  embedding vector(1024),
  create_time timestamp not null default now()
);

create unique index if not exists uk_knowledge_embedding_fragment
  on knowledge_embedding(fragment_id);

create index if not exists idx_knowledge_embedding_goods
  on knowledge_embedding(goods_id);

create index if not exists idx_knowledge_embedding_vector
  on knowledge_embedding
  using hnsw (embedding vector_cosine_ops);
