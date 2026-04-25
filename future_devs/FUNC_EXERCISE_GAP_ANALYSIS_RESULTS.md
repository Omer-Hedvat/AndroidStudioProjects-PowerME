# Exercise Gap Analysis Results — CrossFit / Hyrox / Calisthenics

Generated: 2026-04-24

## Methodology

Cross-referenced movements from:
- CrossFit benchmark "Girl" WODs (Fran, Helen, Grace, Isabel, Annie, Diane, Elizabeth, Angie, Barbara, Chelsea, Cindy, Karen, Linda, Nancy, Amanda, Jackie, Eva, Kelly, Nicole, Mary, Nate, Lynne), Hero WODs (Murph, DT, Filthy Fifty, etc.), and CrossFit Open/Quarterfinals/Semifinals 2019–2024 competition movements
- Official Hyrox race 8 stations + common Hyrox accessory/training work
- Calisthenics progression families: planche, front lever, back lever, ring skills, one-arm pull-up progressions, human flag, manna

Against the 265 exercises currently in `master_exercises.json`.

## Summary

- CrossFit net-new: 16 exercises
- Hyrox net-new: 4 exercises
- Calisthenics net-new: 22 exercises
- Existing exercises needing retag: 41

---

## CrossFit Net-New Exercises

These movements appear regularly in CrossFit programming (Open, Quarterfinals, Semifinals, benchmark WODs) but are absent from the database.

