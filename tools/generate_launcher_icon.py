import os
from pathlib import Path

from PIL import Image

REPO_ROOT = Path(__file__).resolve().parents[1]

# Default: keep the source logo under docs so it's easy to find.
SRC = REPO_ROOT / "docs" / "assets" / "app_logo.png"
OUT_ROOT = REPO_ROOT / "app" / "src" / "main" / "res"

SIZES = {
    "drawable-mdpi": 108,
    "drawable-hdpi": 162,
    "drawable-xhdpi": 216,
    "drawable-xxhdpi": 324,
    "drawable-xxxhdpi": 432,
}

# Scale the source down inside a transparent square canvas to avoid being clipped
# by round/adaptive masks.
SCALE = 0.78


def main() -> None:
    if not SRC.exists():
        raise SystemExit(
            f"Source image not found: {SRC}\n"
            "Expected a 1024x1024 PNG at docs/assets/app_logo.png"
        )

    im = Image.open(SRC).convert("RGBA")

    for folder, n in SIZES.items():
        out_dir = OUT_ROOT / folder
        out_dir.mkdir(parents=True, exist_ok=True)

        canvas = Image.new("RGBA", (n, n), (0, 0, 0, 0))

        target = int(n * SCALE)
        resized = im.resize((target, target), Image.Resampling.LANCZOS)
        x = (n - target) // 2
        y = (n - target) // 2
        canvas.alpha_composite(resized, (x, y))

        out_path = out_dir / "ic_launcher_foreground.png"
        canvas.save(out_path, format="PNG", optimize=True)
        print(f"wrote {out_path}")

    print("done")


if __name__ == "__main__":
    main()
