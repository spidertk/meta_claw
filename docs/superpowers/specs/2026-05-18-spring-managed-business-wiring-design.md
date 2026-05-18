# Spring 托管业务装配设计

## 目标

把当前散落在业务类中的手工对象装配收敛到 Spring 容器中，后续所有有生命周期、可复用、承载业务逻辑的对象都优先通过 Spring 注解方式管理，而不是在调用链上直接 `new`。

## 当前问题

当前仓库虽然已经接入 Spring，但不少主链路仍然保留“业务类自己拼装依赖图”的写法：

- `ChatCommand` 手工创建 `MemoryManagerFactory`、prompt 组件、LLM client 适配对象
- `SessionsCommand` 手工创建 `MemoryManagerFactory`
- `VesselRuntime` 手工创建 prompt 组件
- `VesselManager` 手工创建 `VesselConfigLoader`
- `InitCommand` / `CreateCommand` 手工创建 `VesselTemplate`
- `WeixinChannel` 手工创建 `WeixinMessageConverter`
- `MemoryManagerFactory` 内部继续硬编码创建具体 backend 与 manager

这种写法的问题不是“源码里出现了 `new`”，而是：

1. 业务类同时承担业务职责和依赖装配职责
2. backend 扩展时需要改调用方或工厂内部硬编码
3. Spring 容器已经存在，却没有被真正用于统一生命周期与依赖管理

## 设计原则

### 需要交给 Spring 管理的对象

以下对象属于可复用业务服务或基础组件，应作为 Spring Bean 管理：

- `PromptContextFactory`
- `TemplateLoader`
- `SystemPromptBuilder`
- `VesselConfigLoader`
- `VesselTemplate`
- `WeixinMessageConverter`
- memory backend provider / selector
- 可复用的 manager / service 组件

### 继续允许直接构造的对象

以下对象仍然可以正常 `new`：

- 值对象：`Reply`、`Context`、`SpiMessage`
- builder / DTO / 请求对象：`Prompt`、`SystemMessage`、`UserMessage`
- 临时工具对象：`StringBuilder`、集合、reader、lock
- 第三方 SDK 回调对象或匿名内部类

目标不是“仓库中完全没有 `new`”，而是“业务对象不再由业务调用方手工装配”。

## 选定方案

采用“组件优先，配置兜底”的装配方式：

1. 能稳定复用、无运行时参数依赖的对象，优先使用 `@Component` / `@Service`
2. 只有确实依赖外部配置或运行时参数的对象，才通过 Spring 管理的 provider / factory 暴露
3. `AppConfig` 只保留真正需要基于启动配置创建的 bean，不再继续堆积可通过组件扫描解决的对象

## Memory 装配

当前 `MemoryManagerFactory` 的问题在于它同时做了三件事：

1. 选择 backend
2. 创建 backend
3. 创建 manager

新的设计将它拆开：

- Spring 管理 backend provider 集合
- 每个 provider 声明自己支持的 backend 名称
- 统一的 memory selector 根据 `MemoryConfig` 选择 provider
- manager 不再由调用方或硬编码工厂手工创建

如果某个 backend 必须接收 `vesselsDir`、`vesselId` 等运行时参数，则由 Spring 管理的 provider 负责接收参数并返回结果，而不是让业务调用方直接 `new` 具体实现。

## 调用方改造

### CLI

- `ChatCommand` 注入：
  - prompt 相关 bean
  - memory selector / provider
  - 需要的 LLM 适配服务
- `SessionsCommand` 注入 memory selector / provider
- `InitCommand` 与 `CreateCommand` 注入 `VesselTemplate`

### Runtime

- `VesselRuntime` 注入 prompt 相关 bean，不再自己拼装 builder 链
- `VesselManager` 注入 `VesselConfigLoader`

### Gateway

- `WeixinChannel` 注入 `WeixinMessageConverter`

## AppConfig 收敛

`AppConfig` 只保留那些确实依赖启动期上下文的 bean，例如：

- 外部配置对象
- 必须基于文件路径或启停顺序创建的入口 bean

已经适合注解化的普通组件，应从 `AppConfig` 中移除，交给组件扫描。

## 错误处理

- 如果某个业务组件被移入 Spring 后暴露循环依赖，应优先重新审视职责边界，而不是退回手工 `new`
- 如果某个对象确实依赖运行时参数，使用 provider / factory bean，不在调用方直接构造实现类
- 如果某个对象只是值对象，不应为了形式统一而塞进 Spring 容器

## 测试策略

当前阶段继续只维护 P0 测试集，但需要确保以下主链行为不回归：

- `VesselConfigLoaderTest`
- `VesselManagerTest`
- `SystemPromptBuilderTest`
- `JsonlShortMemoryStoreTest`
- `FileLongMemoryStoreTest`
- `ChatCommandTest`
- `MessageFlowIntegrationTest`

验证顺序：

1. 运行必要的定向编译 / 测试
2. 运行仓库标准入口 `./init.sh`

## 非目标

- 不追求全仓彻底消灭 `new`
- 不把值对象、DTO、匿名回调塞进 Spring 容器
- 不在这一轮重写业务语义
- 不新增当前主链之外的新功能

## 预期结果

完成后：

- 主链业务对象由 Spring 管理，调用方不再自己拼装依赖图
- memory backend 扩展不再依赖修改硬编码工厂
- `AppConfig` 更薄，组件职责更清楚
- 代码风格真正利用 Spring 的依赖注入特性，而不是只在外层套了一个 Spring Boot 壳