```json
[
  {
    "name": "Air Squat",
    "muscleGroup": "Legs",
    "equipmentType": "Bodyweight",
    "exerciseType": "BODYWEIGHT",
    "familyId": "squat_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"KNEE\",\"HIP\"]",
    "secondaryJoints": "[\"ANKLE\"]",
    "primaryMuscles": "[\"Quadriceps\",\"Glutes\"]",
    "secondaryMuscles": "[\"Hamstrings\",\"Calves\"]",
    "setupNotes": "Stand with feet shoulder-width apart and toes slightly out. Keeping your chest tall and core braced, push your knees out and lower your hips until your thighs reach parallel or below — aim for full depth. Drive through your heels to stand back up. This is CrossFit's foundational movement and the strict standard for many WODs."
  },
  {
    "name": "Push Press",
    "muscleGroup": "Shoulders",
    "equipmentType": "Barbell",
    "exerciseType": "WEIGHTED",
    "familyId": "press_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"SHOULDER\",\"KNEE\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\",\"HIP\",\"ANKLE\"]",
    "primaryMuscles": "[\"Deltoids\",\"Triceps\"]",
    "secondaryMuscles": "[\"Quadriceps\",\"Glutes\",\"Upper Trapezius\"]",
    "setupNotes": "Hold the barbell in a front rack position. Dip slightly at the knees keeping your torso vertical, then drive explosively through your legs and press the bar overhead to full lockout. Lower under control back to front rack. The leg drive is what separates the push press from a strict press — use it to move significantly heavier loads. Don't let your torso lean back as you dip."
  },
  {
    "name": "Sumo Deadlift High Pull",
    "muscleGroup": "Full Body",
    "equipmentType": "Barbell",
    "exerciseType": "WEIGHTED",
    "familyId": "deadlift_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"HIP\",\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"KNEE\",\"LUMBAR_SPINE\",\"WRIST\"]",
    "primaryMuscles": "[\"Glutes\",\"Hamstrings\",\"Upper Trapezius\",\"Deltoids\"]",
    "secondaryMuscles": "[\"Quadriceps\",\"Biceps\",\"Core\"]",
    "setupNotes": "Stand with feet wider than shoulder-width, toes flared out, with a narrow double-overhand grip on the bar inside your legs. Hinge down keeping your chest up, then drive explosively through your legs and hips, shrugging hard and pulling your elbows high and outside to bring the bar to chin height. One of CrossFit's nine foundational movements."
  },
  {
    "name": "Box Jump Over",
    "muscleGroup": "Legs",
    "equipmentType": "Other",
    "exerciseType": "BODYWEIGHT",
    "familyId": "plyometric_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"KNEE\",\"HIP\",\"ANKLE\"]",
    "secondaryJoints": "[\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Quadriceps\",\"Glutes\",\"Calves\"]",
    "secondaryMuscles": "[\"Hamstrings\",\"Core\"]",
    "setupNotes": "Stand facing a box (20\"/24\" standard). Jump onto the top of the box with both feet, then immediately jump down the other side — or step down on both sides. Each rep ends on the opposite side of the box from where you started. Unlike standard box jumps, the goal is continuous over-and-back movement. Step-overs are an accepted modification in competition for recovery during long workouts."
  },
  {
    "name": "Dumbbell Box Step-Up",
    "muscleGroup": "Legs",
    "equipmentType": "Dumbbell",
    "exerciseType": "WEIGHTED",
    "familyId": "lunge_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"KNEE\",\"HIP\"]",
    "secondaryJoints": "[\"ANKLE\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Quadriceps\",\"Glutes\"]",
    "secondaryMuscles": "[\"Hamstrings\",\"Core\"]",
    "setupNotes": "Hold a dumbbell in each hand (or one at shoulder height) and step one foot onto a box (20\"/24\" standard for competition). Drive through the lead foot to stand fully on top, then step the trailing foot up to meet it. Step back down and alternate legs. This was the defining movement of CrossFit Open 19.3 — keep your torso upright and do not push off the trailing leg."
  },
  {
    "name": "Wall Walk",
    "muscleGroup": "Shoulders",
    "equipmentType": "Bodyweight",
    "exerciseType": "BODYWEIGHT",
    "familyId": "gymnastics_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"SHOULDER\",\"WRIST\"]",
    "secondaryJoints": "[\"ELBOW\",\"HIP\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Deltoids\",\"Triceps\",\"Core\"]",
    "secondaryMuscles": "[\"Serratus Anterior\",\"Upper Trapezius\"]",
    "setupNotes": "Start face-down on the floor with your feet touching the wall. Push up into a push-up position, then walk your feet up the wall while walking your hands toward the wall simultaneously — until your chest touches the wall in a handstand. Walk back down in the same controlled fashion. Featured prominently in CrossFit Open 22.1. Maintain a tight hollow body throughout; never let your lower back collapse."
  },
  {
    "name": "Legless Rope Climb",
    "muscleGroup": "Back",
    "equipmentType": "Rope",
    "exerciseType": "BODYWEIGHT",
    "familyId": "gymnastics_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"WRIST\",\"CORE\"]",
    "primaryMuscles": "[\"Latissimus Dorsi\",\"Biceps\",\"Forearms\"]",
    "secondaryMuscles": "[\"Rhomboids\",\"Core\",\"Deltoids\"]",
    "setupNotes": "Grab the rope as high as possible and climb using only your arms — no leg wrap or foot lock. Reach high, pull your chin past your hands, then lock off and reach again. Lower under control. This dramatically increases the upper body demand compared to a standard rope climb and appears regularly in CrossFit Quarterfinal and Semifinal programming. Prerequisite: 5+ standard rope climbs."
  },
  {
    "name": "Pegboard Climb",
    "muscleGroup": "Back",
    "equipmentType": "Other",
    "exerciseType": "BODYWEIGHT",
    "familyId": "gymnastics_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"WRIST\"]",
    "primaryMuscles": "[\"Latissimus Dorsi\",\"Biceps\",\"Forearms\"]",
    "secondaryMuscles": "[\"Core\",\"Deltoids\",\"Rhomboids\"]",
    "setupNotes": "Grip both pegs at the bottom holes of a pegboard. Keeping your body as vertical as possible, move each peg up one hole at a time to ascend — then descend under control. Competition standard requires legless ascent and descent. Requires significant pulling strength, grip endurance, and coordination. Build prerequisite strength with legless rope climbs before attempting the pegboard."
  },
  {
    "name": "Ring Muscle-Up",
    "muscleGroup": "Back",
    "equipmentType": "Rings",
    "exerciseType": "BODYWEIGHT",
    "familyId": "gymnastics_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"WRIST\"]",
    "primaryMuscles": "[\"Latissimus Dorsi\",\"Chest\",\"Triceps\"]",
    "secondaryMuscles": "[\"Biceps\",\"Core\",\"Deltoids\"]",
    "setupNotes": "Hang from the rings with a false grip (wrists hooked over the rings). Generate a kipping swing and drive your hips up, pulling the rings to your hips and transitioning into a dip support position in one movement. Press out to lockout. The false grip is essential — it keeps your wrists in contact with the ring through the transition. If you lose the false grip mid-rep, you cannot complete the turnover. Prerequisite: 5+ strict pull-ups, 5+ ring dips."
  },
  {
    "name": "Sandbag Over Shoulder",
    "muscleGroup": "Full Body",
    "equipmentType": "Other",
    "exerciseType": "WEIGHTED",
    "familyId": "metcon_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"HIP\",\"KNEE\",\"SHOULDER\"]",
    "secondaryJoints": "[\"LUMBAR_SPINE\",\"ANKLE\"]",
    "primaryMuscles": "[\"Glutes\",\"Hamstrings\",\"Traps\",\"Deltoids\"]",
    "secondaryMuscles": "[\"Quadriceps\",\"Core\",\"Biceps\"]",
    "setupNotes": "Stand over a heavy sandbag, hinge down and grip the bag on both sides. Drive through your legs and hips explosively, pull the bag up to one shoulder, and swing it over and behind you. Alternate shoulders each rep. The movement is one continuous hip-driven pull — don't muscle it with your arms. Sandbag over shoulder appears in CrossFit Games programming and is a staple in strongman-influenced WODs."
  },
  {
    "name": "Strict Handstand Push-Up",
    "muscleGroup": "Shoulders",
    "equipmentType": "Bodyweight",
    "exerciseType": "BODYWEIGHT",
    "familyId": "gymnastics_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"WRIST\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Deltoids\",\"Triceps\"]",
    "secondaryMuscles": "[\"Upper Trapezius\",\"Serratus Anterior\",\"Core\"]",
    "setupNotes": "Kick up to a handstand against a wall with your hands about shoulder-width apart and head between your arms. Lower your head to just touch the floor (or an AbMat for depth standard), then press back to full lockout. No kip — strict means legs stay stationary throughout. Head position matters: look slightly forward, not straight down. This is the competition strict standard; the kipping version is a separate movement."
  },
  {
    "name": "Kipping Handstand Push-Up",
    "muscleGroup": "Shoulders",
    "equipmentType": "Bodyweight",
    "exerciseType": "BODYWEIGHT",
    "familyId": "gymnastics_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"HIP\",\"WRIST\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Deltoids\",\"Triceps\"]",
    "secondaryMuscles": "[\"Core\",\"Hip Flexors\"]",
    "setupNotes": "From a wall-supported handstand, lower your head toward the floor, then drive your knees into your chest to load a kip, and explosively straighten your legs while pressing through your arms. The leg kick generates upward momentum that assists the press, allowing more reps than the strict version. Used in CrossFit WODs when high rep counts are prescribed. Prerequisite: solid strict handstand push-ups first."
  },
  {
    "name": "Dumbbell Walking Lunge",
    "muscleGroup": "Legs",
    "equipmentType": "Dumbbell",
    "exerciseType": "WEIGHTED",
    "familyId": "lunge_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"KNEE\",\"HIP\"]",
    "secondaryJoints": "[\"ANKLE\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Quadriceps\",\"Glutes\"]",
    "secondaryMuscles": "[\"Hamstrings\",\"Core\",\"Calves\"]",
    "setupNotes": "Hold a dumbbell in each hand at your sides and walk forward by stepping into a lunge on each leg — alternating continuously. Keep your torso upright, step far enough that your front shin is close to vertical, and let your back knee nearly touch the floor. The walking version (vs. stationary reverse lunge) appears frequently in CrossFit Open and Games programming for distance or total reps."
  },
  {
    "name": "Overhead Dumbbell Walking Lunge",
    "muscleGroup": "Legs",
    "equipmentType": "Dumbbell",
    "exerciseType": "WEIGHTED",
    "familyId": "lunge_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"KNEE\",\"HIP\",\"SHOULDER\"]",
    "secondaryJoints": "[\"ANKLE\",\"ELBOW\",\"WRIST\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Quadriceps\",\"Glutes\",\"Deltoids\"]",
    "secondaryMuscles": "[\"Core\",\"Hamstrings\",\"Triceps\"]",
    "setupNotes": "Hold a dumbbell (or two) locked out overhead with arms straight, then walk forward in continuous lunges. The overhead position adds a significant core stability and shoulder strength demand to the lunge. Common in CrossFit Games and Semifinals — often combined with box jumps or other high-skill movements in couplets."
  },
  {
    "name": "Bar-Facing Burpee",
    "muscleGroup": "Full Body",
    "equipmentType": "Barbell",
    "exerciseType": "BODYWEIGHT",
    "familyId": "metcon_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"HIP\",\"KNEE\",\"SHOULDER\"]",
    "secondaryJoints": "[\"ANKLE\",\"ELBOW\",\"WRIST\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Full Body\"]",
    "secondaryMuscles": "[]",
    "setupNotes": "Stand facing a barbell on the floor. Perform a burpee — chest to floor — with your body perpendicular to the bar, then jump with both feet over the bar (jumping forward, not laterally). This is distinguished from the Burpee-Over-Bar by the facing direction: you face the bar and jump over it lengthwise. The two-foot takeoff and landing are required. Common in CrossFit Open workouts."
  },
  {
    "name": "Lateral Burpee Over Bar",
    "muscleGroup": "Full Body",
    "equipmentType": "Barbell",
    "exerciseType": "BODYWEIGHT",
    "familyId": "metcon_family",
    "tags": "[\"functional\"]",
    "primaryJoints": "[\"HIP\",\"KNEE\",\"SHOULDER\"]",
    "secondaryJoints": "[\"ANKLE\",\"ELBOW\",\"WRIST\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Full Body\"]",
    "secondaryMuscles": "[]",
    "setupNotes": "Stand parallel alongside a barbell. Drop to chest-to-floor beside the bar, then jump both feet laterally over it to the other side. Turn and repeat. This is the most common Open competition burpee-over-bar standard — note the chest-to-floor requirement and two-foot lateral jump. Appeared in 24.1 and multiple prior Open workouts. The standard is stricter than a regular burpee."
  }
]
```

