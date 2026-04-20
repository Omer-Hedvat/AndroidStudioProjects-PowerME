import os
from PIL import Image, ImageSequence
from io import BytesIO

TARGET_DIR = "/Users/omerhedvat/git/AndroidStudioProjects-PowerME/app/src/main/assets/exercise_animations"
DB_DIR = "/tmp/hasaneyldrm_db"

targets = [
    {
        "out_name": "barbellbacksquat.webp",
        "gif_path": "videos/0043-qXTaZnJ.gif"
    },
    {
        "out_name": "barbellcurl.webp",
        "gif_path": "videos/0031-25GPyDY.gif"
    },
    {
        "out_name": "barbbellflatbenchpress.webp",
        "gif_path": "videos/0025-EIeI8Vf.gif"
    }
]

TARGET_WIDTH = 400

for t in targets:
    input_path = os.path.join(DB_DIR, t["gif_path"])
    output_path = os.path.join(TARGET_DIR, t["out_name"])
    
    img = Image.open(input_path)
    
    frames = []
    
    for frame in ImageSequence.Iterator(img):
        f = frame.copy().convert("RGBA")
        
        # Scale width to 400, proportional height
        ratio = TARGET_WIDTH / float(f.width)
        new_height = int(f.height * ratio)
        sq = f.resize((TARGET_WIDTH, new_height), Image.Resampling.LANCZOS)
        
        # Original frame duration
        dur = frame.info.get('duration', 100) or 100
        
        # Unroll logic: Target 10 frames per second (100ms each)
        # If a frame's duration is 1000ms, we duplicate it 10 times physically
        repeat_count = int(round(dur / 100.0))
        repeat_count = max(1, repeat_count)
        
        for _ in range(repeat_count):
            frames.append(sq)
            
    print(f"Unrolled {len(frames)} total frames for {t['out_name']}")
    
    quality = 90
    limit_kb = 200
    
    while quality >= 10:
        b = BytesIO()
        frames[0].save(
            b,
            format="webp",
            save_all=True,
            append_images=frames[1:],
            loop=0,
            duration=100, # Explicitly clamp output metadata strictly to 10 FPS
            quality=quality,
            method=4
        )
        
        v = b.getvalue()
        if len(v) / 1024.0 < limit_kb:
            with open(output_path, "wb") as file:
                file.write(v)
            print(f"Successfully processed {t['out_name']} ({len(v)//1024} KB)")
            break
        quality -= 5
