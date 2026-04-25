# CrossFit Exercise Verification Results

Generated: 2026-04-24

Sources fetched:
- https://www.crossfit.com/crossfit-movements (official CF movement library)
- https://gym-mikolo.com/blogs/home-gym/the-ultimate-crossfit-exercises-list-master-every-movement-in-your-training-toolbox
- https://thefitnessphantom.com/wp-content/uploads/2022/01/CrossFit-Workout-List-PDF.pdf

---

## Section 1 — Confirmed List B Items (gap analysis validated ✅)

These gap analysis entries appear explicitly on the source pages:

| Gap Analysis Entry | Found on |
|---|---|
| Air Squat | crossfit.com (foundational movement) |
| Push Press | crossfit.com + gym-mikolo |
| Sumo Deadlift High Pull | crossfit.com |
| Box Jump Over | crossfit.com (as "Burpee Box Jump-over" confirms concept) |
| Wall Walk | crossfit.com |
| Legless Rope Climb | crossfit.com |
| Strict Handstand Push-Up | crossfit.com |
| Kipping Handstand Push-Up | crossfit.com |
| Ring Muscle-Up | crossfit.com (as "Kipping Muscle-up") |
| Dumbbell Walking Lunge | crossfit.com |
| Overhead Dumbbell Walking Lunge | crossfit.com (as "Dumbbell Overhead Walking Lunge") |
| Pegboard Climb | gym-mikolo |
| Sandbag Over Shoulder | gym-mikolo (as "D-Ball Over Shoulder" — same movement pattern) |

**Partially confirmed** (concept present but not exact name):
- Dumbbell Box Step-Up → "Box Step-up" on crossfit.com confirms; DB variant is standard Open programming
- Bar-Facing Burpee / Lateral Burpee Over Bar → multiple burpee variants on crossfit.com confirm these exist as distinct movements

---

## Section 2 — Additional Gaps Missed by First Pass ❌

These appear on the source pages but are NOT in List A (existing DB) or List B (already-identified gaps). Add to `func_exercise_expanded_seed`.

### From crossfit.com/crossfit-movements

