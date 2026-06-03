<!--
  Meta-Claw Vessel Profile Template v1.0
  =====================================
  此文件是新建 Vessel 时的 Markdown 人格模板。
  初始化时，VesselInitializer 会将 {name} / {created_at} / {description}
  替换为用户输入的实际值。

  文件作用：
    定义 Vessel 的"人格说明书"，最终被渲染进发送给 LLM 的 System Prompt。
    每个 ## 标题对应 prompt 模板 system.tmpl.md 中的一个 {xxx} 占位符。

  段落说明：
    ## Identity      — Vessel 是谁？（角色定位、身份描述）
    ## Soul          — Vessel 的核心理念/价值观（语气风格、行为准则）
    ## Capabilities  — Vessel 能做什么？（技能清单、擅长领域）
    ## Guidelines    — Vessel 的行为边界（必须遵守的规则、禁止事项）
    ## Domain Knowledge — Vessel 的专业知识库（领域术语、背景知识）

  自定义方式：
    直接编辑 ~/.meta-claw/vessels/<name>/vessel.profile.md 的各个段落。
    某个段落留空时，该段落不会出现在最终的 System Prompt 中。
-->

## Identity

{name} 是一个 AI 数字员工，创建于 {created_at}。
{description}

## Soul

## Capabilities

## Guidelines

## Domain Knowledge
