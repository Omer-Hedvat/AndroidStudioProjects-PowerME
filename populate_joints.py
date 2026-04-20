#!/usr/bin/env python3
"""Populate primaryJoints and secondaryJoints fields in master_exercises.json."""

import json

JSON_PATH = "app/src/main/res/raw/master_exercises.json"

# Mapping: exercise name -> (primaryJoints, secondaryJoints)
# Joints: CERVICAL_SPINE, THORACIC_SPINE, LUMBAR_SPINE, SHOULDER, ELBOW, WRIST, HIP, KNEE, ANKLE, FOOT
JOINT_MAP = {
    # ── Squat variants ──
    "Barbell Back Squat":           (["KNEE", "HIP"], ["ANKLE", "LUMBAR_SPINE"]),
    "Front Squat":                  (["KNEE", "HIP"], ["ANKLE", "SHOULDER", "WRIST"]),
    "Goblet Squat":                 (["KNEE", "HIP"], ["ANKLE", "SHOULDER"]),
    "Bulgarian Split Squat":        (["KNEE", "HIP"], ["ANKLE", "LUMBAR_SPINE"]),
    "Leg Press":                    (["KNEE", "HIP"], ["ANKLE"]),
    "Hack Squat":                   (["KNEE", "HIP"], ["ANKLE", "LUMBAR_SPINE"]),
    "Safety Bar Squat":             (["KNEE", "HIP"], ["ANKLE", "LUMBAR_SPINE"]),
    "Box Squat":                    (["KNEE", "HIP"], ["ANKLE", "LUMBAR_SPINE"]),
    "Pause Squat":                  (["KNEE", "HIP"], ["ANKLE", "LUMBAR_SPINE"]),
    "Sumo Squat":                   (["KNEE", "HIP"], ["ANKLE", "LUMBAR_SPINE"]),
    "Pistol Squat":                 (["KNEE", "HIP"], ["ANKLE", "LUMBAR_SPINE"]),
    "Single-Leg Press":             (["KNEE", "HIP"], ["ANKLE"]),
    "Belt Squat":                   (["KNEE", "HIP"], ["ANKLE"]),
    "Landmine Squat":               (["KNEE", "HIP"], ["SHOULDER", "LUMBAR_SPINE"]),
    "Smith Machine Squat":          (["KNEE", "HIP"], ["ANKLE", "LUMBAR_SPINE"]),
    "Smith Machine Bulgarian Split Squat": (["KNEE", "HIP"], ["ANKLE", "LUMBAR_SPINE"]),
    "Overhead Squat":               (["KNEE", "HIP", "SHOULDER"], ["ANKLE", "WRIST", "LUMBAR_SPINE"]),
    "Sissy Squat":                  (["KNEE"], ["HIP", "ANKLE"]),
    "Sissy Squat (Bodyweight)":     (["KNEE"], ["HIP", "ANKLE"]),
    "Shrimp Squat":                 (["KNEE", "HIP"], ["ANKLE"]),
    "Cossack Squat":                (["KNEE", "HIP"], ["ANKLE"]),
    "Horse Stance":                 (["HIP", "KNEE"], ["ANKLE"]),
    "Duck Walk":                    (["KNEE", "HIP"], ["ANKLE"]),

    # ── Deadlift variants ──
    "Conventional Deadlift":        (["HIP", "LUMBAR_SPINE"], ["KNEE", "SHOULDER"]),
    "Romanian Deadlift (RDL) - BB": (["HIP", "LUMBAR_SPINE"], ["KNEE"]),
    "Romanian Deadlift (RDL) - DB": (["HIP", "LUMBAR_SPINE"], ["KNEE"]),
    "Trap Bar Deadlift":            (["HIP", "KNEE"], ["LUMBAR_SPINE", "SHOULDER"]),
    "Sumo Deadlift":                (["HIP", "LUMBAR_SPINE"], ["KNEE", "SHOULDER"]),
    "Single-Leg RDL":               (["HIP", "LUMBAR_SPINE"], ["KNEE", "ANKLE"]),
    "Deficit Deadlift":             (["HIP", "LUMBAR_SPINE"], ["KNEE", "SHOULDER", "ANKLE"]),
    "Rack Pull":                    (["HIP", "LUMBAR_SPINE"], ["SHOULDER"]),
    "Stiff-Leg Deadlift":           (["HIP", "LUMBAR_SPINE"], ["KNEE"]),
    "Good Morning":                 (["HIP", "LUMBAR_SPINE"], ["KNEE"]),
    "Smith Machine Romanian Deadlift": (["HIP", "LUMBAR_SPINE"], ["KNEE"]),
    "Kettlebell Romanian Deadlift": (["HIP", "LUMBAR_SPINE"], ["KNEE"]),

    # ── Bench press / chest push ──
    "Barbell Flat Bench Press":     (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Incline Barbell Bench Press":  (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Decline Barbell Bench Press":  (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Dumbbell Flat Bench Press":    (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Incline Dumbbell Bench Press": (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Decline Dumbbell Press":       (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Cable Chest Fly":              (["SHOULDER"], ["ELBOW"]),
    "Machine Chest Press":          (["SHOULDER", "ELBOW"], []),
    "Close-Grip Bench Press":       (["ELBOW", "SHOULDER"], ["WRIST"]),
    "Push-Ups":                     (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Diamond Push-Up":              (["ELBOW", "SHOULDER"], ["WRIST"]),
    "Pec Deck Fly":                 (["SHOULDER"], []),
    "Cable Crossover":              (["SHOULDER"], ["ELBOW"]),
    "Ring Push-Up":                 (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Weighted Push-Up":             (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Smith Machine Bench Press":    (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Dumbbell Pullover":            (["SHOULDER"], ["ELBOW", "LUMBAR_SPINE"]),
    "Decline Push-Up":              (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Dumbbell Chest Fly":           (["SHOULDER"], ["ELBOW"]),
    "Archer Push-up":               (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Typewriter Push-up":           (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Pseudo-Planche Push-up":       (["SHOULDER", "ELBOW", "WRIST"], []),
    "Clap Push-up":                 (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Single-Arm Push-up":           (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Staggered-Hand Push-up":       (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Tiger Bend Push-up":           (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Bench Dip (Feet Elevated)":    (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Incline Push-up":              (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Hindu Push-up":                (["SHOULDER", "ELBOW"], ["WRIST", "LUMBAR_SPINE"]),

    # ── Rowing / back ──
    "Barbell Row":                  (["SHOULDER", "ELBOW"], ["LUMBAR_SPINE", "WRIST"]),
    "Dumbbell Row":                 (["SHOULDER", "ELBOW"], ["LUMBAR_SPINE"]),
    "Chest-Supported Row":          (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Cable Row":                    (["SHOULDER", "ELBOW"], ["LUMBAR_SPINE"]),
    "T-Bar Row":                    (["SHOULDER", "ELBOW"], ["LUMBAR_SPINE", "WRIST"]),
    "Seal Row":                     (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Pendlay Row":                  (["SHOULDER", "ELBOW"], ["LUMBAR_SPINE", "WRIST"]),
    "Inverted Row":                 (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Incline Dumbbell Row":         (["SHOULDER", "ELBOW"], []),
    "Single-Arm Cable Row":         (["SHOULDER", "ELBOW"], ["LUMBAR_SPINE"]),
    "Meadows Row":                  (["SHOULDER", "ELBOW"], ["LUMBAR_SPINE"]),
    "Kettlebell Row":               (["SHOULDER", "ELBOW"], ["LUMBAR_SPINE"]),
    "Australian Pull-up (Inverted Row)": (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Single-Arm Australian Row":    (["SHOULDER", "ELBOW"], ["WRIST"]),

    # ── Overhead press ──
    "Standing Barbell Overhead Press": (["SHOULDER", "ELBOW"], ["WRIST", "CERVICAL_SPINE", "LUMBAR_SPINE"]),
    "Seated Dumbbell Overhead Press":  (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Arnold Press":                 (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Machine Shoulder Press":       (["SHOULDER", "ELBOW"], []),
    "Landmine Press":               (["SHOULDER", "ELBOW"], ["WRIST", "LUMBAR_SPINE"]),
    "Pike Push-Up":                 (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Seated Barbell Overhead Press":(["SHOULDER", "ELBOW"], ["WRIST"]),
    "Z-Press":                      (["SHOULDER", "ELBOW"], ["WRIST", "HIP"]),
    "Smith Machine Overhead Press": (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Handstand Push-Up":            (["SHOULDER", "ELBOW", "WRIST"], ["CERVICAL_SPINE"]),
    "Kettlebell Press":             (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Pseudo-Planche Hold":          (["SHOULDER", "ELBOW", "WRIST"], []),
    "Handstand Hold":               (["SHOULDER", "WRIST"], ["ELBOW"]),

    # ── Pull-ups / lat ──
    "Pull-Up":                      (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Chin-Up":                      (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Neutral Grip Pull-Up":         (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Lat Pulldown":                 (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Close-Grip Lat Pulldown":      (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Straight-Arm Pulldown":        (["SHOULDER"], ["ELBOW"]),
    "Assisted Pull-Up":             (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Weighted Pull-Up":             (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Wide-Grip Lat Pulldown":       (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Cable Pullover":               (["SHOULDER"], ["ELBOW"]),
    "Kneeling Single-Arm Lat Pulldown": (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Negative Pull-up":             (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Commando Pull-up":             (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Archer Pull-up":               (["SHOULDER", "ELBOW"], ["WRIST"]),
    "L-Sit Pull-up":                (["SHOULDER", "ELBOW"], ["WRIST", "HIP"]),
    "Explosive Pull-up (Chest-to-Bar)": (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Scapular Pull-up":             (["SHOULDER"], []),
    "Dead Hang":                    (["SHOULDER"], ["ELBOW", "WRIST"]),
    "Chest-to-Bar Pull-Up":         (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Around the World (Pull-up Bar)":(["SHOULDER"], ["ELBOW", "WRIST"]),
    "Muscle-Up (Rings)":            (["SHOULDER", "ELBOW"], ["WRIST"]),

    # ── Hamstrings / leg isolation ──
    "Lying Leg Curl":               (["KNEE"], ["HIP"]),
    "Seated Leg Curl":              (["KNEE"], ["HIP"]),
    "Nordic Curl":                  (["KNEE"], ["HIP"]),
    "Nordic Hamstring Curl":        (["KNEE"], ["HIP"]),
    "Reverse Nordic":               (["KNEE"], ["HIP"]),
    "Leg Extension":                (["KNEE"], []),

    # ── Calves ──
    "Standing Calf Raise":          (["ANKLE"], ["KNEE"]),
    "Seated Calf Raise":            (["ANKLE"], ["KNEE"]),
    "Donkey Calf Raise":            (["ANKLE"], ["KNEE"]),
    "Leg Press Calf Raise":         (["ANKLE"], ["KNEE"]),
    "Calf Raise (Single-Leg)":      (["ANKLE"], ["KNEE"]),
    "Tibialis Raise (Wall)":        (["ANKLE"], []),

    # ── Bicep curls ──
    "Barbell Curl":                 (["ELBOW"], ["WRIST", "SHOULDER"]),
    "Dumbbell Curl":                (["ELBOW"], ["WRIST", "SHOULDER"]),
    "Hammer Curl":                  (["ELBOW"], ["WRIST"]),
    "Cable Curl":                   (["ELBOW"], ["WRIST", "SHOULDER"]),
    "Preacher Curl":                (["ELBOW"], ["WRIST"]),
    "Concentration Curl":           (["ELBOW"], ["SHOULDER"]),
    "Incline Curl":                 (["ELBOW"], ["SHOULDER"]),
    "EZ Bar Curl":                  (["ELBOW"], ["WRIST", "SHOULDER"]),
    "Reverse Curl":                 (["ELBOW", "WRIST"], ["SHOULDER"]),
    "Zottman Curl":                 (["ELBOW", "WRIST"], []),
    "Machine Preacher Curl":        (["ELBOW"], []),
    "Cable Hammer Curl":            (["ELBOW"], ["WRIST"]),
    "Spider Curl":                  (["ELBOW"], ["SHOULDER"]),
    "Band Bicep Curl":              (["ELBOW"], ["WRIST", "SHOULDER"]),

    # ── Triceps ──
    "Tricep Pushdown":              (["ELBOW"], ["WRIST", "SHOULDER"]),
    "Overhead Tricep Extension":    (["ELBOW"], ["SHOULDER", "WRIST"]),
    "Skull Crusher":                (["ELBOW"], ["WRIST", "SHOULDER"]),
    "Dips":                         (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Bench Dips":                   (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Tricep Kickback":              (["ELBOW"], ["SHOULDER"]),
    "EZ Bar Skullcrusher":          (["ELBOW"], ["WRIST", "SHOULDER"]),
    "Cable Overhead Tricep Extension": (["ELBOW"], ["SHOULDER", "WRIST"]),
    "Rope Tricep Pushdown":         (["ELBOW"], ["WRIST", "SHOULDER"]),
    "Tate Press":                   (["ELBOW"], ["WRIST", "SHOULDER"]),
    "JM Press":                     (["ELBOW", "SHOULDER"], ["WRIST"]),
    "Ring Dip":                     (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Band Tricep Pushdown":         (["ELBOW"], ["WRIST", "SHOULDER"]),

    # ── Shoulders / lateral ──
    "Lateral Raise":                (["SHOULDER"], []),
    "Face Pull":                    (["SHOULDER"], ["ELBOW"]),
    "Rear Delt Fly":                (["SHOULDER"], ["ELBOW"]),
    "Shrugs":                       (["CERVICAL_SPINE", "SHOULDER"], []),
    "Barbell Shrug":                (["CERVICAL_SPINE", "SHOULDER"], []),
    "Upright Row":                  (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Front Raise":                  (["SHOULDER"], ["ELBOW"]),
    "Cable Lateral Raise":          (["SHOULDER"], []),
    "Reverse Pec Deck":             (["SHOULDER"], []),
    "Cable Front Raise":            (["SHOULDER"], ["ELBOW"]),
    "Machine Lateral Raise":        (["SHOULDER"], []),
    "Cable Upright Row":            (["SHOULDER", "ELBOW"], ["WRIST"]),
    "Band Pull-Apart":              (["SHOULDER"], []),
    "Band Pull-Apart (Resistance Band)": (["SHOULDER"], []),
    "Face Pull (Resistance Band)":  (["SHOULDER"], ["ELBOW"]),
    "DB Around the World":          (["SHOULDER"], ["ELBOW"]),
    "Wrist Curl":                   (["WRIST"], ["ELBOW"]),

    # ── Glutes / hip ──
    "Glute Bridge":                 (["HIP"], ["LUMBAR_SPINE", "KNEE"]),
    "Hip Thrust":                   (["HIP"], ["LUMBAR_SPINE", "KNEE"]),
    "Hip Thrust (Couch/Bench)":     (["HIP"], ["LUMBAR_SPINE", "KNEE"]),
    "Band Hip Thrust":              (["HIP"], ["LUMBAR_SPINE", "KNEE"]),
    "Single-Leg Glute Bridge":      (["HIP"], ["LUMBAR_SPINE", "KNEE"]),
    "Cable Pull-Through":           (["HIP"], ["LUMBAR_SPINE", "KNEE"]),
    "Adductor Machine":             (["HIP"], ["KNEE"]),
    "Abductor Machine":             (["HIP"], ["KNEE"]),
    "Band Lateral Walk":            (["HIP"], ["KNEE", "ANKLE"]),

    # ── Lunge variants ──
    "Step-Up":                      (["KNEE", "HIP"], ["ANKLE"]),
    "Reverse Lunge":                (["KNEE", "HIP"], ["ANKLE"]),
    "Walking Lunge":                (["KNEE", "HIP"], ["ANKLE"]),
    "Skater Lunge":                 (["KNEE", "HIP"], ["ANKLE"]),
    "Curtsy Lunge":                 (["KNEE", "HIP"], ["ANKLE"]),
    "Front-Rack Reverse Lunge":     (["KNEE", "HIP"], ["ANKLE", "SHOULDER", "LUMBAR_SPINE"]),
    "Box Step-Over":                (["KNEE", "HIP"], ["ANKLE"]),

    # ── Wall / static ──
    "Wall Sit":                     (["KNEE", "HIP"], ["ANKLE"]),

    # ── Core ──
    "Plank":                        (["LUMBAR_SPINE"], ["SHOULDER", "HIP"]),
    "Ab Wheel Rollout":             (["LUMBAR_SPINE", "SHOULDER"], ["ELBOW", "HIP"]),
    "Cable Crunch":                 (["LUMBAR_SPINE"], ["HIP"]),
    "Russian Twist":                (["LUMBAR_SPINE"], ["HIP", "SHOULDER"]),
    "Hanging Leg Raise":            (["HIP", "LUMBAR_SPINE"], ["SHOULDER", "ELBOW"]),
    "Dead Bug":                     (["LUMBAR_SPINE"], ["HIP", "SHOULDER"]),
    "Pallof Press":                 (["LUMBAR_SPINE"], ["SHOULDER", "ELBOW"]),
    "Dragon Flag":                  (["LUMBAR_SPINE", "HIP"], ["SHOULDER"]),
    "Landmine Rotation":            (["LUMBAR_SPINE", "SHOULDER"], ["HIP", "ELBOW"]),
    "Hollow Body Hold":             (["LUMBAR_SPINE", "HIP"], ["SHOULDER"]),
    "Side Plank":                   (["LUMBAR_SPINE"], ["SHOULDER", "HIP"]),
    "Bicycle Crunch":               (["LUMBAR_SPINE", "HIP"], []),
    "L-Sit":                        (["HIP", "LUMBAR_SPINE"], ["SHOULDER", "ELBOW", "WRIST"]),
    "Landmine Anti-Rotation Press": (["LUMBAR_SPINE", "SHOULDER"], ["ELBOW"]),
    "Suitcase Carry":               (["LUMBAR_SPINE"], ["SHOULDER", "HIP", "KNEE"]),
    "Toes-to-Bar":                  (["HIP", "LUMBAR_SPINE"], ["SHOULDER", "ELBOW"]),
    "Windshield Wiper (Floor)":     (["LUMBAR_SPINE", "HIP"], []),
    "Windshield Wiper (Bar)":       (["HIP", "LUMBAR_SPINE"], ["SHOULDER"]),
    "Knee-to-Elbow (Bar)":          (["HIP", "LUMBAR_SPINE"], ["SHOULDER"]),
    "Mountain Climber":             (["HIP", "LUMBAR_SPINE"], ["SHOULDER", "KNEE"]),
    "Spiderman Plank":              (["LUMBAR_SPINE", "HIP"], ["SHOULDER", "KNEE"]),
    "Plank Shoulder Tap":           (["LUMBAR_SPINE", "SHOULDER"], ["HIP"]),
    "Hollow Rock":                  (["LUMBAR_SPINE", "HIP"], []),
    "V-Up":                         (["HIP", "LUMBAR_SPINE"], []),
    "Tuck Sit":                     (["HIP", "LUMBAR_SPINE"], ["SHOULDER", "WRIST"]),
    "Pallof Press (Iso-Band)":      (["LUMBAR_SPINE"], ["SHOULDER", "ELBOW"]),
    "Bird-Dog":                     (["LUMBAR_SPINE"], ["HIP", "SHOULDER"]),
    "Arch-Body Hold (Superman)":    (["LUMBAR_SPINE"], ["HIP", "SHOULDER"]),

    # ── Olympic / power ──
    "Farmer's Walk":                (["SHOULDER"], ["LUMBAR_SPINE", "WRIST", "HIP", "KNEE"]),
    "Farmer's Walk (Heavy Bags)":   (["SHOULDER"], ["LUMBAR_SPINE", "WRIST", "HIP", "KNEE"]),
    "Sled Push":                    (["HIP", "KNEE"], ["ANKLE", "SHOULDER", "LUMBAR_SPINE"]),
    "Sled Pull":                    (["HIP", "KNEE"], ["ANKLE", "SHOULDER"]),
    "Battle Ropes":                 (["SHOULDER"], ["ELBOW", "WRIST", "LUMBAR_SPINE"]),
    "Box Jump":                     (["KNEE", "HIP"], ["ANKLE"]),
    "Medicine Ball Slam":           (["SHOULDER", "LUMBAR_SPINE"], ["ELBOW", "HIP"]),
    "Broad Jump":                   (["KNEE", "HIP"], ["ANKLE"]),
    "Kettlebell Swing":             (["HIP", "LUMBAR_SPINE"], ["SHOULDER", "KNEE"]),
    "Turkish Get-Up":               (["SHOULDER", "HIP"], ["KNEE", "ELBOW", "WRIST", "LUMBAR_SPINE"]),
    "Power Clean":                  (["HIP", "KNEE", "SHOULDER"], ["ANKLE", "ELBOW", "LUMBAR_SPINE"]),
    "Barbell Snatch":               (["HIP", "SHOULDER"], ["KNEE", "ELBOW", "WRIST", "ANKLE", "LUMBAR_SPINE"]),
    "Clean and Jerk":               (["HIP", "SHOULDER", "KNEE"], ["ELBOW", "WRIST", "ANKLE", "LUMBAR_SPINE"]),
    "Kettlebell Clean":             (["HIP", "SHOULDER"], ["ELBOW", "WRIST"]),
    "Kettlebell Snatch":            (["HIP", "SHOULDER"], ["ELBOW", "WRIST"]),
    "Kettlebell Thruster":          (["HIP", "KNEE", "SHOULDER"], ["ELBOW", "WRIST", "ANKLE"]),
    "Wall Ball Shot":               (["KNEE", "HIP", "SHOULDER"], ["ANKLE", "ELBOW"]),
    "American Kettlebell Swing":    (["HIP", "LUMBAR_SPINE", "SHOULDER"], ["KNEE", "ELBOW"]),
    "Dumbbell Devil Press":         (["HIP", "SHOULDER"], ["KNEE", "ELBOW", "WRIST"]),

    # ── Bodyweight / calisthenics ──
    "Burpee (Standard)":            (["HIP", "KNEE", "SHOULDER"], ["ANKLE", "ELBOW", "WRIST", "LUMBAR_SPINE"]),
    "Burpee":                       (["HIP", "KNEE", "SHOULDER"], ["ANKLE", "ELBOW", "WRIST", "LUMBAR_SPINE"]),
    "Bear Crawl":                   (["SHOULDER", "HIP", "KNEE"], ["ELBOW", "WRIST", "ANKLE"]),
    "Tuck Jump":                    (["KNEE", "HIP"], ["ANKLE"]),

    # ── Conditioning ──
    "Double Under (Jump Rope)":     (["ANKLE", "KNEE"], ["SHOULDER", "WRIST"]),
    "Single Under":                 (["ANKLE", "KNEE"], ["SHOULDER", "WRIST"]),
    "Shadow Boxing":                (["SHOULDER", "ELBOW"], ["WRIST", "HIP"]),
    "Jumping Jacks":                (["SHOULDER", "HIP"], ["KNEE", "ANKLE"]),
}


def build_json_array(joint_names):
    if not joint_names:
        return ""
    return "[" + ",".join(f'"{j}"' for j in joint_names) + "]"


def main():
    with open(JSON_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    exercises = data["exercises"]
    updated = 0
    missing = []

    for ex in exercises:
        name = ex["name"]
        if name in JOINT_MAP:
            primary, secondary = JOINT_MAP[name]
            ex["primaryJoints"] = build_json_array(primary)
            ex["secondaryJoints"] = build_json_array(secondary)
            updated += 1
        else:
            missing.append(name)
            # Leave fields absent so Exercise entity uses its "" default

    data["version"] = "1.9"
    data["lastUpdated"] = "2026-04-17"

    with open(JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print(f"Updated {updated}/{len(exercises)} exercises.")
    if missing:
        print(f"No joint mapping for {len(missing)} exercises (will use empty defaults):")
        for m in missing:
            print(f"  - {m}")


if __name__ == "__main__":
    main()