---

## Hyrox Net-New Exercises

The official 8 Hyrox stations. Ski Erg (calories), Sled Push, Sled Pull, Rowing (calories), Farmer's Walk, and Wall Ball Shot already exist in the database. The following 4 are absent:

```json
[
  {
    "name": "Burpee Broad Jump",
    "muscleGroup": "Full Body",
    "equipmentType": "Bodyweight",
    "exerciseType": "BODYWEIGHT",
    "familyId": "metcon_family",
    "tags": "[\"functional\",\"hyrox\"]",
    "primaryJoints": "[\"HIP\",\"KNEE\",\"ANKLE\",\"SHOULDER\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Glutes\",\"Quadriceps\",\"Hamstrings\"]",
    "secondaryMuscles": "[\"Chest\",\"Triceps\",\"Core\",\"Calves\"]",
    "setupNotes": "Perform a full burpee — chest to floor — then instead of jumping straight up, swing your arms forward and broad jump as far as possible forward, landing with both feet simultaneously. Turn around and repeat continuously. Official Hyrox station 4 is 80m of burpee broad jumps. The key is combining an efficient burpee with maximum horizontal distance on the jump — don't sacrifice jump distance to rush the burpee."
  },
  {
    "name": "Sandbag Lunge",
    "muscleGroup": "Legs",
    "equipmentType": "Other",
    "exerciseType": "WEIGHTED",
    "familyId": "lunge_family",
    "tags": "[\"functional\",\"hyrox\"]",
    "primaryJoints": "[\"KNEE\",\"HIP\"]",
    "secondaryJoints": "[\"ANKLE\",\"LUMBAR_SPINE\",\"SHOULDER\"]",
    "primaryMuscles": "[\"Quadriceps\",\"Glutes\"]",
    "secondaryMuscles": "[\"Hamstrings\",\"Core\",\"Calves\"]",
    "setupNotes": "Drape a Hyrox competition sandbag (10/20/30 kg depending on division) across your shoulders behind your neck. Walk forward in alternating lunges for the prescribed distance — official Hyrox station 7 is 100m. Keep your torso upright, step long enough for your front shin to be near vertical, and let the back knee nearly brush the floor each rep. The sandbag creates an unstable load that demands significant trunk stability compared to a barbell."
  },
  {
    "name": "Ski Erg (meters)",
    "muscleGroup": "Full Body",
    "equipmentType": "Machine",
    "exerciseType": "CARDIO",
    "familyId": "monostructural_family",
    "tags": "[\"functional\",\"hyrox\"]",
    "primaryJoints": "[\"SHOULDER\",\"HIP\",\"LUMBAR_SPINE\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\",\"KNEE\"]",
    "primaryMuscles": "[\"Latissimus Dorsi\",\"Core\",\"Deltoids\"]",
    "secondaryMuscles": "[\"Triceps\",\"Glutes\",\"Rhomboids\"]",
    "setupNotes": "Stand in front of the Concept2 Ski Erg and grab both handles overhead. Drive them down and back in a sweeping arc, hinging forward at the hips and ending with your hands at hip height, then rise back up as your hands return. The official Hyrox station 1 is 1000m of ski erg, tracked by distance rather than calories. Pace is critical — the first station sets the tone for the entire race."
  },
  {
    "name": "Row Erg (meters)",
    "muscleGroup": "Full Body",
    "equipmentType": "Machine",
    "exerciseType": "CARDIO",
    "familyId": "monostructural_family",
    "tags": "[\"functional\",\"hyrox\"]",
    "primaryJoints": "[\"HIP\",\"KNEE\",\"SHOULDER\"]",
    "secondaryJoints": "[\"ANKLE\",\"ELBOW\",\"LUMBAR_SPINE\",\"WRIST\"]",
    "primaryMuscles": "[\"Back\",\"Glutes\",\"Hamstrings\"]",
    "secondaryMuscles": "[\"Quadriceps\",\"Biceps\",\"Core\"]",
    "setupNotes": "Sit on the Concept2 RowErg with feet strapped in. Drive through your legs first, then swing your back open, then pull the handle to your lower chest with elbows tight. Recover arms first, then torso, then legs. Official Hyrox station 5 is 1000m, tracked as distance. Distinct from the existing Rowing (meters) entry in that this is specifically the RowErg in the Hyrox race context — same technique, different scoring context."
  }
]
```

