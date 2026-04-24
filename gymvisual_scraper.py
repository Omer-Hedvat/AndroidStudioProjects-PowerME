#!/usr/bin/env python3
"""
Scrapes all exercise names from gymvisual.com male animated GIFs catalogue,
cross-references against PowerME master exercise list, and writes a markdown report.
Research-only — no GIFs downloaded.
"""

import json
import re
import time
import os
import requests
from bs4 import BeautifulSoup
import jellyfish

BASE_URL = "https://gymvisual.com/16-animated-gifs/s-1/gender-male/media_type-animated_gifs?n=80&p={page}"
CACHE_FILE = "gymvisual_names_cache.json"
MASTER_EXERCISES_JSON = "app/src/main/res/raw/master_exercises.json"
ANIMATION_DIR = "app/src/main/assets/exercise_animations"
REPORT_OUT = "bugs_to_fix/assets/gymvisual_male_report.md"
JARO_WINKLER_THRESHOLD = 0.85

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
    "Accept-Language": "en-US,en;q=0.9",
}


def to_search_name(name: str) -> str:
    """Mirror Exercise.kt toSearchName() — lowercase + strip [\\s\\-()'/]"""
    return re.sub(r"[\s\-()'/]", "", name.lower())


def strip_variants(name: str) -> str:
    """Remove common gymvisual suffixes that indicate variants, not distinct exercises."""
    # Remove trailing parentheticals like (male), (female), (version 2), (side pov), etc.
    name = re.sub(r"\s*\(male\)\s*$", "", name, flags=re.IGNORECASE).strip()
    name = re.sub(r"\s*\(female\)\s*$", "", name, flags=re.IGNORECASE).strip()
    name = re.sub(r"\s*\(version\s+\d+\)\s*$", "", name, flags=re.IGNORECASE).strip()
    name = re.sub(r"\s*\(v\d+\)\s*$", "", name, flags=re.IGNORECASE).strip()
    return name


def scrape_page(page: int) -> list[str]:
    url = BASE_URL.format(page=page)
    try:
        resp = requests.get(url, headers=HEADERS, timeout=15)
        resp.raise_for_status()
    except requests.RequestException as e:
        print(f"  ERROR fetching page {page}: {e}")
        return []

    soup = BeautifulSoup(resp.text, "html.parser")
    # Each product has an <a class="product_img_link" title="Exercise Name (male)">
    names = []
    for a in soup.select("a.product_img_link"):
        title = a.get("title", "").strip()
        if title:
            names.append(title)
    return names


def load_cache() -> list[str] | None:
    if os.path.exists(CACHE_FILE):
        with open(CACHE_FILE) as f:
            data = json.load(f)
        print(f"Loaded {len(data)} names from cache.")
        return data
    return None


def save_cache(names: list[str]):
    with open(CACHE_FILE, "w") as f:
        json.dump(names, f, indent=2)
    print(f"Saved {len(names)} names to cache.")


def scrape_all() -> list[str]:
    cached = load_cache()
    if cached is not None:
        return cached

    all_names = []
    # Probe first page to detect total pages from pagination
    print("Probing page 1 ...")
    resp = requests.get(BASE_URL.format(page=1), headers=HEADERS, timeout=15)
    soup = BeautifulSoup(resp.text, "html.parser")

    # Extract total pages from pagination (?p= format)
    total_pages = 56  # fallback: 4442 items / 80 per page = 56 pages
    page_links = soup.select(".pagination a[href]")
    page_nums = []
    for a in page_links:
        href = a.get("href", "")
        m = re.search(r"[?&]p=(\d+)", href)
        if m:
            page_nums.append(int(m.group(1)))
    if page_nums:
        total_pages = max(page_nums)
        print(f"  Detected {total_pages} total pages")

    print(f"Scraping {total_pages} pages (80 items/page) ...")

    # Parse page 1 we already fetched
    page1_names = []
    for a in soup.select("a.product_img_link"):
        title = a.get("title", "").strip()
        if title:
            page1_names.append(title)

    all_names.extend(page1_names)
    print(f"  Page 1: {len(page1_names)} names (total so far: {len(all_names)})")

    for page in range(2, total_pages + 1):
        names = scrape_page(page)
        all_names.extend(names)
        if page % 10 == 0:
            print(f"  Page {page}/{total_pages}: {len(names)} names (total so far: {len(all_names)})")
        time.sleep(0.3)  # polite delay

    print(f"Scraped {len(all_names)} raw names across {total_pages} pages.")
    save_cache(all_names)
    return all_names


