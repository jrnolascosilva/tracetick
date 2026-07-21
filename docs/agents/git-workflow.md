# Git Workflow

How engineering work is landed in this repo. Read this **before** the first commit on a new ticket.

## The rule

Work lands on `main` via a pull request — never by pushing to `main` directly.

A CI workflow (`ci: drop push-to-main trigger`, commit `7b92811`) intentionally refuses
builds on push-to-main, so an unwary direct push also bypasses CI.

## Per-ticket flow

For a ticket `TN` titled something like "Password reset":

1. **Cut a branch from up-to-date `main`.**
   ```bash
   git checkout main
   git pull --ff-only
   git checkout -b t<N>-<kebab-topic>
   ```
   Branch naming, observed across T1–T3:
   - `t1-foundation`
   - `t2-auth`
   - `t3-password-reset`

2. **Commit on the feature branch, not on `main`.** The `implement` skill's "Commit your
   work to the current branch" line applies *after* step 1 has put you on a ticket branch;
   it does not override the rule above.

3. **Push the branch and open a PR.**
   ```bash
   git push -u origin t<N>-<kebab-topic>
   gh pr create --base main --head t<N>-<kebab-topic --title "TN: <ticket title>"
   ```
   The PR title becomes the linear-history commit on `main` after squash-merge — that is
   why the merged commit messages read `T3: Password reset (#17)` rather than a list of
   the in-flight commit titles.

4. **CI runs on the PR branch**, not after merge.

## If you already committed to `main`

Do not `git revert`. The clean fix is to relocate the commit, not rewind history (rewinding
`main` while others have it checked out corrupts their worktrees).

```bash
git reset --hard origin/main            # local main back to its origin tip
git branch t<N>-<kebab-topic> <your-SHA> # create branch at the misplaced commit
git checkout t<N>-<kebab-topic>         # work on the branch from here
```

The commit is *moved*, not recreated — no SHA churn, no rebase noise. After the reset,
`main` matches `origin/main` exactly. Pushing the branch and opening a PR proceeds normally.

## Why not the other options

- **`git revert origin/main`** — produces an "undo" commit on `main` that pollutes history
  and can't be cleanly un-reverted later. Avoid.
- **`git cherry-pick` onto a new branch** — gives the work a new SHA; CI checks and any
  external references keyed on the original SHA break.
- **`git push --force` to rewrite `origin/main`** — never. The repo's prior PRs all
  preserved the linear `main` history; rewriting it would discard every merged commit's
  SHA.

## Cross-references

- `AGENTS.md` — top-level index of agent conventions.
- `docs/agents/issue-tracker.md` — how issues and PRs surface in the tracker.