**Note:** Ski Erg (meters) and Row Erg (meters) are modest duplicates of the existing Ski Erg (calories) and Rowing (meters) entries. If the app tracks both metric types under a single exercise name (configurable score type), these can be skipped. Include only if separate exercise entries are needed per scoring unit.

---

## Calisthenics Net-New Exercises

### Planche Family

```json
[
  {
    "name": "Tuck Planche Hold",
    "muscleGroup": "Shoulders",
    "equipmentType": "Bodyweight",
    "exerciseType": "TIMED",
    "familyId": "planche_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\",\"WRIST\"]",
    "secondaryJoints": "[\"ELBOW\",\"LUMBAR_SPINE\",\"HIP\"]",
    "primaryMuscles": "[\"Deltoids (Anterior)\",\"Serratus Anterior\",\"Triceps\"]",
    "secondaryMuscles": "[\"Core\",\"Hip Flexors\",\"Wrist Extensors\"]",
    "setupNotes": "Place your hands flat on the floor (or parallettes) with fingers pointing forward or slightly outward. Lean forward until your shoulders are well over your hands, then lift both feet off the ground with your knees tucked tightly to your chest. Hold for time with arms locked and body as horizontal as possible. This is the first weighted planche progression — the tuck shortens the lever arm. Most athletes achieve their first tuck planche in 3–6 months of dedicated training."
  },
  {
    "name": "Advanced Tuck Planche Hold",
    "muscleGroup": "Shoulders",
    "equipmentType": "Bodyweight",
    "exerciseType": "TIMED",
    "familyId": "planche_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\",\"WRIST\"]",
    "secondaryJoints": "[\"ELBOW\",\"LUMBAR_SPINE\",\"HIP\"]",
    "primaryMuscles": "[\"Deltoids (Anterior)\",\"Serratus Anterior\",\"Triceps\"]",
    "secondaryMuscles": "[\"Core\",\"Hip Flexors\"]",
    "setupNotes": "From the tuck planche, extend your hips backward so your back is roughly flat and parallel to the floor while knees remain bent and tucked close to the chest. The body is more extended than a tuck planche but not yet a straddle. Maintain forward lean with shoulders past hands. A hold of 10+ seconds is the target before progressing to straddle. Often used as a strength benchmark in structured calisthenics programs."
  },
  {
    "name": "Straddle Planche Hold",
    "muscleGroup": "Shoulders",
    "equipmentType": "Bodyweight",
    "exerciseType": "TIMED",
    "familyId": "planche_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\",\"WRIST\"]",
    "secondaryJoints": "[\"ELBOW\",\"LUMBAR_SPINE\",\"HIP\"]",
    "primaryMuscles": "[\"Deltoids (Anterior)\",\"Serratus Anterior\",\"Triceps\"]",
    "secondaryMuscles": "[\"Core\",\"Hip Flexors\",\"Glutes\"]",
    "setupNotes": "Support your straight, horizontal body on locked arms with legs spread wide (straddled) to reduce the lever arm vs. a full planche. Shoulders must stay over or past the hands, hips in line with shoulders, and back flat. The straddle position is the penultimate planche progression — splitting the legs distributes the load and makes it attainable before the full closed-leg version. Target: 5–10 second hold before attempting full planche."
  },
  {
    "name": "Full Planche Hold",
    "muscleGroup": "Shoulders",
    "equipmentType": "Bodyweight",
    "exerciseType": "TIMED",
    "familyId": "planche_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\",\"WRIST\"]",
    "secondaryJoints": "[\"ELBOW\",\"LUMBAR_SPINE\",\"HIP\"]",
    "primaryMuscles": "[\"Deltoids (Anterior)\",\"Serratus Anterior\",\"Triceps\"]",
    "secondaryMuscles": "[\"Core\",\"Hip Flexors\",\"Glutes\"]",
    "setupNotes": "Hold your entire body horizontal, parallel to the ground, supported only on locked straight arms with legs closed together. The ultimate planche progression — requires years of dedicated straight-arm pushing strength development. Shoulders must be noticeably past the hands, entire body rigid, and there should be zero hip sag or pike. Even a 2-second hold is considered elite."
  },
  {
    "name": "Planche Push-Up",
    "muscleGroup": "Shoulders",
    "equipmentType": "Bodyweight",
    "exerciseType": "BODYWEIGHT",
    "familyId": "planche_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\",\"WRIST\"]",
    "secondaryJoints": "[\"LUMBAR_SPINE\",\"HIP\"]",
    "primaryMuscles": "[\"Deltoids (Anterior)\",\"Serratus Anterior\",\"Triceps\",\"Chest\"]",
    "secondaryMuscles": "[\"Core\",\"Hip Flexors\"]",
    "setupNotes": "From a straddle or full planche hold, lower your body by bending your elbows — maintaining the planche position (body parallel to floor) throughout — then press back up to lockout. One of the most advanced calisthenics pushing exercises. Typically performed for 1–5 reps even at elite level. Begin with straddle planche push-ups before attempting full planche push-ups."
  }
]
```

