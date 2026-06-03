<!--
  Meta-Claw Context Prompt Template v1.0
  =====================================
  此模板定义发送给 LLM 的【上下文提示词（Context Prompt）】。
  提供每次对话时的动态环境信息（时间、工作目录、用户偏好等），
  帮助 AI 理解"此刻正在发生什么"。

  渲染方式：
    PromptRenderer 读取本模板后，将 {xxx} 占位符替换为运行时数据。
    与 system.tmpl.md 不同，此处字段可能随每次请求变化（如 current_time）。

  占位符来源：
    {workspace}    → VesselConfigBundle.workspaceDir（当前 Vessel 工作目录）
    {current_time} → PromptContext.currentTime（渲染时生成的实时时间）
    {location}     → PromptContext.location（系统默认时区）
    {preferences}  → VesselProfile ## Preferences 段落（用户长期偏好）

  自定义方式：
    如需调整上下文格式，可直接修改本文件后重新编译；
    如需调整用户偏好内容，请编辑：
      ~/.meta-claw/vessels/<name>/vessel.profile.md 的 ## Preferences 段落
-->

{workspace}

## Runtime Context

- Current Time: {current_time}
- Location: {location}

{preferences}
