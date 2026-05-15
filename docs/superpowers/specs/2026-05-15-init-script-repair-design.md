# init.sh 标准入口修复设计

> 日期：2026-05-15  
> 范围：仅修复仓库根目录 `init.sh`，使其与当前 Java/Maven 多模块仓库一致。

## 背景

当前 `init.sh` 仍保留 npm 模板命令：

- `npm install`
- `npm test`
- `npm run dev`

但 Meta-Claw 当前已是 Java 21 + Maven 多模块仓库，根目录不存在 `package.json`。因此 2026-05-15 执行 `./init.sh` 会在 `npm install` 阶段失败，破坏了 `AGENTS.md` 约定的统一开工入口。

## 目标

让 `./init.sh` 成为仓库真实的一键入口：

1. 先检查 `mvn` 是否可用
2. 再执行全量基础验证
3. 最后打印真实启动命令
4. 当 `RUN_START_COMMAND=1` 时，直接启动 bootstrap 应用

## 非目标

- 不在本次修复中安装 Maven
- 不新增 Maven Wrapper
- 不处理旧错误入口生成的 `package-lock.json`
- 不顺手修复其他文档、语义残留或功能缺口

## 设计

### 命令流

```text
进入仓库根目录
   ↓
检查 mvn 是否存在
   ↓
执行 mvn clean test
   ↓
打印 mvn spring-boot:run -pl meta-claw-bootstrap -DskipTests
   ↓
若 RUN_START_COMMAND=1，则 exec 启动命令
```

### 具体脚本行为

- 保留 `set -euo pipefail`
- 使用 `command -v mvn` 检查 Maven
- Maven 缺失时：
  - 向 stderr 输出明确错误
  - 返回非零退出码
- 验证命令：
  - `mvn clean test`
- 启动命令：
  - `mvn spring-boot:run -pl meta-claw-bootstrap -DskipTests`
- 不再出现“同步依赖”这类 npm 语义文案，改为“检查 Maven”“运行基础验证”

## 验证

### 当前环境验证

当前 shell 中 `mvn` 不可用，因此修复后应验证：

1. `./init.sh` 快速失败
2. 输出明确包含 Maven 缺失说明
3. 不再尝试执行 npm

### Maven 可用环境验证

在具备 Maven 的环境中，应验证：

1. `./init.sh` 执行 `mvn clean test`
2. 测试通过后打印 bootstrap 启动命令
3. `RUN_START_COMMAND=1 ./init.sh` 会执行 bootstrap 启动命令

## 状态记录

完成后需要同步：

- `claude-progress.md`
- `clean-state-checklist.md`
- `evaluator-rubric.md`
- `feature_list.json`

但只有在标准验证真实跑通后，`repo-001` 才能从 `in_progress` 升为 `passing`。