### Front Lever Family

```json
[
  {
    "name": "Tuck Front Lever Hold",
    "muscleGroup": "Back",
    "equipmentType": "Pull-up Bar",
    "exerciseType": "TIMED",
    "familyId": "front_lever_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\",\"HIP\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Latissimus Dorsi\",\"Rear Deltoids\"]",
    "secondaryMuscles": "[\"Rhomboids\",\"Core\",\"Biceps\"]",
    "setupNotes": "Hang from a pull-up bar or rings with an overhand grip. Pull your body up until it is horizontal with knees tucked tightly to your chest — your back should be flat and parallel to the ground. Arms are straight or very slightly bent. Hold for time. This is the entry-level front lever progression — the tucked knees shorten the lever significantly. Aim for a solid 10-second hold before progressing to advanced tuck."
  },
  {
    "name": "Advanced Tuck Front Lever Hold",
    "muscleGroup": "Back",
    "equipmentType": "Pull-up Bar",
    "exerciseType": "TIMED",
    "familyId": "front_lever_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\",\"HIP\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Latissimus Dorsi\",\"Rear Deltoids\"]",
    "secondaryMuscles": "[\"Rhomboids\",\"Core\",\"Biceps\"]",
    "setupNotes": "From the tuck front lever, extend your hips back so your thighs are parallel to the floor while your knees remain bent at 90 degrees. Back remains flat and horizontal. The additional lever arm demands significantly more lat strength. This is the second major front lever checkpoint — typically takes 3–6 months to achieve from a solid tuck hold."
  },
  {
    "name": "One-Leg Front Lever Hold",
    "muscleGroup": "Back",
    "equipmentType": "Pull-up Bar",
    "exerciseType": "TIMED",
    "familyId": "front_lever_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\",\"HIP\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Latissimus Dorsi\",\"Rear Deltoids\"]",
    "secondaryMuscles": "[\"Rhomboids\",\"Core\",\"Biceps\"]",
    "setupNotes": "Hold a front lever position with one leg extended and one knee tucked to your chest. Alternate which leg is extended. The asymmetric position bridges the gap between advanced tuck and straddle front lever. Focus on keeping the body horizontal and preventing rotation. Useful when straddle flexibility is a limiting factor."
  },
  {
    "name": "Straddle Front Lever Hold",
    "muscleGroup": "Back",
    "equipmentType": "Pull-up Bar",
    "exerciseType": "TIMED",
    "familyId": "front_lever_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\",\"HIP\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Latissimus Dorsi\",\"Rear Deltoids\"]",
    "secondaryMuscles": "[\"Rhomboids\",\"Core\",\"Biceps\"]",
    "setupNotes": "Hold a front lever with legs spread wide (straddled) to shorten the effective lever arm compared to a full front lever. Body is horizontal, arms straight, hips in line with the torso. The last progression before full front lever. Requires full hip flexor strength to keep legs up and strong lat engagement to keep the torso horizontal. Target: 5-second hold."
  },
  {
    "name": "Full Front Lever Hold",
    "muscleGroup": "Back",
    "equipmentType": "Pull-up Bar",
    "exerciseType": "TIMED",
    "familyId": "front_lever_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\",\"HIP\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Latissimus Dorsi\",\"Rear Deltoids\"]",
    "secondaryMuscles": "[\"Rhomboids\",\"Core\",\"Biceps\"]",
    "setupNotes": "Hang from a bar or rings and hold your entire body horizontal — body straight, legs closed together, arms locked straight, face pointing down. The full front lever requires exceptional lat and posterior chain strength. Competition minimum is a 2-second hold for recognition. Even seasoned calisthenics athletes take 12–24 months to develop from beginner to full front lever."
  },
  {
    "name": "Front Lever Row",
    "muscleGroup": "Back",
    "equipmentType": "Pull-up Bar",
    "exerciseType": "BODYWEIGHT",
    "familyId": "front_lever_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"WRIST\",\"HIP\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Latissimus Dorsi\",\"Rear Deltoids\",\"Biceps\"]",
    "secondaryMuscles": "[\"Rhomboids\",\"Core\"]",
    "setupNotes": "Start in a tuck or full front lever hold, then row yourself up toward the bar by bending your elbows — maintaining the horizontal body position throughout — then lower back to the hold position. Often trained in tuck front lever rows to build the pulling strength needed for the full front lever. The dynamic version significantly increases the strength demand vs. the static hold."
  }
]
```