```json
[
  {
    "name": "Butterfly Pull-Up",
    "muscleGroup": "Back",
    "equipmentType": "Pull-up Bar",
    "exerciseType": "STRENGTH",
    "familyId": "pullup_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"WRIST\",\"HIP\"]",
    "primaryMuscles": "[\"Latissimus Dorsi\",\"Biceps\"]",
    "secondaryMuscles": "[\"Core\",\"Hip Flexors\"]",
    "setupNotes": "A kipping pull-up variant where the hips drive in a circular arc (forward-up-back) rather than the standard front-back kip. The butterfly technique enables much higher rep rates than standard kipping and is the standard for fast CrossFit metcon sets. Prerequisite: solid kipping pull-up before learning the butterfly cycle. Requires good shoulder mobility and timing."
  },
  {
    "name": "Burpee Box Jump-Over",
    "muscleGroup": "Full Body",
    "equipmentType": "Other",
    "exerciseType": "STRENGTH",
    "familyId": "metcon_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"HIP\",\"KNEE\",\"ANKLE\",\"SHOULDER\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Glutes\",\"Quadriceps\",\"Hamstrings\"]",
    "secondaryMuscles": "[\"Chest\",\"Triceps\",\"Core\"]",
    "setupNotes": "Perform a burpee beside a box, then jump up and over the box landing on the other side. Unlike a standard box jump where you step down, the rep ends on the far side of the box. You can either jump onto the box and off the other side (two jumps) or broad jump directly over it. A CrossFit Open staple — appeared in Open 20.1 and multiple other workouts. The step-over modification is accepted in scaled divisions."
  },
  {
    "name": "Dumbbell Hang Power Clean",
    "muscleGroup": "Full Body",
    "equipmentType": "Dumbbell",
    "exerciseType": "STRENGTH",
    "familyId": "olympic_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"HIP\",\"KNEE\",\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"ANKLE\",\"WRIST\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Glutes\",\"Hamstrings\",\"Trapezius\",\"Deltoids\"]",
    "secondaryMuscles": "[\"Quadriceps\",\"Biceps\",\"Core\"]",
    "setupNotes": "Hold a dumbbell in each hand at hip height (hang position). Hinge slightly, then drive explosively through hips and shrug, pulling the dumbbells to shoulder height with elbows high. Drop under into a partial squat catching the dumbbells in a front-rack position (heads resting on shoulders). Official CrossFit movement — appears in Open programming. Each DB should finish with head resting on shoulder, elbows forward."
  },
  {
    "name": "Dumbbell Front Squat",
    "muscleGroup": "Legs",
    "equipmentType": "Dumbbell",
    "exerciseType": "STRENGTH",
    "familyId": "squat_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"KNEE\",\"HIP\",\"ANKLE\"]",
    "secondaryJoints": "[\"LUMBAR_SPINE\",\"SHOULDER\",\"ELBOW\"]",
    "primaryMuscles": "[\"Quadriceps\",\"Glutes\"]",
    "secondaryMuscles": "[\"Hamstrings\",\"Core\",\"Deltoids\"]",
    "setupNotes": "Hold a dumbbell in each hand resting on your shoulders in a front-rack position (heads on shoulders, elbows forward). Squat to full depth — hip crease below parallel — keeping your torso as upright as possible and elbows high. Drive through your heels to stand. Official CrossFit movement; the front rack DB position demands significant shoulder mobility and anterior core strength."
  },
  {
    "name": "Dumbbell Push Press",
    "muscleGroup": "Shoulders",
    "equipmentType": "Dumbbell",
    "exerciseType": "STRENGTH",
    "familyId": "press_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\",\"KNEE\"]",
    "secondaryJoints": "[\"HIP\",\"WRIST\",\"ANKLE\"]",
    "primaryMuscles": "[\"Deltoids\",\"Triceps\"]",
    "secondaryMuscles": "[\"Quadriceps\",\"Glutes\",\"Trapezius\"]",
    "setupNotes": "Hold a dumbbell in each hand at shoulder height. Dip slightly at the knees keeping your torso vertical, then drive explosively through your legs and press both dumbbells to full overhead lockout simultaneously. Lower under control. Official CrossFit movement — the dumbbell version is programmed in Open workouts when barbells aren't available and allows independent arm loading."
  },
  {
    "name": "Dumbbell Push Jerk",
    "muscleGroup": "Shoulders",
    "equipmentType": "Dumbbell",
    "exerciseType": "STRENGTH",
    "familyId": "press_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\",\"KNEE\",\"HIP\"]",
    "secondaryJoints": "[\"ANKLE\",\"WRIST\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Deltoids\",\"Triceps\",\"Quadriceps\"]",
    "secondaryMuscles": "[\"Glutes\",\"Core\",\"Trapezius\"]",
    "setupNotes": "Hold dumbbells at shoulder height. Dip and drive explosively, pressing the dumbbells overhead while simultaneously re-dipping under them to catch at full lockout — then stand to full extension. The re-dip under the bar distinguishes the jerk from the push press. Official CrossFit movement. Requires timing and coordination to catch the dumbbells in a stable overhead position."
  },
  {
    "name": "Dumbbell Front-Rack Lunge",
    "muscleGroup": "Legs",
    "equipmentType": "Dumbbell",
    "exerciseType": "STRENGTH",
    "familyId": "lunge_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"KNEE\",\"HIP\",\"ANKLE\"]",
    "secondaryJoints": "[\"LUMBAR_SPINE\",\"SHOULDER\",\"ELBOW\"]",
    "primaryMuscles": "[\"Quadriceps\",\"Glutes\"]",
    "secondaryMuscles": "[\"Hamstrings\",\"Core\",\"Deltoids\"]",
    "setupNotes": "Hold dumbbells on your shoulders in a front-rack position and perform alternating walking lunges. The front-rack position increases the core stability and shoulder endurance demand vs. dumbbells at the side. Official CrossFit movement. Keep elbows high throughout — dropping the elbows collapses the front rack and shifts load to the wrists."
  },
  {
    "name": "Dumbbell Overhead Squat",
    "muscleGroup": "Full Body",
    "equipmentType": "Dumbbell",
    "exerciseType": "STRENGTH",
    "familyId": "squat_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"KNEE\",\"HIP\",\"SHOULDER\",\"ANKLE\"]",
    "secondaryJoints": "[\"WRIST\",\"ELBOW\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Quadriceps\",\"Glutes\",\"Deltoids\"]",
    "secondaryMuscles": "[\"Core\",\"Hamstrings\",\"Rotator Cuff\",\"Trapezius\"]",
    "setupNotes": "Hold one or two dumbbells locked out overhead with arms straight and actively pressing into the handles. Squat to full depth (hip crease below parallel) while keeping the dumbbells directly over your base of support. The overhead position demands exceptional shoulder stability, thoracic mobility, and ankle flexibility. Official CrossFit movement — commonly used as a warm-up and in skill-based WODs."
  },
  {
    "name": "Dumbbell Power Clean",
    "muscleGroup": "Full Body",
    "equipmentType": "Dumbbell",
    "exerciseType": "STRENGTH",
    "familyId": "olympic_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"HIP\",\"KNEE\",\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"ANKLE\",\"WRIST\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Glutes\",\"Hamstrings\",\"Trapezius\"]",
    "secondaryMuscles": "[\"Quadriceps\",\"Deltoids\",\"Core\"]",
    "setupNotes": "With a dumbbell in each hand, hinge to the floor and perform a full clean pull — driving through your legs and hips, shrugging, and pulling the dumbbells to shoulder height. Unlike the hang power clean, the movement starts from the floor. Each rep catches in a partial squat with DB heads resting on shoulders. Official CrossFit movement."
  },
  {
    "name": "Snatch Balance",
    "muscleGroup": "Full Body",
    "equipmentType": "Barbell",
    "exerciseType": "STRENGTH",
    "familyId": "olympic_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"KNEE\",\"HIP\",\"SHOULDER\",\"ANKLE\"]",
    "secondaryJoints": "[\"WRIST\",\"ELBOW\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Quadriceps\",\"Glutes\",\"Deltoids\"]",
    "secondaryMuscles": "[\"Core\",\"Trapezius\",\"Rotator Cuff\"]",
    "setupNotes": "Start with the bar on your back in snatch-grip width. Push-press the bar overhead and simultaneously drop into a full overhead squat, catching the bar locked out at the bottom. Stand to finish. The snatch balance trains the speed under the bar and receiving position of the snatch. Appears in CF weightlifting programming and skill-focused classes."
  },
  {
    "name": "Muscle Snatch",
    "muscleGroup": "Shoulders",
    "equipmentType": "Barbell",
    "exerciseType": "STRENGTH",
    "familyId": "olympic_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"HIP\",\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"KNEE\",\"WRIST\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Trapezius\",\"Deltoids\",\"Glutes\"]",
    "secondaryMuscles": "[\"Hamstrings\",\"Triceps\",\"Core\"]",
    "setupNotes": "Perform a snatch pull but press the bar overhead without re-bending the knees or dropping under the bar — the bar goes from floor to overhead in a single continuous press with no re-dip. The muscle snatch builds the pulling strength and overhead turnover for the full snatch. Official CrossFit skill-development movement. Typically done at lighter weights than the full snatch."
  },
  {
    "name": "Hang Snatch",
    "muscleGroup": "Full Body",
    "equipmentType": "Barbell",
    "exerciseType": "STRENGTH",
    "familyId": "olympic_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"HIP\",\"KNEE\",\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"ANKLE\",\"WRIST\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Glutes\",\"Hamstrings\",\"Trapezius\",\"Deltoids\"]",
    "secondaryMuscles": "[\"Quadriceps\",\"Core\",\"Rotator Cuff\"]",
    "setupNotes": "Starting from a hang position (bar at hip/knee height), perform a full squat snatch — driving through the hips, pulling under, and catching the bar overhead in a full squat. Unlike the hang power snatch (already in DB), the catch is in a full squat below parallel. Official CrossFit movement; excellent for developing snatch timing and hip extension."
  },
  {
    "name": "Strict Bar Muscle-Up",
    "muscleGroup": "Back",
    "equipmentType": "Pull-up Bar",
    "exerciseType": "STRENGTH",
    "familyId": "gymnastics_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"WRIST\"]",
    "primaryMuscles": "[\"Latissimus Dorsi\",\"Chest\",\"Triceps\"]",
    "secondaryMuscles": "[\"Biceps\",\"Core\",\"Deltoids\"]",
    "setupNotes": "Hang from a pull-up bar and perform a muscle-up without any kipping swing — pulling strictly until your hips reach bar height, then pressing to lockout above the bar. Significantly harder than the kipping bar muscle-up. Official CrossFit movement. Requires exceptional pulling strength and the ability to transition smoothly from pull to press at the top."
  },
  {
    "name": "Chest-to-Wall Handstand Push-Up",
    "muscleGroup": "Shoulders",
    "equipmentType": "Bodyweight",
    "exerciseType": "STRENGTH",
    "familyId": "gymnastics_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\",\"WRIST\"]",
    "secondaryJoints": "[\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Deltoids\",\"Triceps\"]",
    "secondaryMuscles": "[\"Core\",\"Serratus Anterior\",\"Trapezius\"]",
    "setupNotes": "Kick up to a handstand facing the wall so your chest (not back) is toward the wall. Lower your head to the floor and press back up. The chest-to-wall position forces a straighter body line — no hollow back allowed — making this stricter than the standard back-to-wall HSPU. Required for high-level CrossFit competition standards. Prerequisite: solid back-to-wall strict HSPU."
  },
  {
    "name": "Zercher Squat",
    "muscleGroup": "Legs",
    "equipmentType": "Barbell",
    "exerciseType": "STRENGTH",
    "familyId": "squat_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"KNEE\",\"HIP\",\"ELBOW\"]",
    "secondaryJoints": "[\"ANKLE\",\"SHOULDER\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Quadriceps\",\"Glutes\"]",
    "secondaryMuscles": "[\"Core\",\"Upper Back\",\"Biceps\"]",
    "setupNotes": "Hold the barbell in the crooks of your elbows, cradled against your forearms and upper arms. Squat to full depth keeping the bar close to your torso and your elbows pointing forward. The anterior loading demands extreme core bracing and upper back strength. Listed on crossfit.com's official movement library. Excellent for developing squat mechanics and core strength."
  },
  {
    "name": "Slam Ball",
    "muscleGroup": "Full Body",
    "equipmentType": "Other",
    "exerciseType": "STRENGTH",
    "familyId": "metcon_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"SHOULDER\",\"HIP\",\"LUMBAR_SPINE\"]",
    "secondaryJoints": "[\"KNEE\",\"ELBOW\"]",
    "primaryMuscles": "[\"Core\",\"Deltoids\",\"Glutes\"]",
    "secondaryMuscles": "[\"Latissimus Dorsi\",\"Hamstrings\",\"Triceps\"]",
    "setupNotes": "Lift a heavy non-bouncing slam ball (20–100 lb) from the floor to overhead, then explosively throw it straight down into the floor. Pick it up and repeat. Unlike the medicine ball slam (which uses a lighter bouncing ball), a slam ball is dense and designed to absorb impact — it will not bounce back. Common in CF metcons as a full-body power endurance exercise."
  }
]
```

