# War Room: Natural Language Commands

## 1. Action Dispatcher
- Gemini must recognize intents to:
    - "Set current weight to 92kg" -> Updates last set in active workout.
    - "My back hurts today (Level 7)" -> Updates Injury Tracker and triggers Noaa.
    - "Switch to Home gym" -> Changes Gym Profile.
- **Implementation**: Gemini returns a JSON 'ActionBlock' alongside its text response.