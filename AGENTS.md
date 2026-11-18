# AGENTS.md

**STOP — before any action (reading code, running commands, editing files), open and read [CLAUDE.md](CLAUDE.md) in full.** It is the single guide for all AI tools (Claude, ChatGPT/Codex, others): stack, conventions, non-negotiable privacy rules, git rule and the "do Txx" workflow. If you cannot read it, stop and tell the user.

Key rules (details in CLAUDE.md — CLAUDE.md wins if anything differs):
- `git pull --rebase` first; commit straight to `main` with messages starting with the task ID (`T37.2: …`); never force-push. Codex Cloud PRs must be merged into `main` right away.
- Find tasks in `trackers/INDEX.md`; read only that task's section and its dependencies' Handoff notes; stop if a dependency is not `done`.
- Never claim tests passed without showing real command output. Tick only verified subtasks.
- Full SMS text never leaves the phone; the LLM never gets names/merchants/account data.

For chat tools without repo access, paste: the "Stack", "Conventions", "Non-negotiable rules" and "How to do a task" sections of CLAUDE.md + the single task section from the tracker file + its dependencies' Handoff notes.