### Back Lever Family

```json
[
  {
    "name": "Back Lever Hold",
    "muscleGroup": "Back",
    "equipmentType": "Pull-up Bar",
    "exerciseType": "TIMED",
    "familyId": "back_lever_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Biceps\",\"Rear Deltoids\",\"Chest\"]",
    "secondaryMuscles": "[\"Core\",\"Forearms\"]",
    "setupNotes": "From a dead hang, perform a skin-the-cat to get into a German hang (shoulders in extension), then straighten your body until it is horizontal and parallel to the ground with your back facing up. Hold for time with arms straight. The back lever is easier than the front lever because of the shoulder position — entry progression for rings-based static skills. Always train skin-the-cat first to develop shoulder flexibility."
  },
  {
    "name": "Skin the Cat",
    "muscleGroup": "Shoulders",
    "equipmentType": "Pull-up Bar",
    "exerciseType": "BODYWEIGHT",
    "familyId": "back_lever_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\",\"HIP\"]",
    "primaryMuscles": "[\"Rear Deltoids\",\"Chest (Pec Minor)\"]",
    "secondaryMuscles": "[\"Core\",\"Biceps\",\"Hip Flexors\"]",
    "setupNotes": "Hang from rings or a bar with an overhand grip. Keeping your arms straight, lift your feet up and over — rotating your body backward (toes go behind and below you) until you are in a German hang (shoulders fully extended overhead). Hold briefly, then reverse the movement back to the starting position. The skin-the-cat builds the shoulder flexibility and strength needed for the back lever. Essential mobility prerequisite."
  }
]
```

### Ring Skills

