Wrap up a completed feature implementation. Argument: short feature name (e.g. `superset_organize_mode`). If no argument given, infer from recent changes.

## Steps

### 1. Identify the feature
- Argument: `$ARGUMENTS`
- If blank, run `git diff HEAD~1 --name-only` and `git status` to infer from changed files

### 2. Update relevant spec files
- Find which `*_SPEC.md` files cover the changed domain (see Feature Specs table in `CLAUDE.md`)
- Update every spec that is now out of date — new state machines, UI components, data contracts, invariants
- If the feature adds a new spec file, link it in the Feature Specs table in `CLAUDE.md`

### 3. Update `CLAUDE.md` "Current State" section
- Add new features to the **Main Features Implemented** list
- Update **Database** section if schema version changed
- Update **Unit Tests** count if new tests were added
- Update any other facts (entity count, DAO count, etc.) that changed

### 4. Update `DB_UPGRADE.md` (only if schema changed)
- Document the migration: version bump, new tables/columns, migration SQL

### 5. Append to `plans.json`
- Add a new entry at the end of the JSON array:
  ```json
  { "plan": "<feature name> — <concise description of what was built: screens, ViewModels, DAOs, migrations, test files touched>.", "timestamp": "<ISO-8601 UTC now>" }
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
feat: <short description of the feature>

<2-3 line body: what was built, key files/components, any DB version bump>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```
Then `git push`.

### 9. Output QA checklist
Print a **"How to QA"** section covering:
- Happy path steps to exercise the new feature on device
- Edge cases to verify
- Any screens or flows to regression-check