def load_db_exercises() -> list[dict]:
    with open(MASTER_EXERCISES_JSON) as f:
        data = json.load(f)
    exercises = data.get("exercises", [])
    print(f"Loaded {len(exercises)} exercises from master_exercises.json")
    return exercises


def has_webp(search_name: str) -> bool:
    path = os.path.join(ANIMATION_DIR, f"{search_name}.webp")
    return os.path.exists(path)


def jaro_winkler(a: str, b: str) -> float:
    return jellyfish.jaro_winkler_similarity(a, b)


def match_gymvisual_to_db(gymvisual_name: str, db_exercises: list[dict]) -> tuple[dict | None, str, float]:
    """Returns (matched_exercise or None, match_type, confidence)"""
    normalized = to_search_name(gymvisual_name)

    # 1. Exact match on searchName
    for ex in db_exercises:
        if to_search_name(ex["name"]) == normalized:
            return ex, "EXACT", 1.0

    # 2. Fuzzy Jaro-Winkler >= threshold
    best_score = 0.0
    best_ex = None
    for ex in db_exercises:
        score = jaro_winkler(normalized, to_search_name(ex["name"]))
        if score > best_score:
            best_score = score
            best_ex = ex

    if best_score >= JARO_WINKLER_THRESHOLD:
        return best_ex, f"FUZZY({best_score:.2f})", best_score

    return None, "UNMATCHED", 0.0


