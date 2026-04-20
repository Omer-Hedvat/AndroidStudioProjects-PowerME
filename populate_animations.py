import os
import re
import io
import requests
from PIL import Image, ImageDraw
import imageio.v3 as iio

EXERCISES = {
    "barbellbacksquat": "Barbell Back Squat",
    "frontsquat": "Front Squat",
    "gobletsquat": "Goblet Squat",
    "bulgariansplitsquat": "Bulgarian Split Squat",
    "legpress": "Leg Press",
    "hacksquat": "Hack Squat",
    "safetybarsquat": "Safety Bar Squat",
    "boxsquat": "Box Squat",
    "pausesquat": "Pause Squat",
    "sumosquat": "Sumo Squat",
    "conventionaldeadlift": "Conventional Deadlift",
    "romaniandeadliftrdlbb": "Romanian Deadlift (RDL) - BB",
    "trapbardeadlift": "Trap Bar Deadlift",
    "sumodeadlift": "Sumo Deadlift",
    "singlelegrdl": "Single-Leg RDL",
    "deficitdeadlift": "Deficit Deadlift",
    "rackpull": "Rack Pull",
    "stifflegdeadlift": "Stiff-Leg Deadlift",
    "barbellflatbenchpress": "Barbell Flat Bench Press",
    "inclinebarbellbenchpress": "Incline Barbell Bench Press",
    "declinebarbellbenchpress": "Decline Barbell Bench Press",
    "dumbbellflatbenchpress": "Dumbbell Flat Bench Press",
    "inclinedumbbellbenchpress": "Incline Dumbbell Bench Press",
    "cablechestfly": "Cable Chest Fly",
    "machinechestpress": "Machine Chest Press",
    "closegripbenchpress": "Close-Grip Bench Press",
    "pushups": "Push-Ups",
    "barbellrow": "Barbell Row",
    "dumbbellrow": "Dumbbell Row",
    "chestsupportedrow": "Chest-Supported Row",
    "cablerow": "Cable Row",
    "tbarrow": "T-Bar Row",
    "sealrow": "Seal Row",
    "pendlayrow": "Pendlay Row",
    "invertedrow": "Inverted Row",
    "standingbarbelloverheadpress": "Standing Barbell Overhead Press",
    "seateddumbbelloverheadpress": "Seated Dumbbell Overhead Press",
    "arnoldpress": "Arnold Press",
    "machineshoulderpress": "Machine Shoulder Press",
    "landminepress": "Landmine Press",
    "pikepushup": "Pike Push-Up",
    "pullup": "Pull-Up",
    "chinup": "Chin-Up",
    "neutralgrippullup": "Neutral Grip Pull-Up",
    "latpulldown": "Lat Pulldown",
    "closegriplatpulldown": "Close-Grip Lat Pulldown",
    "straightarmpulldown": "Straight-Arm Pulldown",
    "assistedpullup": "Assisted Pull-Up",
    "lyinglegcurl": "Lying Leg Curl",
    "seatedlegcurl": "Seated Leg Curl",
    "nordiccurl": "Nordic Curl",
    "legextension": "Leg Extension",
    "standingcalfraise": "Standing Calf Raise",
    "seatedcalfraise": "Seated Calf Raise",
    "barbellcurl": "Barbell Curl",
    "dumbbellcurl": "Dumbbell Curl",
    "hammercurl": "Hammer Curl",
    "cablecurl": "Cable Curl",
    "triceppushdown": "Tricep Pushdown",
    "overheadtricepextension": "Overhead Tricep Extension",
    "skullcrusher": "Skull Crusher",
    "dips": "Dips",
    "lateralraise": "Lateral Raise",
    "facepull": "Face Pull",
    "reardeltfly": "Rear Delt Fly",
    "shrugs": "Shrugs",
    "plank": "Plank",
    "abwheelrollout": "Ab Wheel Rollout",
    "cablecrunch": "Cable Crunch",
    "russiantwist": "Russian Twist",
    "hanginglegraise": "Hanging Leg Raise",
    "farmerswalk": "Farmer's Walk",
    "sledpush": "Sled Push",
    "sledpull": "Sled Pull",
    "battleropes": "Battle Ropes",
    "boxjump": "Box Jump",
    "medicineballslam": "Medicine Ball Slam",
    "broadjump": "Broad Jump",
    "kettlebellswing": "Kettlebell Swing",
    "turkishgetup": "Turkish Get-Up",
    "glutebridge": "Glute Bridge",
    "hipthrust": "Hip Thrust",
    "wallsit": "Wall Sit",
    "stepup": "Step-Up",
    "reverselunge": "Reverse Lunge",
    "walkinglunge": "Walking Lunge",
    "sissysquat": "Sissy Squat",
    "goodmorning": "Good Morning",
    "preachercurl": "Preacher Curl",
    "concentrationcurl": "Concentration Curl",
    "inclinecurl": "Incline Curl",
    "tricepkickback": "Tricep Kickback",
    "diamondpushup": "Diamond Push-Up",
    "benchdips": "Bench Dips",
    "uprightrow": "Upright Row",
    "frontraise": "Front Raise",
    "cablelateralraise": "Cable Lateral Raise",
    "reversepecdeck": "Reverse Pec Deck",
    "adductormachine": "Adductor Machine",
    "abductormachine": "Abductor Machine",
    "donkeycalfraise": "Donkey Calf Raise",
    "wristcurl": "Wrist Curl",
    "deadhang": "Dead Hang",
    "declinedumbbellpress": "Decline Dumbbell Press",
    "peckdeckfly": "Pec Deck Fly",
    "cablecrossover": "Cable Crossover",
    "ringpushup": "Ring Push-Up",
    "weightedpushup": "Weighted Push-Up",
    "smithmachinebenchpress": "Smith Machine Bench Press",
    "dumbbellpullover": "Dumbbell Pullover",
    "declinepushup": "Decline Push-Up",
    "dumbbellchestfly": "Dumbbell Chest Fly",
    "deadbug": "Dead Bug",
    "pallofpress": "Pallof Press",
    "dragonflag": "Dragon Flag",
    "landminerotation": "Landmine Rotation",
    "hollowbodyhold": "Hollow Body Hold",
    "sideplank": "Side Plank",
    "bicyclecrunch": "Bicycle Crunch",
    "lsit": "L-Sit",
    "landmineantirotationpress": "Landmine Anti-Rotation Press",
    "suitcasecarry": "Suitcase Carry",
    "ezbarcurl": "EZ Bar Curl",
    "ezbarskullcrusher": "EZ Bar Skullcrusher",
    "cableoverheadtricepextension": "Cable Overhead Tricep Extension",
    "reversecurl": "Reverse Curl",
    "ropetriceppushdown": "Rope Tricep Pushdown",
    "zottmancurl": "Zottman Curl",
    "tatepress": "Tate Press",
    "machinepreachercurl": "Machine Preacher Curl",
    "cablehammercurl": "Cable Hammer Curl",
    "spidercurl": "Spider Curl",
    "jmpress": "JM Press",
    "romaniandeadliftrdldb": "Romanian Deadlift (RDL) - DB",
    "weightedpullup": "Weighted Pull-Up",
    "inclinedumbbellrow": "Incline Dumbbell Row",
    "singlearmcablerow": "Single-Arm Cable Row",
    "meadowsrow": "Meadows Row",
    "widegriplatpulldown": "Wide-Grip Lat Pulldown",
    "cablepullover": "Cable Pullover",
    "barbellshrug": "Barbell Shrug",
    "kneelingsinglearmlatpulldown": "Kneeling Single-Arm Lat Pulldown",
    "seatedbarbelloverheadpress": "Seated Barbell Overhead Press",
    "zpress": "Z-Press",
    "cablefrontraise": "Cable Front Raise",
    "machinelateralraise": "Machine Lateral Raise",
    "cableuprightrow": "Cable Upright Row",
    "bandpullapart": "Band Pull-Apart",
    "pistolsquat": "Pistol Squat",
    "singlelegpress": "Single-Leg Press",
    "beltsquat": "Belt Squat",
    "legpresscalfraise": "Leg Press Calf Raise",
    "cablepullthrough": "Cable Pull-Through",
    "landminesquat": "Landmine Squat",
    "kettlebellclean": "Kettlebell Clean",
    "kettlebellpress": "Kettlebell Press",
    "kettlebellsnatch": "Kettlebell Snatch",
    "kettlebellrow": "Kettlebell Row",
    "kettlebellromaniandeadlift": "Kettlebell Romanian Deadlift",
    "bandbicepcurl": "Band Bicep Curl",
    "bandtriceppushdown": "Band Tricep Pushdown",
    "bandhipthrust": "Band Hip Thrust",
    "bandlateralwalk": "Band Lateral Walk",
    "bandpullapartresistanceband": "Band Pull-Apart (Resistance Band)",
    "smithmachinesquat": "Smith Machine Squat",
    "smithmachineoverheadpress": "Smith Machine Overhead Press",
    "smithmachineromaniandeadlift": "Smith Machine Romanian Deadlift",
    "smithmachinebulgariansplitsquat": "Smith Machine Bulgarian Split Squat",
    "powerclean": "Power Clean",
    "barbellsnatch": "Barbell Snatch",
    "cleanandjerk": "Clean and Jerk",
    "muscleuprings": "Muscle-Up (Rings)",
    "handstandpushup": "Handstand Push-Up",
    "burpeestandard": "Burpee (Standard)",
    "toestobar": "Toes-to-Bar",
    "wallballshot": "Wall Ball Shot",
    "kettlebellthruster": "Kettlebell Thruster",
    "overheadsquat": "Overhead Squat",
    "archbodyholdsuperman": "Arch-Body Hold (Superman)",
    "chesttobarpullup": "Chest-to-Bar Pull-Up",
    "ringdip": "Ring Dip",
    "frontrackreverselunge": "Front-Rack Reverse Lunge",
    "boxstepover": "Box Step-Over",
    "americankettlebellswing": "American Kettlebell Swing",
    "bearcrawl": "Bear Crawl",
    "dumbbelldevilpress": "Dumbbell Devil Press",
    "tucksit": "Tuck Sit",
    "handstandhold": "Handstand Hold",
    "pseudoplanchehold": "Pseudo-Planche Hold",
    "horsestance": "Horse Stance",
    "duckwalk": "Duck Walk",
    "birddog": "Bird-Dog",
    "pallofpressisoband": "Pallof Press (Iso-Band)",
    "archerpushup": "Archer Push-up",
    "typewriterpushup": "Typewriter Push-up",
    "pseudoplanchepushup": "Pseudo-Planche Push-up",
    "clappushup": "Clap Push-up",
    "singlearmpushup": "Single-Arm Push-up",
    "staggeredhandpushup": "Staggered-Hand Push-up",
    "tigerbendpushup": "Tiger Bend Push-up",
    "benchdipfeetelevated": "Bench Dip (Feet Elevated)",
    "inclinepushup": "Incline Push-up",
    "hindupushup": "Hindu Push-up",
    "dbaroundtheworld": "DB Around the World",
    "aroundtheworldpullupbar": "Around the World (Pull-up Bar)",
    "negativepullup": "Negative Pull-up",
    "commandopullup": "Commando Pull-up",
    "archerpullup": "Archer Pull-up",
    "australianpullupinvertedrow": "Australian Pull-up (Inverted Row)",
    "facepullresistanceband": "Face Pull (Resistance Band)",
    "lsitpullup": "L-Sit Pull-up",
    "explosivepullupchesttobar": "Explosive Pull-up (Chest-to-Bar)",
    "singlearmaustralinanrow": "Single-Arm Australian Row",
    "scapularpullup": "Scapular Pull-up",
    "shrimpsquat": "Shrimp Squat",
    "skaterlunge": "Skater Lunge",
    "curtsylunge": "Curtsy Lunge",
    "sissysquatbodyweight": "Sissy Squat (Bodyweight)",
    "reversenordic": "Reverse Nordic",
    "nordichamstringcurl": "Nordic Hamstring Curl",
    "cossacksquat": "Cossack Squat",
    "singlelegglutebridge": "Single-Leg Glute Bridge",
    "hiphrustcouchbench": "Hip Thrust (Couch/Bench)",
    "calfraisesingleleg": "Calf Raise (Single-Leg)",
    "tibialisraisewall": "Tibialis Raise (Wall)",
    "tuckjump": "Tuck Jump",
    "hollowrock": "Hollow Rock",
    "vup": "V-Up",
    "windshieldwiperfloor": "Windshield Wiper (Floor)",
    "windshieldwiperbar": "Windshield Wiper (Bar)",
    "kneetoelbowbar": "Knee-to-Elbow (Bar)",
    "mountainclimber": "Mountain Climber",
    "spidermanplank": "Spiderman Plank",
    "plankshouldertap": "Plank Shoulder Tap",
    "doubleunderjumprope": "Double Under (Jump Rope)",
    "singleunder": "Single Under",
    "burpee": "Burpee",
    "shadowboxing": "Shadow Boxing",
    "farmerswalkheavybags": "Farmer's Walk (Heavy Bags)",
    "jumpingjacks": "Jumping Jacks"
}

