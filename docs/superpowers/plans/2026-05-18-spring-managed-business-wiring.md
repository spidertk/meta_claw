# Spring 托管业务装配 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将主链业务对象改为 Spring 管理，移除调用链上的手工业务装配，并把 Memory backend 选择改造成可扩展的注入式结构。

**Architecture:** 使用 `@Component` / `@Service` 管理稳定复用组件；对依赖运行时参数的 memory backend 使用 Spring 管理的 provider；CLI 与 runtime 只依赖注入的组件，不再自己拼装业务依赖图。

**Tech Stack:** Java 21, Spring Boot, Maven

---

## 主要改动

- prompt 组件注解化
- vessel 配置与模板组件注解化
- weixin converter 注解化
- memory backend provider 化，替代硬编码 `MemoryManagerFactory`
- CLI / runtime 调用方改为注入式依赖
- 收缩 `AppConfig` 中可通过组件扫描替代的手工 bean

## 任务拆分

### Task 1: 组件注解化

- `PromptContextFactory`
- `TemplateLoader`
- `SystemPromptBuilder`
- `VesselConfigLoader`
- `VesselTemplate`
- `WeixinMessageConverter`

### Task 2: Memory 装配重构

- 新增 short-term / long-term provider 接口
- 新增 JSONL / file provider 实现
- 新增 Spring 管理的 memory selector/service
- 删除或弱化 `MemoryManagerFactory` 的硬编码职责

### Task 3: 调用方改造

- `ChatCommand`
- `SessionsCommand`
- `VesselRuntime`
- `VesselManager`
- `InitCommand`
- `CreateCommand`
- `WeixinChannel`

### Task 4: 启动配置收敛

- 缩小 `AppConfig`
- 只保留确实需要启动期装配的 bean

### Task 5: 验证与状态同步

- 运行 P0 测试相关验证
- 运行 `./init.sh`
- 更新长期状态文件

## 自审

- 计划只处理业务服务与可复用组件，不机械清除值对象 `new`
- Memory 装配从硬编码工厂转为可注入扩展点
- 保持当前 P0 测试策略不扩大
