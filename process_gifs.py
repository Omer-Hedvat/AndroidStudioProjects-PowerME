import os
import json
import re
import difflib
from PIL import Image, ImageSequence
from io import BytesIO

TARGET_DIR = "/Users/omerhedvat/git/AndroidStudioProjects-PowerME/app/src/main/assets/exercise_animations"
DB_DIR = "/tmp/hasaneyldrm_db"
JSON_PATH = os.path.join(DB_DIR, "data", "exercises.json")

search_names_raw = """
abductormachine, abwheelrollout, adductormachine, americankettlebellswing,
  archbodyholdsuperman, archerpullup, archerpushup, arnoldpress,
  aroundtheworldpullupbar, assistedpullup, australianpullupinvertedrow,
  bandbicepcurl, bandhipthrust, bandlateralwalk, bandpullapart,
  bandpullapartresistanceband, bandtriceppushdown, barbellbacksquat, barbellcurl,
  barbellflatbenchpress, barbellrow, barbellshrug, barbellsnatch, battleropes,
  bearcrawl, beltsquat, benchdipfeetelevated, benchdips, bicyclecrunch, birddog,
  boxjump, boxsquat, boxstepover, broadjump, bulgariansplitsquat, burpee,
  burpeestandard, cablechestfly, cablecrossover, cablecrunch, cablecurl,
  cablefrontraise, cablehammercurl, cablelateralraise,
  cableoverheadtricepextension, cablepullover, cablepullthrough, cablerow,
  cableuprightrow, calfraisesingleleg, chestsupportedrow, chesttobarpullup,
  chinup, clappushup, cleanandjerk, closegripbenchpress, closegriplatpulldown,
  commandopullup, concentrationcurl, conventionaldeadlift, cossacksquat,
  curtsylunge, dbaroundtheworld, deadbug, deadhang, declinebarbellbenchpress,
  declinedumbbellpress, declinepushup, deficitdeadlift, diamondpushup, dips,
  donkeycalfraise, doubleunderjumprope, dragonflag, duckwalk, dumbbellchestfly,
  dumbbellcurl, dumbbelldevilpress, dumbbellflatbenchpress, dumbbellpullover,
  dumbbellrow, explosivepullupchesttobar, ezbarcurl, ezbarskullcrusher, facepull,
  facepullresistanceband, farmerswalk, farmerswalkheavybags,
  frontrackreverselunge, frontraise, frontsquat, glutebridge, gobletsquat,
  goodmorning, hacksquat, hammercurl, handstandhold, handstandpushup,
  hanginglegraise, hindupushup, hiphrustcouchbench, hipthrust, hollowbodyhold,
  hollowrock, horsestance, inclinebarbellbenchpress, inclinecurl,
  inclinedumbbellbenchpress, inclinedumbbellrow, inclinepushup, invertedrow,
  jmpress, jumpingjacks, kettlebellclean, kettlebellpress,
  kettlebellromaniandeadlift, kettlebellrow, kettlebellsnatch, kettlebellswing,
  kettlebellthruster, kneelingsinglearmlatpulldown, kneetoelbowbar,
  landmineantirotationpress, landminepress, landminerotation, landminesquat,
  lateralraise, latpulldown, legextension, legpress, legpresscalfraise, lsit,
  lsitpullup, lyinglegcurl, machinechestpress, machinelateralraise,
  machinepreachercurl, machineshoulderpress, meadowsrow, medicineballslam,
  mountainclimber, muscleuprings, negativepullup, neutralgrippullup, nordiccurl,
  nordichamstringcurl, overheadsquat, overheadtricepextension, pallofpress,
  pallofpressisoband, pausesquat, peckdeckfly, pendlayrow, pikepushup,
  pistolsquat, plank, plankshouldertap, powerclean, preachercurl,
  pseudoplanchehold, pseudoplanchepushup, pullup, pushups, rackpull, reardeltfly,
  reversecurl, reverselunge, reversenordic, reversepecdeck, ringdip, ringpushup,
  romaniandeadliftrdlbb, romaniandeadliftrdldb, ropetriceppushdown, russiantwist,
  safetybarsquat, scapularpullup, sealrow, seatedbarbelloverheadpress,
  seatedcalfraise, seateddumbbelloverheadpress, seatedlegcurl, shadowboxing,
  shrimpsquat, shrugs, sideplank, singlearmaustralinanrow, singlearmcablerow,
  singlearmpushup, singlelegglutebridge, singlelegpress, singlelegrdl,
  singleunder, sissysquat, sissysquatbodyweight, skaterlunge, skullcrusher,
  sledpull, sledpush, smithmachinebenchpress, smithmachinebulgariansplitsquat,
  smithmachineoverheadpress, smithmachineromaniandeadlift, smithmachinesquat,
  spidercurl, spidermanplank, staggeredhandpushup, standingbarbelloverheadpress,
  standingcalfraise, stepup, stifflegdeadlift, straightarmpulldown,
  suitcasecarry, sumodeadlift, sumosquat, tatepress, tbarrow, tibialisraisewall,
  tigerbendpushup, toestobar, trapbardeadlift, tricepkickback, triceppushdown,
  tuckjump, tucksit, turkishgetup, typewriterpushup, uprightrow, vup,
  walkinglunge, wallballshot, wallsit, weightedpullup, weightedpushup,
  widegriplatpulldown, windshieldwiperbar, windshieldwiperfloor, wristcurl,
  zottmancurl, zpress
"""