OUTPUT_DIR = "app/src/main/assets/exercise_animations/"
TARGET_SIZE = 400
RAPIDAPI_KEY = os.environ.get("RAPIDAPI_KEY")

def normalize_name(name):
    return re.sub(r'[\s\-()]', '', name.lower())

def fetch_wger_exercises():
    print("Fetching WGER exercises list...")
    url = "https://wger.de/api/v2/exercise/?language=2&limit=100"
    exercises = []
    
    session = requests.Session()
    from requests.adapters import HTTPAdapter
    from urllib3.util.retry import Retry
    retry = Retry(total=3, backoff_factor=1, status_forcelist=[ 500, 502, 503, 504 ])
    adapter = HTTPAdapter(max_retries=retry)
    session.mount('https://', adapter)
    session.mount('http://', adapter)
    
    while url:
        try:
            response = session.get(url, timeout=30)
            response.raise_for_status()
            data = response.json()
            exercises.extend(data.get('results', []))
            url = data.get('next')
        except Exception as e:
            print(f"Error fetching WGER exercises: {e}")
            break
            
    print("Fetching WGER images list...")
    url = "https://wger.de/api/v2/exerciseimage/?is_main=True&limit=100"
    images = {}
    while url:
        try:
            response = session.get(url, timeout=30)
            response.raise_for_status()
            data = response.json()
            for img in data.get('results', []):
                base_id = img.get('exercise_base')
                if base_id is not None:
                    images[str(base_id)] = img.get('image')
            url = data.get('next')
        except Exception as e:
            print(f"Error fetching WGER images: {e}")
            break
            
    wger_dict = {}
    for ex in exercises:
        name = ex.get('name')
        if not name:
            continue
        norm_name = normalize_name(name)
        base_id = ex.get('exercise_base')
        if base_id is not None:
            img_url = images.get(str(base_id))
            if img_url:
                wger_dict[norm_name] = img_url
            
    print(f"Found {len(wger_dict)} exercises with images on WGER.")
    return wger_dict