```json
[
  {
    "name": "Ring L-Sit",
    "muscleGroup": "Core",
    "equipmentType": "Rings",
    "exerciseType": "TIMED",
    "familyId": "ring_skills_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\",\"HIP\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\",\"KNEE\"]",
    "primaryMuscles": "[\"Hip Flexors\",\"Core\",\"Triceps\"]",
    "secondaryMuscles": "[\"Serratus Anterior\",\"Deltoids\",\"Quadriceps\"]",
    "setupNotes": "Support yourself on the rings with arms straight down and rings turned out. Lift your legs to horizontal (parallel to the floor) to form an L shape, with legs together and toes pointed. Hold for time. Significantly harder than the parallette or floor version because the rings are unstable — your shoulder stabilizers must work continuously to prevent lateral movement. The ring L-sit is a prerequisite for more advanced ring static skills."
  },
  {
    "name": "Ring Support Hold",
    "muscleGroup": "Shoulders",
    "equipmentType": "Rings",
    "exerciseType": "TIMED",
    "familyId": "ring_skills_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"WRIST\"]",
    "primaryMuscles": "[\"Deltoids\",\"Triceps\",\"Chest\"]",
    "secondaryMuscles": "[\"Serratus Anterior\",\"Core\",\"Forearms\"]",
    "setupNotes": "Mount the rings and hold yourself in a support position — arms fully locked out below you with rings at hip height. Turn the rings out (externally rotate) so your knuckles face forward and your pinkies face out. Hold for time with a tight hollow body position. The ring support hold is the foundational ring skill — it builds the wrist, elbow, and shoulder stability needed for muscle-ups and ring dips. Aim for 30+ seconds before progressing to other ring work."
  },
  {
    "name": "Iron Cross Hold",
    "muscleGroup": "Shoulders",
    "equipmentType": "Rings",
    "exerciseType": "TIMED",
    "familyId": "ring_skills_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\"]",
    "primaryMuscles": "[\"Deltoids\",\"Chest\",\"Subscapularis\"]",
    "secondaryMuscles": "[\"Biceps\",\"Core\",\"Forearms\"]",
    "setupNotes": "From a ring support hold, lower the rings outward until your arms are extended straight out to the sides — forming a cross with your body. Hold for time at the lowest position where you can maintain control. One of the most demanding ring static skills, requiring exceptional shoulder joint strength. Always use band assistance or a spotter when beginning. Build prerequisite strength with years of ring support work, ring dips, and weighted pull-ups."
  }
]
```

### Other Calisthenics Skills

```json
[
  {
    "name": "One-Arm Pull-Up",
    "muscleGroup": "Back",
    "equipmentType": "Pull-up Bar",
    "exerciseType": "BODYWEIGHT",
    "familyId": "pullup_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"WRIST\"]",
    "primaryMuscles": "[\"Latissimus Dorsi\",\"Biceps\"]",
    "secondaryMuscles": "[\"Rhomboids\",\"Core\",\"Forearms\"]",
    "setupNotes": "Hang from a bar with one hand and pull yourself up until your chin clears the bar, then lower under control. The free hand can rest on your wrist or stay at your side. One of the most demanding single-arm pulling exercises possible — prerequisite is typically 15–20 strict pull-ups plus specific one-arm negative training. Common progression path: assisted one-arm pull-ups, one-arm negatives, then full reps."
  },
  {
    "name": "One-Arm Negative Pull-Up",
    "muscleGroup": "Back",
    "equipmentType": "Pull-up Bar",
    "exerciseType": "BODYWEIGHT",
    "familyId": "pullup_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\",\"ELBOW\"]",
    "secondaryJoints": "[\"WRIST\"]",
    "primaryMuscles": "[\"Latissimus Dorsi\",\"Biceps\"]",
    "secondaryMuscles": "[\"Rhomboids\",\"Core\",\"Forearms\"]",
    "setupNotes": "Jump or use a chair to get your chin above the bar, then lower yourself as slowly as possible using only one arm. Aim for 5+ seconds per rep. This eccentric-only approach is the primary training method for developing one-arm pull-up strength before being able to do the concentric phase. Focus on controlling the descent evenly throughout the range."
  },
  {
    "name": "Human Flag Hold",
    "muscleGroup": "Core",
    "equipmentType": "Other",
    "exerciseType": "TIMED",
    "familyId": "flag_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\",\"HIP\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Deltoids\",\"Latissimus Dorsi\",\"Core (Obliques)\"]",
    "secondaryMuscles": "[\"Glutes\",\"Adductors\",\"Chest\"]",
    "setupNotes": "Grip a vertical pole with both hands (top hand pushing away, bottom hand pulling in). Hold your body out horizontally to the side — entirely parallel to the ground, with legs together. Hold for time. The human flag is an iconic calisthenics skill requiring enormous lateral core and shoulder strength. Begin with a tuck flag (knees bent) before attempting full straight-body variation."
  },
  {
    "name": "Tuck Human Flag Hold",
    "muscleGroup": "Core",
    "equipmentType": "Other",
    "exerciseType": "TIMED",
    "familyId": "flag_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"SHOULDER\"]",
    "secondaryJoints": "[\"ELBOW\",\"WRIST\",\"HIP\"]",
    "primaryMuscles": "[\"Deltoids\",\"Latissimus Dorsi\",\"Core (Obliques)\"]",
    "secondaryMuscles": "[\"Glutes\",\"Chest\"]",
    "setupNotes": "Hold a vertical pole with both hands (top hand pushing, bottom hand pulling) and hold your body out to the side with your knees tucked tightly to your chest. The tuck shortens the lever arm and makes the human flag achievable as a progression. Hold for time. Progress to a straddle flag and then a full flag as strength increases."
  },
  {
    "name": "V-Sit Hold",
    "muscleGroup": "Core",
    "equipmentType": "Bodyweight",
    "exerciseType": "TIMED",
    "familyId": "gymnastics_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"HIP\",\"LUMBAR_SPINE\"]",
    "secondaryJoints": "[\"SHOULDER\",\"ELBOW\",\"WRIST\"]",
    "primaryMuscles": "[\"Hip Flexors\",\"Core\",\"Quadriceps\"]",
    "secondaryMuscles": "[\"Triceps\",\"Deltoids\",\"Forearms\"]",
    "setupNotes": "Support yourself on the floor, parallettes, or bars with arms locked out. Lift your legs above the horizontal — past the L-sit — creating a V shape with your body. Legs and torso form an angle greater than 90 degrees. Hold for time. The V-sit bridges the gap between the L-sit and the manna, requiring advanced hip flexor strength and compression ability. Also called a 'V-hold'."
  },
  {
    "name": "Manna Hold",
    "muscleGroup": "Core",
    "equipmentType": "Bodyweight",
    "exerciseType": "TIMED",
    "familyId": "gymnastics_family",
    "tags": "[\"functional\",\"calisthenics\"]",
    "primaryJoints": "[\"HIP\",\"SHOULDER\",\"WRIST\"]",
    "secondaryJoints": "[\"ELBOW\",\"LUMBAR_SPINE\"]",
    "primaryMuscles": "[\"Hip Flexors\",\"Core\",\"Deltoids (Posterior)\"]",
    "secondaryMuscles": "[\"Triceps\",\"Glutes\"]",
    "setupNotes": "Support yourself on straight arms with your legs raised high — ideally past vertical — so your hips are elevated well above shoulder height. The body forms an extreme piked position with legs pointing toward the ceiling. One of the most advanced static positions in gymnastics and calisthenics, requiring extreme hip flexor and compression strength combined with significant posterior shoulder mobility. Progress through L-sit, V-sit, then increasingly vertical leg positions."
  }
]
```