def main():
    print("=== PowerME × gymvisual.com catalogue comparison ===\n")

    gymvisual_raw = scrape_all()
    if not gymvisual_raw:
        print("ERROR: No names scraped. Check network access and HTML selectors.")
        return

    # Deduplicate: strip variants and collect unique canonical names
    canonical_set: dict[str, list[str]] = {}  # canonical → list of raw names
    for raw in gymvisual_raw:
        canon = strip_variants(raw)
        if canon not in canonical_set:
            canonical_set[canon] = []
        canonical_set[canon].append(raw)

    print(f"\n{len(gymvisual_raw)} raw names → {len(canonical_set)} canonical names after dedup\n")

    db_exercises = load_db_exercises()

    # Match each canonical gymvisual name against our DB
    matched = []     # (gymvisual_canon, db_exercise, match_type, confidence)
    unmatched = []   # gymvisual_canon names with no DB counterpart

    for canon, raw_variants in canonical_set.items():
        ex, match_type, confidence = match_gymvisual_to_db(canon, db_exercises)
        if ex:
            matched.append((canon, raw_variants, ex, match_type, confidence))
        else:
            unmatched.append((canon, raw_variants))

    # Our DB exercises: which ones matched at all on gymvisual?
    matched_db_ids = {ex["name"] for (_, _, ex, _, _) in matched}
    db_with_gymvisual = [ex for ex in db_exercises if ex["name"] in matched_db_ids]
    db_without_gymvisual = [ex for ex in db_exercises if ex["name"] not in matched_db_ids]

    print(f"\n=== SUMMARY ===")
    print(f"Our DB:          {len(db_exercises)} exercises")
    print(f"Gymvisual canon: {len(canonical_set)} unique exercises (from {len(gymvisual_raw)} raw)")
    print(f"DB exercises with a gymvisual match: {len(db_with_gymvisual)}")
    print(f"DB exercises with NO gymvisual match: {len(db_without_gymvisual)}")
    print(f"Gymvisual exercises NOT in our DB:   {len(unmatched)}")

    # Write report
    os.makedirs(os.path.dirname(REPORT_OUT), exist_ok=True)
    with open(REPORT_OUT, "w") as f:
        f.write("# gymvisual.com × PowerME Exercise Comparison Report\n\n")
        f.write(f"**Source:** https://gymvisual.com/16-animated-gifs/s-1/gender-male/media_type-animated_gifs  \n")
        f.write(f"**Date:** 2026-04-24  \n")
        f.write(f"**Filter:** gender=male, media_type=animated_gifs  \n")
        f.write(f"**Gymvisual raw names scraped:** {len(gymvisual_raw)}  \n")
        f.write(f"**Gymvisual canonical (deduped):** {len(canonical_set)}  \n")
        f.write(f"**PowerME DB exercises:** {len(db_exercises)}  \n\n")
        f.write("> ⚠️ **Commercial content.** No GIFs were downloaded. "
                "gymvisual.com sells animated GIFs (~$0.75–$0.90 each). "
                "See https://gymvisual.com/content/3-terms-and-conditions-of-use before any future use.\n\n")
        f.write("---\n\n")

        # Section A: Our DB exercises and their webp status
        f.write("## Section A — Our 240 DB exercises: webp status + gymvisual match\n\n")
        f.write("| Exercise | Muscle Group | Equipment | searchName | Has WebP | On Gymvisual? |\n")
        f.write("|---|---|---|---|---|---|\n")
        for ex in sorted(db_exercises, key=lambda x: (x["muscleGroup"], x["name"])):
            sn = to_search_name(ex["name"])
            webp = "✅" if has_webp(sn) else "❌"
            on_gym = "✅" if ex["name"] in matched_db_ids else "❌"
            f.write(f"| {ex['name']} | {ex['muscleGroup']} | {ex['equipmentType']} | `{sn}` | {webp} | {on_gym} |\n")

        f.write("\n---\n\n")

        # Section B: Gymvisual exercises matched to our DB
        f.write(f"## Section B — Gymvisual exercises matched to our DB ({len(matched)} matches)\n\n")
        f.write("| Gymvisual canonical name | Raw variants | Matched DB exercise | Match type |\n")
        f.write("|---|---|---|---|\n")
        for canon, raw_variants, ex, match_type, confidence in sorted(matched, key=lambda x: x[0]):
            variants_str = "; ".join(raw_variants[:3]) + ("…" if len(raw_variants) > 3 else "")
            f.write(f"| {canon} | {variants_str} | {ex['name']} | {match_type} |\n")

        f.write("\n---\n\n")

        # Section C: Gymvisual exercises NOT in our DB
        f.write(f"## Section C — Gymvisual exercises NOT in our DB ({len(unmatched)} candidates)\n\n")
        f.write("These could be added to `master_exercises.json` (requires muscleGroup/equipmentType/joints curation + seeded_version bump).  \n\n")
        f.write("| Gymvisual canonical name | Sample raw variants |\n")
        f.write("|---|---|\n")
        for canon, raw_variants in sorted(unmatched, key=lambda x: x[0]):
            variants_str = "; ".join(raw_variants[:2]) + ("…" if len(raw_variants) > 2 else "")
            f.write(f"| {canon} | {variants_str} |\n")

        f.write("\n---\n\n")

        # Section D: Our exercises with NO gymvisual equivalent
        f.write(f"## Section D — Our DB exercises with NO gymvisual match ({len(db_without_gymvisual)} exercises)\n\n")
        f.write("| Exercise | Muscle Group | Equipment | Has WebP |\n")
        f.write("|---|---|---|---|\n")
        for ex in sorted(db_without_gymvisual, key=lambda x: (x["muscleGroup"], x["name"])):
            sn = to_search_name(ex["name"])
            webp = "✅" if has_webp(sn) else "❌"
            f.write(f"| {ex['name']} | {ex['muscleGroup']} | {ex['equipmentType']} | {webp} |\n")

    print(f"\nReport written to: {REPORT_OUT}")


if __name__ == "__main__":
    main()
