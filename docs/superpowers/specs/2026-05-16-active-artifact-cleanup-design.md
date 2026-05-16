# 活跃工件遗留项清理设计

> 日期：2026-05-16  
> 范围：清理当前会继续误导开发者的活跃工件，不抹除历史文档中的迁移语境。

## 背景

标准入口恢复后，仓库仍存在一批遗留语义：

- 活跃工件中仍写着 `Expert`、`专家`、`meta-claw-session`
- 部分 README / POM / 资源注释与当前实现已经不一致
- `SessionManagerTest` 的测试文案仍沿用旧术语

这些内容虽然不影响编译，却会让后来者在理解系统时产生噪音。

## 目标

把当前活跃工件统一到真实现状：

1. 当前模块结构只描述实际存在的模块
2. 当前注释、描述与测试文案统一使用 `Vessel` / `数字员工`
3. 当前资源配置注释不再引用旧的 `expert.yaml`
4. 迁移历史文档中本来用于解释“从 Expert 到 Vessel”的段落继续保留

## 非目标

- 不重写历史路线图、旧设计、旧实施计划中的迁移记录
- 不改变业务逻辑
- 不顺手处理 `chat-001`、工具引擎、MCP、Skill 等后续功能

## 清理范围

### 需要修改

- `README.md`
- 根 `pom.xml`
- `meta-claw-core/pom.xml`
- `meta-claw-cli/pom.xml`
- `meta-claw-bootstrap/pom.xml`
- `meta-claw-bootstrap/src/main/resources/application.yml`
- `meta-claw-core/src/test/java/meta/claw/core/session/SessionManagerTest.java`

### 明确保留

- `docs/superpowers/specs/*`
- `docs/superpowers/plans/*`
- 其他明确描述迁移历史的文档段落

## 设计原则

- **当前事实优先**：README、POM 描述、运行配置注释应该只描述现在的系统
- **历史证据保留**：迁移文档中对旧术语的引用是上下文，不是脏数据
- **只改语言，不改行为**：这轮不触碰运行逻辑

## 验证

1. 在活跃工件范围内执行：

   ```bash
   rg -n "Expert|专家|meta-claw-session|ExpertRuntime|targetExpert|expertName" \
     README.md pom.xml meta-claw-* \
     --glob '!**/target/**'
   ```

2. 期望：仅历史文档之外的活跃工件结果为空
3. 执行 `./init.sh`
4. 期望：全量验证继续通过

## 状态记录

完成后同步：

- `claude-progress.md`
- `clean-state-checklist.md`
- `evaluator-rubric.md`
- `feature_list.json`

当活跃工件扫描为空且验证通过后，`semantic-001` 可以标记为 `passing`。