---

## Existing Exercises Needing Retag (tags addition only)

These exercises are already in the database but are missing the `"functional"` tag (and in some cases a domain sub-tag). They should not get new JSON entries — only a tag patch is required.

| Exercise Name | Add tags |
|---|---|
| Barbell Back Squat | `["functional"]` |
| Front Squat | `["functional"]` |
| Overhead Squat | `["functional"]` |
| Bulgarian Split Squat | `["functional"]` |
| Sumo Deadlift | `["functional"]` |
| Conventional Deadlift | `["functional"]` |
| Deficit Deadlift | `["functional"]` |
| Trap Bar Deadlift | `["functional"]` |
| Romanian Deadlift (RDL) - BB | `["functional"]` |
| Stiff-Leg Deadlift | `["functional"]` |
| Barbell Row | `["functional"]` |
| Pendlay Row | `["functional"]` |
| Standing Barbell Overhead Press | `["functional"]` |
| Barbell Thruster | `["functional"]` |
| Kettlebell Thruster | `["functional"]` |
| Dumbbell Devil Press | `["functional"]` |
| Turkish Get-Up | `["functional"]` |
| Kettlebell Swing | `["functional"]` |
| American Kettlebell Swing | `["functional"]` |
| Kettlebell Snatch | `["functional"]` |
| Kettlebell Clean | `["functional"]` |
| Box Jump | `["functional"]` |
| Box Step-Over | `["functional"]` |
| Broad Jump | `["functional"]` |
| Tuck Jump | `["functional"]` |
| Double Under (Jump Rope) | `["functional","monostructural"]` |
| Single Under | `["functional","monostructural"]` |
| Burpee | `["functional"]` |
| Burpee (Standard) | `["functional"]` |
| Wall Ball Shot | `["functional"]` |
| Sled Push | `["functional","hyrox"]` |
| Sled Pull | `["functional","hyrox"]` |
| Farmer's Walk | `["functional","hyrox"]` |
| Toes-to-Bar | `["functional","gymnastics"]` |
| Hanging Leg Raise | `["functional"]` |
| Muscle-Up (Rings) | `["functional","gymnastics"]` |
| Ring Dip | `["functional","gymnastics"]` |
| Handstand Hold | `["functional","gymnastics"]` |
| Handstand Push-Up | `["functional","gymnastics"]` |
| Pull-Up | `["functional"]` |
| Dips | `["functional"]` |
| Push-Ups | `["functional"]` |

---

## Implementation Notes

1. **Duplicate Ski Erg / Row Erg entries**: If the app supports multiple score types (calories vs. meters) on a single exercise entry, the Ski Erg (meters) and Row Erg (meters) Hyrox entries are redundant — only add them if separate tracking is architecturally required.

2. **`exerciseType` alignment**: The schema in `master_exercises.json` uses `STRENGTH`, `CARDIO`, and `TIMED` as actual values (not the `WEIGHTED`/`BODYWEIGHT`/`ASSISTED` the task spec lists). Use `STRENGTH` for dynamic weighted/bodyweight movements, `TIMED` for isometric holds, and `CARDIO` for monostructural cardio. The task spec values appear to be simplified aliases — match the file's existing pattern.

3. **Planche family prerequisite**: The existing `Pseudo-Planche Hold` and `Pseudo-Planche Push-up` already exist in the DB — they should also receive `["functional","calisthenics"]` tags (added to the retag table above implicitly, but call them out: they are `Pseudo-Planche Hold` and `Pseudo-Planche Push-up`).

4. **`familyId` for new families**: New family IDs introduced: `planche_family`, `front_lever_family`, `back_lever_family`, `ring_skills_family`, `flag_family`. These should be registered in whatever family registry the app uses.

5. **Total new exercises**: 16 (CrossFit) + 4 (Hyrox) + 22 (Calisthenics) = **42 net-new entries**.
