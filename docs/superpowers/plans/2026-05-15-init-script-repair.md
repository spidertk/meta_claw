# init.sh Standard Entry Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the stale npm-based root entry script with a truthful Maven-based workflow for the current Meta-Claw repository.

**Architecture:** Keep the repair local to `init.sh`: validate tool availability, run the canonical verification command, print the canonical startup command, and optionally `exec` it when requested. Then update the repository’s persistent state artifacts so future sessions know exactly what was fixed and what still remains unverified in the current environment.

**Tech Stack:** Bash, Maven, Markdown, JSON

---

### Task 1: Replace the stale npm workflow in `init.sh`

**Files:**
- Modify: `init.sh`

- [ ] **Step 1: Capture the current failing behavior**

Run:

```bash
./init.sh
```

Expected: FAIL because the current script tries to execute `npm install` in a repository without `package.json`.

- [ ] **Step 2: Replace the script body with Maven-aware commands**

Use this exact shape:

```bash
#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

VERIFY_CMD=(mvn clean test)
START_CMD=(mvn spring-boot:run -pl meta-claw-bootstrap -DskipTests)

echo "==> 当前目录: $PWD"
echo "==> 检查 Maven"
if ! command -v mvn >/dev/null 2>&1; then
  echo "错误：未找到 mvn。请先安装 Maven，再重新运行 ./init.sh。" >&2
  exit 127
fi

echo "==> 运行基础验证"
"${VERIFY_CMD[@]}"

echo "==> 启动命令"
printf '    %q' "${START_CMD[@]}"
printf '\n'

if [ "${RUN_START_COMMAND:-0}" = "1" ]; then
  echo "==> 启动应用"
  exec "${START_CMD[@]}"
fi

echo "如果希望 init.sh 直接启动应用，请设置 RUN_START_COMMAND=1。"
```

- [ ] **Step 3: Verify the new missing-Maven failure mode**

Run:

```bash
./init.sh
```

Expected in the current environment: FAIL quickly with a clear Maven-specific error and no npm invocation.

- [ ] **Step 4: Commit the script repair**

```bash
git add init.sh
git commit -m "fix: repair init script for maven workflow"
```

### Task 2: Sync persistent repository state after the repair

**Files:**
- Modify: `claude-progress.md`
- Modify: `clean-state-checklist.md`
- Modify: `evaluator-rubric.md`
- Modify: `feature_list.json`

- [ ] **Step 1: Update the progress log**

Record that:

- `init.sh` now uses Maven semantics
- the current environment still lacks `mvn`
- `./init.sh` now fails for the truthful reason (`mvn` missing), not because it tries npm
- the next blocker is restoring a runnable Maven path

- [ ] **Step 2: Update the clean-state checklist**

Reflect that:

- the script shape is now correct
- the standard verification path still cannot be marked clean until Maven is executable and tests really run

- [ ] **Step 3: Update the evaluator rubric**

Increase maintainability/correctness only if warranted, but keep verification/reliability conservative until a real Maven run succeeds.

- [ ] **Step 4: Update `feature_list.json`**

Keep `repo-001` as `in_progress` unless a real Maven-backed verification run succeeds. Add evidence that the stale npm workflow has been replaced and note the remaining `mvn` blocker.

- [ ] **Step 5: Validate the JSON**

Run:

```bash
python3 -m json.tool feature_list.json >/dev/null
```

Expected: PASS with exit code 0.

- [ ] **Step 6: Commit the state sync**

```bash
git add claude-progress.md clean-state-checklist.md evaluator-rubric.md feature_list.json
git commit -m "docs: update state after init script repair"
```

### Task 3: Final verification and handoff

**Files:**
- Review only

- [ ] **Step 1: Check the final worktree**

Run:

```bash
git status --short
```

Expected: only intentionally unresolved files remain, such as the pre-existing untracked `package-lock.json` if it has not yet been addressed.

- [ ] **Step 2: Summarize the repaired boundary**

State clearly:

- what is now fixed
- what still cannot be claimed
- the exact next action needed before new feature work resumes
