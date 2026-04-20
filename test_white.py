import os
from PIL import Image

DIR = "app/src/main/assets/exercise_animations/"

files_to_check = ["barbellbacksquat.webp", "barbellcurl.webp", "barbellflatbenchpress.webp", "bandtriceppushdown.webp", "pullup.webp", "pushups.webp", "burpee.webp"]

def get_white_percentage(filepath):
    try:
        with Image.open(filepath) as img:
            img = img.convert("RGB")
            w, h = img.size
            pixels = img.getdata()
            white_count = sum(1 for p in pixels if p[0] > 245 and p[1] > 245 and p[2] > 245)
            return white_count / (w * h)
    except Exception as e:
        return 0

for filename in files_to_check:
    path = os.path.join(DIR, filename)
    if os.path.exists(path):
        pct = get_white_percentage(path)
        print(f"{filename}: {pct:.4f}")
