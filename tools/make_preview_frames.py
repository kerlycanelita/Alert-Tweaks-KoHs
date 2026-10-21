"""Turns the PreviewRecorder shots into the looping preview frames the mod ships.

Usage: python tools/make_preview_frames.py <shots dir> <frames out dir> [width height colors]

The recorder takes FRAMES + OVERLAP shots; the last OVERLAP are crossfaded into the first ones so
the loop has no seam. Frames are downscaled (the 1280x720 shots supersample them) and stored as
palette PNGs to keep the jar small.
"""
import sys
from pathlib import Path

from PIL import Image

FRAMES = 48
OVERLAP = 8


def main() -> None:
    shots = Path(sys.argv[1])
    out = Path(sys.argv[2])
    width = int(sys.argv[3]) if len(sys.argv) > 3 else 512
    height = int(sys.argv[4]) if len(sys.argv) > 4 else 288
    colors = int(sys.argv[5]) if len(sys.argv) > 5 else 256
    raw = [Image.open(shots / f"rec_{k:03d}.png").convert("RGB") for k in range(FRAMES + OVERLAP)]
    frames = raw[:FRAMES]
    for i in range(OVERLAP):
        t = (i + 0.5) / OVERLAP
        frames[i] = Image.blend(raw[FRAMES + i], raw[i], t)
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("frame_*.png"):
        old.unlink()
    total = 0
    for i, frame in enumerate(frames):
        small = frame.resize((width, height), Image.LANCZOS)
        small = small.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)
        path = out / f"frame_{i:02d}.png"
        small.save(path, optimize=True)
        total += path.stat().st_size
    print(f"{len(frames)} frames {width}x{height}, {total / 1024:.0f} KiB")


if __name__ == "__main__":
    main()
