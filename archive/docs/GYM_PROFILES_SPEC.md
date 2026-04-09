# Gym Profiles & Environment Logic

## 1. Profiles
- **Home**: Default profile. Limited equipment (e.g., Dumbbells, Bench).
- **Work/Commercial**: Full equipment (Cables, Squat Rack, Machines).
- **Functionality**: Switching profiles in 'Settings' automatically filters the Exercise List to show only available movements.

## 2. Discovery via Gemini
- **Input**: User uploads a photo of the gym area or provides a description.
- **Logic**: Gemini identifies equipment and maps it to a Profile ID.
- **Action**: Update `UserSettings.activeGymProfile`.