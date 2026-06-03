<!--
  Meta-Claw System Prompt Template v1.0
  =====================================
  此模板定义发送给 LLM 的【系统提示词（System Prompt）】。
  即 Vessel 的"人格说明书"，决定 AI 如何理解自己、如何回应用户。

  渲染方式：
    PromptRenderer 读取本模板后，将 {xxx} 占位符替换为 VesselConfigBundle
    中对应字段的实际值。若字段为空，该区块自动折叠（不输出空标题）。

  占位符来源：
    {vessel_name}        → VesselMeta.meta.name
    {vessel_description} → VesselMeta.meta.description
    {identity}           → VesselProfile ## Identity 段落
    {soul}               → VesselProfile ## Soul 段落
    {capabilities}       → VesselProfile ## Capabilities 段落
    {guidelines}         → VesselProfile ## Guidelines 段落
    {domain_knowledge}   → VesselProfile ## Domain Knowledge 段落

  自定义方式：
    不要直接修改本文件！如需调整 Vessel 人格，请编辑：
      ~/.meta-claw/vessels/<name>/vessel.profile.md
    修改后重启 CLI 或重新加载 Vessel 即可生效。
-->

# {vessel_name}

{vessel_description}

{identity}
{soul}
{capabilities}
{guidelines}
{domain_knowledge}