def fetch_exercisedb(name):
    if not RAPIDAPI_KEY:
        return None
    url = f"https://exercisedb.p.rapidapi.com/exercises/name/{name}"
    headers = {
        "X-RapidAPI-Key": RAPIDAPI_KEY,
        "X-RapidAPI-Host": "exercisedb.p.rapidapi.com"
    }
    try:
        response = requests.get(url, headers=headers, timeout=30)
        if response.status_code == 200:
            data = response.json()
            if isinstance(data, list) and len(data) > 0:
                return data[0].get('gifUrl')
    except Exception as e:
        pass
    return None

def pad_to_square(img, size=TARGET_SIZE):
    if img.mode != 'RGBA':
        img = img.convert('RGBA')
        
    width, height = img.size
    max_dim = max(width, height)
    
    new_img = Image.new('RGBA', (max_dim, max_dim), (0, 0, 0, 255))
    new_img.paste(img, ((max_dim - width) // 2, (max_dim - height) // 2))
    
    return new_img.resize((size, size), Image.Resampling.LANCZOS)

def create_placeholder(display_name, output_path):
    frames = []
    # 2 frames (fade in/out effect)
    for opacity in [150, 255]:
        img = Image.new('RGBA', (TARGET_SIZE, TARGET_SIZE), color=(80, 80, 80, 255))
        draw = ImageDraw.Draw(img)
        
        words = display_name.split()
        lines = []
        current_line = ""
        # rough text wrap
        for word in words:
            if len(current_line + word) * 10 < TARGET_SIZE - 40:
                current_line += word + " "
            else:
                lines.append(current_line.strip())
                current_line = word + " "
        if current_line:
            lines.append(current_line.strip())
        
        y_text = TARGET_SIZE // 2 - (len(lines) * 20 // 2)
        for line in lines:
            w = len(line) * 6 # crude approx for default font width
            draw.text(((TARGET_SIZE - w) // 2, y_text), line, fill=(255, 255, 255, opacity))
            y_text += 20
            
        frames.append(img)
        
    frames[0].save(output_path, format='webp', save_all=True, append_images=[frames[1]], loop=0, duration=1000)

def convert_and_save(img_content, output_path):
    try:
        # Load frames with imageio plugin to support both static and animated (like GIF) formats reliably
        try:
            frames = iio.imread(io.BytesIO(img_content), extension=".gif")
        except:
            try:
                frames = iio.imread(io.BytesIO(img_content))
            except:
                return False, 0
                
        pil_frames = []
        if len(frames.shape) == 2:
            pil_frames.append(Image.fromarray(frames).convert("RGBA"))
        elif len(frames.shape) == 3:
            if frames.shape[2] in (3, 4):
                pil_frames.append(Image.fromarray(frames).convert("RGBA"))
            else:
                for f in frames:
                    pil_frames.append(Image.fromarray(f).convert("RGBA"))
        elif len(frames.shape) == 4:
            for f in frames:
                pil_frames.append(Image.fromarray(f).convert("RGBA"))
        else:
            return False, 0
            
        processed_frames = [pad_to_square(f) for f in pil_frames]
        if not processed_frames:
            return False, 0
            
        quality = 75 # Starting quality specified by the user
        while quality >= 10: 
            byte_io = io.BytesIO()
            if len(processed_frames) > 1:
                # To reach 15-20fps, duration 50-66ms
                processed_frames[0].save(
                    byte_io, 
                    format='webp', 
                    save_all=True, 
                    append_images=processed_frames[1:], 
                    loop=0, 
                    quality=quality,
                    method=4
                )
            else:
                processed_frames[0].save(
                    byte_io, 
                    format='webp', 
                    quality=quality,
                    method=4
                )
                
            file_size_kb = len(byte_io.getvalue()) / 1024.0
            if file_size_kb <= 200:
                with open(output_path, "wb") as f:
                    f.write(byte_io.getvalue())
                return True, int(file_size_kb)
                
            quality -= 5 # step down by 5 if too big
            
        return False, 0
    except Exception as e:
        return False, 0

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    wger_dict = fetch_wger_exercises()
    
    for stem, display_name in EXERCISES.items():
        output_path = os.path.join(OUTPUT_DIR, f"{stem}.webp")
        if os.path.exists(output_path):
            print(f"[SKIP] {stem}.webp already exists.")
            continue
            
        norm_name = normalize_name(display_name)
        img_url = wger_dict.get(norm_name)
        
        # Fallback to a partial WGER match
        if not img_url:
            for wger_name, url in wger_dict.items():
                if norm_name in wger_name or wger_name in norm_name:
                    img_url = url
                    break
                    
        # Fallback to ExerciseDB API if RapidAPI key exists
        if not img_url and RAPIDAPI_KEY:
            img_url = fetch_exercisedb(display_name.lower())
            
        success = False
        if img_url:
            try:
                if img_url.startswith('/'):
                    img_url = "https://wger.de" + img_url
                    
                img_response = requests.get(img_url, timeout=20)
                if img_response.status_code == 200:
                    success, size_kb = convert_and_save(img_response.content, output_path)
                    if success:
                        print(f"[OK] {stem}.webp ({size_kb} KB)")
            except Exception as e:
                pass
                
        if not success:
            create_placeholder(display_name, output_path)
            print(f"[PLACEHOLDER] {stem}.webp")

if __name__ == "__main__":
    main()
