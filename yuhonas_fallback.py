import os
import difflib
from PIL import Image
from io import BytesIO

DB_DIR = "/tmp/ex_db_test/exercises"
TARGET_DIR = "/Users/omerhedvat/git/AndroidStudioProjects-PowerME/app/src/main/assets/exercise_animations"
MISSING_FILE = os.path.join(TARGET_DIR, "missing_animations.txt")
TARGET_SIZE = 400

if not os.path.exists(MISSING_FILE):
    print("No missing file found.")
    exit()

with open(MISSING_FILE, "r") as f:
    missing = [line.strip() for line in f if line.strip()]

# Build candidate mapping
all_dirs = [d for d in os.listdir(DB_DIR) if os.path.isdir(os.path.join(DB_DIR, d))]
candidate_keys = []
key_to_dir = {}

import re
def format_name(s):
    return re.sub(r'[\s\-()]', '', s).lower()

for d in all_dirs:
    # d is like 'Alternate_Incline_Dumbbell_Curl'
    norm = format_name(d.replace("_", ""))
    candidate_keys.append(norm)
    key_to_dir[norm] = d

still_missing = []
found_count = 0

for sn in missing:
    out_path = os.path.join(TARGET_DIR, f"{sn}.webp")
    match_dir = None
    
    if sn in key_to_dir:
        match_dir = key_to_dir[sn]
    else:
        match = difflib.get_close_matches(sn, candidate_keys, n=1, cutoff=0.7)
        if match:
            match_dir = key_to_dir[match[0]]
            
    if match_dir:
        dir_path = os.path.join(DB_DIR, match_dir, "images")
        
        # In yuhonas/free-exercise-db some images are directly in the dir or images folder, wait:
        # Before I ran `ls /tmp/ex_db_test/exercises/Ab_Roller` and found `0.jpg 1.jpg` directly inside.
        if not os.path.exists(dir_path):
            dir_path = os.path.join(DB_DIR, match_dir)
            
        img0 = os.path.join(dir_path, "0.jpg")
        img1 = os.path.join(dir_path, "1.jpg")
        
        if os.path.exists(img0) and os.path.exists(img1):
            try:
                i0 = Image.open(img0).convert("RGBA")
                i1 = Image.open(img1).convert("RGBA")
                
                # frame 1
                max_dim0 = max(i0.width, i0.height)
                sq0 = Image.new("RGBA", (max_dim0, max_dim0), (0,0,0,255))
                sq0.paste(i0, ((max_dim0 - i0.width)//2, (max_dim0 - i0.height)//2))
                sq0 = sq0.resize((TARGET_SIZE, TARGET_SIZE), Image.Resampling.LANCZOS)
                
                # frame 2
                max_dim1 = max(i1.width, i1.height)
                sq1 = Image.new("RGBA", (max_dim1, max_dim1), (0,0,0,255))
                sq1.paste(i1, ((max_dim1 - i1.width)//2, (max_dim1 - i1.height)//2))
                sq1 = sq1.resize((TARGET_SIZE, TARGET_SIZE), Image.Resampling.LANCZOS)
                
                # Loop: 10 frames of sq0, 10 frames of sq1 to mimic 10fps holding 1 sec each
                frames_seq = [sq0]*10 + [sq1]*10
                
                b = BytesIO()
                frames_seq[0].save(
                    b,
                    format="webp",
                    save_all=True,
                    append_images=frames_seq[1:],
                    loop=0,
                    duration=100, # 100ms per frame -> 10fps
                    quality=75,
                    method=4
                )
                
                with open(out_path, "wb") as f:
                    f.write(b.getvalue())
                    
                print(f"Processed from yuhonas: {sn} using {match_dir}")
                found_count += 1
            except Exception as e:
                print(f"Error processing {sn}: {e}")
                still_missing.append(sn)
        else:
            still_missing.append(sn)
    else:
        still_missing.append(sn)

with open(MISSING_FILE, "w") as f:
    for m in still_missing:
        f.write(m + "\n")
        
print(f"Recovered {found_count} animations from yuhonas DB. Still missing: {len(still_missing)}")
