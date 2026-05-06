
## 2026-05-02 17:38

### CLI 命令增强
- [x] 新增 `list` 命令 — 扫描 `~/.meta-claw/vessels/`，表格展示 ID/Name/Description/Model
- [x] 新增 `delete` 命令 — 删除指定 vessel，支持 `--yes` 跳过确认
- [x] 增强 `chat` 交互体验 — 参考 `cli.py` 增加欢迎屏幕（emoji + 名称 + 描述 + model + provider）
- [x] `MetaClawCommand` 注册新子命令

### 运行验证
```
$ meta-claw list
┌──────────┬──────────────┬─────────────┬───────────┐
│ ID       │ Name         │ Description │ Model     │
├──────────┼──────────────┼─────────────┼───────────┤
│ default  │ Default Ves… │ A general…  │ kimi-k2.5 │
└──────────┴──────────────┴─────────────┴───────────┘

$ meta-claw chat default
╔════════════════════════════════════════════════════════════╗
║                                                            ║
║   🤖  Default Vessel                                       ║
║                                                            ║
║   A general-purpose AI assistant                           ║
║                                                            ║
║   Model: kimi-k2.5                                         ║
║   Provider: moonshot                                       ║
║                                                            ║
╚════════════════════════════════════════════════════════════╝
```

---
