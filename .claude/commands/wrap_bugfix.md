Wrap up a completed bug fix. Argument: bug slug (e.g. `superset_color_collision`). If no argument given, infer the bug from recent changes.

## Steps

### 1. Identify the bug
- Argument: `$ARGUMENTS`
- If blank, run `git diff HEAD~1 --name-only` and `git status` to infer which bug was just fixed from changed files
- Find the file: `bugs_to_fix/BUG_<slug>.md`

### 2. Update the bug file (`bugs_to_fix/BUG_<slug>.md`)
- Set `## Status` to `[x] Fixed`
- Fill in `## Fix Notes` with a concise summary: what was root-caused, what changed, which files

### 3. Update `bugs_to_fix/BUG_TRACKER.md`
- Change the row's status from `Fixed (uncommitted)` → `✅ Fixed & Committed`
- Update the **Files Changed** column to match what was actually changed

### 4. Update relevant spec files
- Read the changed source files to determine which domain was touched
- Update any `*_SPEC.md` references that still point to the old behavior
- Update `CLAUDE.md` "Current State" section only if an architectural invariant changed

### 5. Append to `plans.json`
- Add a new entry at the end of the JSON array:
  ```json
  { "plan": "Bug fix BUG_<slug>: <one-sentence summary of root cause and fix>. Files: <comma-separated list>.", "timestamp": "<ISO-8601 UTC now>" }
  ```
- Never remove or overwrite existing entries

### 6. Run /simplify
- Invoke the simplify skill to review changed code for quality and efficiency

### 7. Build + test
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
/Users/omerhedvat/.gradle/wrapper/dists/gradle-9.1.0-bin/9agqghryom9wkf8r80qlhnts3/gradle-9.1.0/bin/gradle \
  -p /Users/omerhedvat/git/AndroidStudioProjects-PowerME :app:testDebugUnitTest
```
Fix any regressions before continuing.

### 8. Commit and push
Stage all changed files and commit:
```
fix: <short description of the bug that was fixed>

<2-3 line body: what the root cause was, what the fix does>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```
Then `git push`.

### 9. Output QA checklist
Print a short **"How to QA"** section the user can run on device to verify the fix.
