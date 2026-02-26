# Instructions for Claude Code

1. **Initialization:** Run `gradle init` or use the Android Studio template for a 'Compose Activity'.
2. **Architecture:** Use MVVM. Create a `repository` layer that handles both the Room DB and the Gemini API calls.
3. **Safety:** Ensure all Health Connect queries are wrapped in permission checks.
4. **Priority:**
    - Step 1: Create the Database and Workout Logger UI.
    - Step 2: Implement the "Vision" capture button using CameraX.
    - Step 3: Implement the "Committee Chat" using the System Prompts provided in COMMITTEE_LOGIC.md.