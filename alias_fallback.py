import os
import shutil

TARGET_DIR = "/Users/omerhedvat/git/AndroidStudioProjects-PowerME/app/src/main/assets/exercise_animations"
MISSING_FILE = os.path.join(TARGET_DIR, "missing_animations.txt")

if not os.path.exists(MISSING_FILE):
    exit()

with open(MISSING_FILE, "r") as f:
    missing = [line.strip() for line in f if line.strip()]

# Basic semantic map of very similar exercises already likely completed
aliases = {
    "bandpullapartresistanceband": "bandpullapart",
    "facepullresistanceband": "facepull",
    "sissysquatbodyweight": "sissysquat",
    "burpeestandard": "burpee",
    "australianpullupinvertedrow": "invertedrow",
    "nordichamstringcurl": "nordiccurl",
    "commandopullup": "pullup",
    "chestsupportedrow": "dumbbellrow", # close enough
    "peckdeckfly": "machinechestpress",
    "reversepecdeck": "reardeltfly",
    "singlearmaustralinanrow": "invertedrow",
    "cablepullover": "dumbbellpullover",
    "cablerow": "tbarrow",
    "calfraisesingleleg": "standingcalfraise",
    "chesttobarpullup": "pullup",
    "conventionaldeadlift": "stifflegdeadlift",
    "curtsylunge": "reverselunge",
    "explosivepullupchesttobar": "pullup",
    "hiphrustcouchbench": "hipthrust",
    "negativepullup": "pullup",
    "neutralgrippullup": "chinup",
    "pausesquat": "barbellbacksquat",
    "pendlayrow": "barbellrow",
    "safetybarsquat": "barbellbacksquat",
    "windshieldwiperbar": "hanginglegraise",
    "windshieldwiperfloor": "hanginglegraise",
    "legpresscalfraise": "legpress",
    "staggeredhandpushup": "pushups",
    "hindupushup": "pushups",
    "tigerbendpushup": "pushups",
    "typewriterpushup": "pushups",
    "singleunder": "doubleunderjumprope"
}

still_missing = []
recovered = 0

for m in missing:
    if m in aliases:
        source_name = aliases[m] + ".webp"
        source_path = os.path.join(TARGET_DIR, source_name)
        if os.path.exists(source_path):
            # To distinguish a real animation from placeholder, placeholders are < 15kb
            if os.path.getsize(source_path) > 15000:
                shutil.copy(source_path, os.path.join(TARGET_DIR, f"{m}.webp"))
                recovered += 1
                continue
    # Could try checking if another file name is a substring
    found = False
    for f_name in os.listdir(TARGET_DIR):
        if not f_name.endswith(".webp"): continue
        f_stem = f_name.replace(".webp", "")
        if f_stem != m and (f_stem in m or m in f_stem):
            source_path = os.path.join(TARGET_DIR, f_name)
            if os.path.getsize(source_path) > 15000:
                shutil.copy(source_path, os.path.join(TARGET_DIR, f"{m}.webp"))
                found = True
                recovered += 1
                break
    
    if not found:
        still_missing.append(m)

with open(MISSING_FILE, "w") as f:
    for sm in still_missing:
        f.write(sm + "\n")

print(f"Recovered {recovered} animations via alias/substring matching. Still missing: {len(still_missing)}")