search_names = [x.strip() for x in search_names_raw.replace("\n", "").split(",") if x.strip()]

def format_name(s):
    return re.sub(r'[\s\-()]', '', s).lower()

def is_placeholder(path):
    if not os.path.exists(path):
        return True
    return os.path.getsize(path) < 15000

print("Loading dataset...")
with open(JSON_PATH, 'r') as f:
    db = json.load(f)

name_to_gif = {}
for ex in db:
    norm_name = format_name(ex['name'])
    gif_path = os.path.join(DB_DIR, ex['gif_url'])
    name_to_gif[norm_name] = gif_path

candidate_keys = list(name_to_gif.keys())
missing = []
TARGET_SIZE = 400

def process_gif(input_path, output_path):
    if not os.path.exists(input_path):
        return False
    try:
        img = Image.open(input_path)
        frames = []
        for frame in ImageSequence.Iterator(img):
            f = frame.copy().convert("RGBA")
            max_dim = max(f.width, f.height)
            sq = Image.new("RGBA", (max_dim, max_dim), (0,0,0,255))
            sq.paste(f, ((max_dim - f.width)//2, (max_dim - f.height)//2))
            sq = sq.resize((TARGET_SIZE, TARGET_SIZE), Image.Resampling.LANCZOS)
            frames.append(sq)
            
        if not frames:
            return False
            
        duration = img.info.get("duration", 100) or 100
        # Force frame rate around 10-12 fps
        duration = max(min(duration, 100), 83)
        limit_kb = 200
        quality = 75
        
        while quality >= 10:
            b = BytesIO()
            frames[0].save(
                b,
                format="webp",
                save_all=True,
                append_images=frames[1:],
                loop=0,
                duration=duration,
                quality=quality,
                method=4
            )
            v = b.getvalue()
            if len(v) / 1024.0 < limit_kb:
                with open(output_path, "wb") as f:
                    f.write(v)
                return True
            quality -= 5
            
        return False
    except Exception as e:
        print(f"Error processing {input_path}: {e}")
        return False

found_count = 0
for sn in search_names:
    out_path = os.path.join(TARGET_DIR, f"{sn}.webp")
        
    gif_path = name_to_gif.get(sn)
    if not gif_path:
        match = difflib.get_close_matches(sn, candidate_keys, n=1, cutoff=0.8)
        if match:
            gif_path = name_to_gif[match[0]]
            
    if gif_path:
        success = process_gif(gif_path, out_path)
        if success:
            found_count += 1
            print(f"Processed: {sn}")
        else:
            missing.append(sn)
    else:
        missing.append(sn)

miss_path = os.path.join(TARGET_DIR, "missing_animations.txt")
with open(miss_path, "w") as f:
    for m in missing:
        f.write(m + "\n")
        
print(f"Total processed currently: {found_count}, missing appended: {len(missing)}")
