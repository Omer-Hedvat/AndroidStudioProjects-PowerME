# Gemini Prompt: Populate Exercise Animations

Copy the full prompt below into Gemini (Google AI Studio or similar) to generate a Python script that downloads and converts exercise animations for the PowerME app.

---

## Prompt

You are helping me populate offline exercise animation files for an Android fitness app called PowerME.

**Goal:** Download animated GIF exercise demonstrations for each exercise in our library, convert them to Animated WebP format, and name them according to our naming convention.

**Naming convention:** `{searchName}.webp`
Where `searchName = exercise.name.lowercase().replace(Regex("[\\s\\-()]"), "")`

Examples:
- "Barbell Back Squat" → `barbellbacksquat.webp`
- "Romanian Deadlift (RDL) - BB" → `romaniandeadliftrdlbb.webp`
- "Pull-Up" → `pullup.webp`
- "Face Pull" → `facepull.webp`

**Output directory:** `app/src/main/assets/exercise_animations/`

**Quality requirements:**
- Format: Animated WebP
- Target size: under 200 KB per file
- Target dimensions: 400×400 px (square crop)
- Frame rate: 15–20 fps
- Duration: 2–4 seconds (looping)

**Source API:** Use the free WGER Workout Manager API (https://wger.de/api/v2/exercise/) and/or ExerciseDB (RapidAPI). Prefer WGER as it's free and open source. For exercises not found via API, fall back to a placeholder (a static grey square with the exercise name as text).

**Full exercise list (240 exercises) with their target filenames:**

```
barbellbacksquat.webp          <- Barbell Back Squat
frontsquat.webp                <- Front Squat
gobletsquat.webp               <- Goblet Squat
bulgariansplitsquat.webp       <- Bulgarian Split Squat
legpress.webp                  <- Leg Press
hacksquat.webp                 <- Hack Squat
safetybarsquat.webp            <- Safety Bar Squat
boxsquat.webp                  <- Box Squat
pausesquat.webp                <- Pause Squat
sumosquat.webp                 <- Sumo Squat
conventionaldeadlift.webp      <- Conventional Deadlift
romaniandeadliftrdlbb.webp     <- Romanian Deadlift (RDL) - BB
trapbardeadlift.webp           <- Trap Bar Deadlift
sumodeadlift.webp              <- Sumo Deadlift
singlelegrdl.webp              <- Single-Leg RDL
deficitdeadlift.webp           <- Deficit Deadlift
rackpull.webp                  <- Rack Pull
stifflegdeadlift.webp          <- Stiff-Leg Deadlift
barbbellflatbenchpress.webp    <- Barbell Flat Bench Press
inclinebarbellbenchpress.webp  <- Incline Barbell Bench Press
declinebarbellbenchpress.webp  <- Decline Barbell Bench Press
dumbbbellflatbenchpress.webp   <- Dumbbell Flat Bench Press
inclinedumbbellbenchpress.webp <- Incline Dumbbell Bench Press
cablechestfly.webp             <- Cable Chest Fly
machinechestpress.webp         <- Machine Chest Press
closegripbenchpress.webp       <- Close-Grip Bench Press
pushups.webp                   <- Push-Ups
barbellrow.webp                <- Barbell Row
dumbbellrow.webp               <- Dumbbell Row
chestsupportedrow.webp         <- Chest-Supported Row
cablerow.webp                  <- Cable Row
tbarrow.webp                   <- T-Bar Row
sealrow.webp                   <- Seal Row
pendlayrow.webp                <- Pendlay Row
invertedrow.webp               <- Inverted Row
standingbarbelloverheadpress.webp  <- Standing Barbell Overhead Press
seateddumbbelloverheadpress.webp   <- Seated Dumbbell Overhead Press
arnoldpress.webp               <- Arnold Press
machineshoulderpress.webp      <- Machine Shoulder Press
landminepress.webp             <- Landmine Press
pikepushup.webp                <- Pike Push-Up
pullup.webp                    <- Pull-Up
chinup.webp                    <- Chin-Up
neutralgrippullup.webp         <- Neutral Grip Pull-Up
latpulldown.webp               <- Lat Pulldown
closegriplatpulldown.webp      <- Close-Grip Lat Pulldown
straightarmpulldown.webp       <- Straight-Arm Pulldown
assistedpullup.webp            <- Assisted Pull-Up
lyinglegcurl.webp              <- Lying Leg Curl
seatedlegcurl.webp             <- Seated Leg Curl
nordiccurl.webp                <- Nordic Curl
legextension.webp              <- Leg Extension
standingcalfraise.webp         <- Standing Calf Raise
seatedcalfraise.webp           <- Seated Calf Raise
barbellcurl.webp               <- Barbell Curl
dumbbellcurl.webp              <- Dumbbell Curl
hammercurl.webp                <- Hammer Curl
cablecurl.webp                 <- Cable Curl
triceppushdown.webp            <- Tricep Pushdown
overheadtricepextension.webp   <- Overhead Tricep Extension
skullcrusher.webp              <- Skull Crusher
dips.webp                      <- Dips
lateralraise.webp              <- Lateral Raise
facepull.webp                  <- Face Pull
reardeltfly.webp               <- Rear Delt Fly
shrugs.webp                    <- Shrugs
plank.webp                     <- Plank
abwheelrollout.webp            <- Ab Wheel Rollout
cablecrunch.webp               <- Cable Crunch
russiantwist.webp              <- Russian Twist
hanginglegraise.webp           <- Hanging Leg Raise
farmerswalk.webp               <- Farmer's Walk
sledpush.webp                  <- Sled Push
sledpull.webp                  <- Sled Pull
battleropes.webp               <- Battle Ropes
boxjump.webp                   <- Box Jump
medicinebillslam.webp          <- Medicine Ball Slam
broadjump.webp                 <- Broad Jump
kettlebellswing.webp           <- Kettlebell Swing
turkishgetup.webp              <- Turkish Get-Up
glutebridge.webp               <- Glute Bridge
hipthrust.webp                 <- Hip Thrust
wallsit.webp                   <- Wall Sit
stepup.webp                    <- Step-Up
reverselunge.webp              <- Reverse Lunge
walkinglunge.webp              <- Walking Lunge
sissysquat.webp                <- Sissy Squat
goodmorning.webp               <- Good Morning
preachercurl.webp              <- Preacher Curl
concentrationcurl.webp         <- Concentration Curl
inclinecurl.webp               <- Incline Curl
tricepkickback.webp            <- Tricep Kickback
diamondpushup.webp             <- Diamond Push-Up
benchdips.webp                 <- Bench Dips
uprightrow.webp                <- Upright Row
frontraise.webp                <- Front Raise
cablelateralraise.webp         <- Cable Lateral Raise
reversepecdeck.webp            <- Reverse Pec Deck
adductormachine.webp           <- Adductor Machine
abductormachine.webp           <- Abductor Machine
donkeycalfraise.webp           <- Donkey Calf Raise
wristcurl.webp                 <- Wrist Curl
deadhang.webp                  <- Dead Hang
declinedumbbellpress.webp      <- Decline Dumbbell Press
peckdeckfly.webp               <- Pec Deck Fly
cablecrossover.webp            <- Cable Crossover
ringpushup.webp                <- Ring Push-Up
weightedpushup.webp            <- Weighted Push-Up
smithmachinebenchpress.webp    <- Smith Machine Bench Press
dumbbellpullover.webp          <- Dumbbell Pullover
declinepushup.webp             <- Decline Push-Up
dumbbellchestfly.webp          <- Dumbbell Chest Fly
deadbug.webp                   <- Dead Bug
pallofpress.webp               <- Pallof Press
dragonflag.webp                <- Dragon Flag
landminerotation.webp          <- Landmine Rotation
hollowbodyhold.webp            <- Hollow Body Hold
sideplank.webp                 <- Side Plank
bicyclecrunch.webp             <- Bicycle Crunch
lsit.webp                      <- L-Sit
landmineantirotationpress.webp <- Landmine Anti-Rotation Press
suitcasecarry.webp             <- Suitcase Carry
ezbarcurl.webp                 <- EZ Bar Curl
ezbarskullcrusher.webp         <- EZ Bar Skullcrusher
cableoverheadtricepextension.webp <- Cable Overhead Tricep Extension
reversecurl.webp               <- Reverse Curl
ropetriceppushdown.webp        <- Rope Tricep Pushdown
zottmancurl.webp               <- Zottman Curl
tatepress.webp                 <- Tate Press
machinepreachercurl.webp       <- Machine Preacher Curl
cablehammercurl.webp           <- Cable Hammer Curl
spidercurl.webp                <- Spider Curl
jmpress.webp                   <- JM Press
romaniandeadliftrdldb.webp     <- Romanian Deadlift (RDL) - DB
weightedpullup.webp            <- Weighted Pull-Up
inclinedumbbellrow.webp        <- Incline Dumbbell Row
singlearmcablerow.webp         <- Single-Arm Cable Row
meadowsrow.webp                <- Meadows Row
widegriplatpulldown.webp       <- Wide-Grip Lat Pulldown
cablepullover.webp             <- Cable Pullover
barbellshrug.webp              <- Barbell Shrug
kneeelingsinglearmlatpulldown.webp <- Kneeling Single-Arm Lat Pulldown
seatedbarbelloverheadpress.webp <- Seated Barbell Overhead Press
zpress.webp                    <- Z-Press
cablefrontraise.webp           <- Cable Front Raise
machinelateralraise.webp       <- Machine Lateral Raise
cableuprightrow.webp           <- Cable Upright Row
bandpullapart.webp             <- Band Pull-Apart
pistolsquat.webp               <- Pistol Squat
singlelegpress.webp            <- Single-Leg Press
beltsquat.webp                 <- Belt Squat
legpresscalfraise.webp         <- Leg Press Calf Raise
cablepullthrough.webp          <- Cable Pull-Through
landminesquat.webp             <- Landmine Squat
kettlebellclean.webp           <- Kettlebell Clean
kettlebellpress.webp           <- Kettlebell Press
kettlebellsnatch.webp          <- Kettlebell Snatch
kettlebellrow.webp             <- Kettlebell Row
kettlebellromaniandeadlift.webp <- Kettlebell Romanian Deadlift
bandbicepcurl.webp             <- Band Bicep Curl
bandtriceppushdown.webp        <- Band Tricep Pushdown
bandhipthrust.webp             <- Band Hip Thrust
bandlateralwalk.webp           <- Band Lateral Walk
bandpullapartresistanceband.webp <- Band Pull-Apart (Resistance Band)
smithmachinesquat.webp         <- Smith Machine Squat
smithmachineoverheadpress.webp <- Smith Machine Overhead Press
smithmachineromaniandeadlift.webp <- Smith Machine Romanian Deadlift
smithmachinebulgariansplitsquat.webp <- Smith Machine Bulgarian Split Squat
powerclean.webp                <- Power Clean
barbellsnatch.webp             <- Barbell Snatch
cleanandjerk.webp              <- Clean and Jerk
muscleuprings.webp             <- Muscle-Up (Rings)
handstandpushup.webp           <- Handstand Push-Up
burpeestandard.webp            <- Burpee (Standard)
toestobar.webp                 <- Toes-to-Bar
wallballshot.webp              <- Wall Ball Shot
kettlebellthruster.webp        <- Kettlebell Thruster
overheadsquat.webp             <- Overhead Squat
archbodyholdsuperman.webp      <- Arch-Body Hold (Superman)
chesttobarpullup.webp          <- Chest-to-Bar Pull-Up
ringdip.webp                   <- Ring Dip
frontrackreverselunge.webp     <- Front-Rack Reverse Lunge
boxstepover.webp               <- Box Step-Over
americankettlebellswing.webp   <- American Kettlebell Swing
bearcrawl.webp                 <- Bear Crawl
dumbbbelldevilpress.webp       <- Dumbbell Devil Press
tucksit.webp                   <- Tuck Sit
handstandhold.webp             <- Handstand Hold
pseudoplanchehold.webp         <- Pseudo-Planche Hold
horsestance.webp               <- Horse Stance
duckwalk.webp                  <- Duck Walk
birddog.webp                   <- Bird-Dog
pallofpressiso-band.webp       <- Pallof Press (Iso-Band)
archerpushup.webp              <- Archer Push-up
typewriterpushup.webp          <- Typewriter Push-up
pseudoplanchepushup.webp       <- Pseudo-Planche Push-up
clappushup.webp                <- Clap Push-up
singlearmpushup.webp           <- Single-Arm Push-up
staggeredhandpushup.webp       <- Staggered-Hand Push-up
tigerbendpushup.webp           <- Tiger Bend Push-up
benchdipfeeteleevated.webp     <- Bench Dip (Feet Elevated)
inclinepushup.webp             <- Incline Push-up
hindupushup.webp               <- Hindu Push-up
dbaroundtheworld.webp          <- DB Around the World
aroundtheworldpullupbar.webp   <- Around the World (Pull-up Bar)
negativepullup.webp            <- Negative Pull-up
commandopullup.webp            <- Commando Pull-up
archerpullup.webp              <- Archer Pull-up
australianpullupinvertedrow.webp <- Australian Pull-up (Inverted Row)
facepullresistanceband.webp    <- Face Pull (Resistance Band)
lsitpullup.webp                <- L-Sit Pull-up
explosivepullupchesttobar.webp <- Explosive Pull-up (Chest-to-Bar)
singlearmaustralinanrow.webp   <- Single-Arm Australian Row
scapularpullup.webp            <- Scapular Pull-up
shrimpsquat.webp               <- Shrimp Squat
skaterlunge.webp               <- Skater Lunge
curtsylunge.webp               <- Curtsy Lunge
sissysquatbodyweight.webp      <- Sissy Squat (Bodyweight)
reversenordic.webp             <- Reverse Nordic
nordichamstringcurl.webp       <- Nordic Hamstring Curl
cossacksquat.webp              <- Cossack Squat
singlelegglbutebridge.webp     <- Single-Leg Glute Bridge
hiphrustcouchbench.webp        <- Hip Thrust (Couch/Bench)
calfraisesingleleg.webp        <- Calf Raise (Single-Leg)
tibialisraisewwall.webp        <- Tibialis Raise (Wall)
tuckjump.webp                  <- Tuck Jump
hollowrock.webp                <- Hollow Rock
vup.webp                       <- V-Up
windshieldwiperflloor.webp     <- Windshield Wiper (Floor)
windshieldwiperbar.webp        <- Windshield Wiper (Bar)
kneettoelbowbar.webp           <- Knee-to-Elbow (Bar)
mountainclimber.webp           <- Mountain Climber
spidermanplank.webp            <- Spiderman Plank
plankshoulddertap.webp         <- Plank Shoulder Tap
doubleunderjumprope.webp       <- Double Under (Jump Rope)
singleunder.webp               <- Single Under
burpee.webp                    <- Burpee
shadowboxing.webp              <- Shadow Boxing
farmerswalkheeavybags.webp     <- Farmer's Walk (Heavy Bags)
jumpingjacks.webp              <- Jumping Jacks
```

**Task:** Write a Python 3 script (`populate_animations.py`) that does the following:

1. **Define the mapping** — a dict `EXERCISES` mapping `filename_stem` (without `.webp`) → `display_name`.

2. **Download from WGER API:**
   - Call `https://wger.de/api/v2/exercise/?format=json&language=2&limit=100` (paginate with `offset`) to get exercises
   - Also try `https://wger.de/api/v2/exerciseinfo/{id}/?format=json` for images
   - Match by normalizing WGER exercise names the same way (`lowercase, strip spaces/hyphens/parens`)
   - Download the best matching GIF/image (prefer animated)

3. **Fallback: ExerciseDB via RapidAPI** (if WGER has no image):
   - Use `https://exercisedb.p.rapidapi.com/exercises/name/{name}` with header `X-RapidAPI-Key: YOUR_KEY`
   - Each exercise object has a `"gifUrl"` field — download it

4. **Fallback: Generate placeholder:**
   - If no animation found, create a 400×400 grey animated WebP with 2 frames (fade in/out)
   - Render the display name as white text centred in the frame

5. **Convert to Animated WebP:**
   - Use `Pillow` (PIL) with `imageio` for GIF→WebP conversion
   - Resize to 400×400 (pad to square with black bars, preserve aspect ratio)
   - Target: ≤200 KB. If over limit, reduce quality (start at 75, step down by 5 until under limit)
   - Save as animated WebP: `img.save(output_path, format='webp', save_all=True, loop=0, quality=75)`

6. **Output directory:** `app/src/main/assets/exercise_animations/` (create if not exists)

7. **Progress reporting:** Print `[OK] filename.webp (45 KB)` or `[PLACEHOLDER] filename.webp` for each file.

8. **Resume support:** Skip files that already exist in the output directory.

**Dependencies to install:**
```bash
pip install Pillow imageio requests
```

Generate the complete `populate_animations.py` script. Include all 240 exercises in the `EXERCISES` dict using the filename stems and display names listed above.
