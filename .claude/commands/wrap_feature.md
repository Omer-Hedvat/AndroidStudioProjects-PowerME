Wrap up a completed feature implementation. Argument: short feature name (e.g. `superset_organize_mode`). If no argument given, infer from recent changes.

## Steps

### 1. Identify the feature
- Argument: `$ARGUMENTS`
- If blank, run `git diff HEAD~1 --name-only` and `git status` to infer from changed files

### 2. Update `ROADMAP.md`
- Verify the feature's row is currently in `completed` status (if it's still `in-progress` or `not-started`, stop and flag to the user — dev work isn't done yet)
- Change the row's status to `wrapped`
- If the feature was split into steps, mark each completed step as `wrapped`

### 3. Update the feature's spec file
- If built from a `future_devs/<NAME>_SPEC.md`: update its status header to `done`, append a **How to QA** section at the bottom
- If the feature lives in an implemented spec (e.g. `WORKOUT_SPEC.md`): update any sections that are now out of date — new state machines, UI components, data contracts, invariants

### 4. Update `CLAUDE.md` "Current State" section
- Add the feature to the **Main Features Implemented** list
- Update **Database** section if schema version changed
- Update **Unit Tests** count if new tests were added
- Move the spec file row from the **Future** table to **Implemented** (if applicable)

### 5. Update `DB_UPGRADE.md` (only if schema changed)
- Document the migration: version bump, new tables/columns, migration SQL

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
