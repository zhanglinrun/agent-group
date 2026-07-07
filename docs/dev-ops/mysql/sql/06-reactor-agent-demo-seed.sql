-- After import, sync LLM key from repo-root .env (do not commit keys):
--   . docs/dev-ops/load-root-env.ps1
--   update ai_client_api.api_key from DASHSCOPE_API_KEY / AGENT_GROUP_LLM_API_KEY
-- Then armory runtime (Bearer token required):
--   POST /armory_api  body: {"apiId":"api-dashscope-default"}
--   POST /armory_agent body: {"agentId":"AGENT_CHAT_DEFAULT"}
-- Grant test quota: insert into user_quota_account (user_id, quota_balance) ...

use agent_group;
set names utf8mb4;

insert into ai_client_api (api_id, base_url, api_key, completions_path, embeddings_path, status)
values (
  'api-dashscope-default',
  'https://dashscope.aliyuncs.com/compatible-mode/v1',
  'not-configured',
  '/chat/completions',
  '/embeddings',
  1
) on duplicate key update
  base_url = values(base_url),
  api_key = values(api_key),
  status = values(status);

insert into ai_client_model (model_id, model_name, model_type, model_usage, api_id, status)
values (
  'model-qwen-plus-chat',
  'qwen-plus',
  'chat',
  'chat',
  'api-dashscope-default',
  1
) on duplicate key update
  model_name = values(model_name),
  api_id = values(api_id),
  status = values(status);

insert into ai_client (client_id, client_name, description, status)
values (
  'client-chat-default',
  'Default Chat Client',
  'Local demo Fix role client',
  1
) on duplicate key update
  client_name = values(client_name),
  status = values(status);

insert into ai_client_system_prompt (prompt_id, prompt_name, prompt_content, status)
values (
  'prompt-chat-default',
  'Default Chat Prompt',
  'You are a helpful assistant. Reply in concise Chinese.',
  1
) on duplicate key update
  prompt_content = values(prompt_content),
  status = values(status);

insert into ai_client_config (source_type, source_id, target_type, target_id, status)
values
  ('client', 'client-chat-default', 'model', 'model-qwen-plus-chat', 1),
  ('client', 'client-chat-default', 'prompt', 'prompt-chat-default', 1)
on duplicate key update status = values(status);

insert into ai_agent (agent_id, agent_name, description, channel, strategy, flow_step_count, status)
values (
  'AGENT_CHAT_DEFAULT',
  'Chat Assistant',
  'Local demo default Fix role',
  'fix',
  'flow',
  1,
  1
) on duplicate key update
  agent_name = values(agent_name),
  description = values(description),
  status = values(status);

insert into ai_agent_flow_config (agent_id, client_id, client_name, client_type, sequence, step_prompt)
values (
  'AGENT_CHAT_DEFAULT',
  'client-chat-default',
  'Default Chat Client',
  'chat',
  1,
  'Answer the user question accurately and concisely.'
) on duplicate key update
  step_prompt = values(step_prompt);