### From gym-mikolo.com (additional functional movements)

```json
[
  {
    "name": "Tire Flip",
    "muscleGroup": "Full Body",
    "equipmentType": "Other",
    "exerciseType": "STRENGTH",
    "familyId": "metcon_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"HIP\",\"KNEE\",\"SHOULDER\",\"LUMBAR_SPINE\"]",
    "secondaryJoints": "[\"ANKLE\",\"ELBOW\",\"WRIST\"]",
    "primaryMuscles": "[\"Glutes\",\"Hamstrings\",\"Quadriceps\",\"Upper Back\"]",
    "secondaryMuscles": "[\"Core\",\"Deltoids\",\"Biceps\",\"Chest\"]",
    "setupNotes": "Stand facing a large tractor tire. Squat low and grip the underside of the tire with both hands, driving it up and forward by extending your hips and legs explosively (like a deadlift-to-push). As the tire rises, transition to a pushing position and drive it up and over onto its other face. A CrossFit garage gym and functional fitness staple. Requires a large tire — typically 200–600+ lb. Drive through your hips first, then push — do not pull with your lower back."
  },
  {
    "name": "Man Maker",
    "muscleGroup": "Full Body",
    "equipmentType": "Dumbbell",
    "exerciseType": "STRENGTH",
    "familyId": "metcon_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\",\"HIP\",\"KNEE\"]",
    "secondaryJoints": "[\"WRIST\",\"ANKLE\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Full Body\"]",
    "secondaryMuscles": "[]",
    "setupNotes": "Hold a dumbbell in each hand in push-up position. Perform a push-up, then row each dumbbell to your hip one at a time, then jump your feet in and stand (or clean the DBs) to perform a thruster. One rep = push-up + row R + row L + thruster. A notorious CrossFit metcon movement that combines every major movement pattern into one rep. Typically done at lighter weights for conditioning."
  },
  {
    "name": "Yoke Walk",
    "muscleGroup": "Full Body",
    "equipmentType": "Other",
    "exerciseType": "TIMED",
    "familyId": "carry_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"LUMBAR_SPINE\",\"KNEE\",\"HIP\"]",
    "secondaryJoints": "[\"ANKLE\",\"SHOULDER\"]",
    "primaryMuscles": "[\"Quadriceps\",\"Glutes\",\"Core\",\"Upper Back\"]",
    "secondaryMuscles": "[\"Hamstrings\",\"Calves\",\"Trapezius\"]",
    "setupNotes": "Carry a yoke (a weighted frame resting across the upper back and shoulders) for a prescribed distance or time. The yoke typically loads 1.5–3× bodyweight and is carried in a similar position to a high-bar squat but while walking. A strongman movement that appears in CF-influenced fitness competitions and garage gym programming. Requires keeping the core braced and taking controlled steps — wobbling accelerates fatigue."
  }
]
```

