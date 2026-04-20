import os
import re
import requests
import difflib
from PIL import Image, ImageSequence
from io import BytesIO

TARGET_DIR = "/Users/omerhedvat/git/AndroidStudioProjects-PowerME/app/src/main/assets/exercise_animations"
MISSING_FILE = os.path.join(TARGET_DIR, "missing_animations.txt")
TARGET_SIZE = 400

if not os.path.exists(MISSING_FILE):
    print("No missing animations file found.")
    exit()

with open(MISSING_FILE, "r") as f:
    missing_names = [line.strip() for line in f if line.strip()]

if not missing_names:
    print("All exercises already processed.")
    exit()

def normalize_name(name):
    return re.sub(r'[\s\-()]', '', name.lower())

def fetch_wger_exercises():
    print("Fetching WGER exercises list...")
    url = "https://wger.de/api/v2/exercise/?language=2&limit=200"
    exercises = []
    
    session = requests.Session()
    from requests.adapters import HTTPAdapter
    from urllib3.util.retry import Retry
    retry = Retry(total=5, backoff_factor=1, status_forcelist=[ 500, 502, 503, 504 ])
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
    url = "https://wger.de/api/v2/exerciseimage/?is_main=True&limit=200"
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

def process_gif(img_content, output_path):
    try:
        img = Image.open(BytesIO(img_content))
        frames = []
        is_animated = getattr(img, "is_animated", False)
        
        if is_animated:
            for frame in ImageSequence.Iterator(img):
                f = frame.copy().convert("RGBA")
                max_dim = max(f.width, f.height)
                sq = Image.new("RGBA", (max_dim, max_dim), (0,0,0,255))
                sq.paste(f, ((max_dim - f.width)//2, (max_dim - f.height)//2))
                sq = sq.resize((TARGET_SIZE, TARGET_SIZE), Image.Resampling.LANCZOS)
                frames.append(sq)
        else:
            f = img.convert("RGBA")
            max_dim = max(f.width, f.height)
            sq = Image.new("RGBA", (max_dim, max_dim), (0,0,0,255))
            sq.paste(f, ((max_dim - f.width)//2, (max_dim - f.height)//2))
            sq = sq.resize((TARGET_SIZE, TARGET_SIZE), Image.Resampling.LANCZOS)
            frames.append(sq)
            
        if not frames:
            return False
            
        duration = img.info.get("duration", 100) or 100
        duration = max(min(duration, 100), 83)
        limit_kb = 200
        quality = 75
        
        while quality >= 10:
            b = BytesIO()
            if len(frames) > 1:
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
            else:
                frames[0].save(
                    b,
                    format="webp",
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
        print(f"Error processing image: {e}")
        return False

wger_dict = fetch_wger_exercises()
candidate_keys = list(wger_dict.keys())

still_missing = []
found_count = 0

for sn in missing_names:
    out_path = os.path.join(TARGET_DIR, f"{sn}.webp")
    
    img_url = wger_dict.get(sn)
    if not img_url:
        match = difflib.get_close_matches(sn, candidate_keys, n=1, cutoff=0.7) # try slightly looser matching for wger
        if match:
            img_url = wger_dict[match[0]]
            
    if img_url:
        if img_url.startswith('/'):
            img_url = "https://wger.de" + img_url
            
        try:
            response = requests.get(img_url, timeout=20)
            if response.status_code == 200:
                success = process_gif(response.content, out_path)
                if success:
                    found_count += 1
                    print(f"Processed from WGER: {sn}")
                else:
                    still_missing.append(sn)
            else:
                still_missing.append(sn)
        except Exception as e:
            still_missing.append(sn)
    else:
        still_missing.append(sn)

with open(MISSING_FILE, "w") as f:
    for m in still_missing:
        f.write(m + "\n")

print(f"WGER Fallback recovered {found_count} animations. Still missing: {len(still_missing)}")
