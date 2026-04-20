import json
import difflib

with open("/tmp/hasaneyldrm_db/data/exercises.json") as f:
    db = json.load(f)
    
db_names = []
for ex in db:
    name = ex.get("name", "")
    import re
    norm_name = re.sub(r'[\s\-()]', '', name).lower()
    db_names.append(norm_name)

target = "abductormachine"
match = difflib.get_close_matches(target, db_names, n=3, cutoff=0.3)
print(f"Target: {target}, matches: {match}")

target = "adductormachine"
match = difflib.get_close_matches(target, db_names, n=3, cutoff=0.3)
print(f"Target: {target}, matches: {match}")

target = "bandbicepcurl"
match = difflib.get_close_matches(target, db_names, n=3, cutoff=0.3)
print(f"Target: {target}, matches: {match}")