---

## Section 3 — Not Found on Source Pages

List B items that don't appear explicitly on the fetched pages (they may still be valid CF movements or Open-derived movements, just not on these particular reference pages):

| Entry | Assessment |
|---|---|
| Dumbbell Box Step-Up | Confirmed by "Box Step-up" on crossfit.com — dumbbell variant is standard |
| Bar-Facing Burpee | Not explicitly named but widely used in CF Open programming — keep |
| Lateral Burpee Over Bar | Same — valid Open movement, not on these pages but unambiguously CF |
| Sandbag Over Shoulder | gym-mikolo has "D-Ball Over Shoulder" — same movement, different implement. Keep as Sandbag variant |

**Recommendation:** Keep all List B items. The source pages aren't exhaustive — they're reference libraries, not complete WOD movement databases.

---

## Summary

| Category | Count |
|---|---|
| List B items confirmed by sources | 13 confirmed + 4 reasonable |
| Net-new gaps missed by first pass | 16 CrossFit + 3 gym-mikolo = **19 additional** |
| Total new exercises across both passes | 42 (first pass) + 19 (this pass) = **61 net-new** |

**Notable exerciseType note:** All entries above use `STRENGTH` for dynamic movements (not `WEIGHTED` or `BODYWEIGHT`). This matches the actual schema values in `master_exercises.json`.
