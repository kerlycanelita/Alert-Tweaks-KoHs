"""The mod's icons: a pixel-art heart sweating in front of a Nether portal while PvP gear attacks it.

python tools/gen_icon.py
  -> src/main/resources/assets/alert_tweaks/icon.png   static, 256x256 (Mod Menu, in game)
  -> docs/media/icon-animated.gif                      animated, 128x128, seamless loop (Modrinth)
  -> docs/media/icon-animated-frames.png               every frame on one sheet, to review

The portal and the gear are Minecraft's own textures, read from the game jar Loom keeps in the
Gradle cache. Everything is drawn at 256 and the GIF is scaled down from it. A GIF has no half
transparency, so only the rounded corners are see-through.
"""
import glob
import io
import math
import os
import zipfile
from functools import lru_cache

from PIL import Image, ImageDraw, ImageFilter

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ICON = os.path.join(BASE, "src", "main", "resources", "assets", "alert_tweaks", "icon.png")
MEDIA = os.path.join(BASE, "docs", "media")

SIZE = 256
FRAMES = 40
MILLIS = 50
COLORS = 112
CELL = 8
CORNER = 46
CX, CY = 128, 132

FRAME_OUTER = (0x5B, 0x02, 0xAD)
FRAME_INNER = (0x74, 0x12, 0xC2)
OUTLINE = (0x2D, 0x05, 0x72)
HEART = {
    "L": (0xF0, 0x7A, 0xFF),
    "M": (0xD9, 0x3C, 0xFC),
    "B": (0xB8, 0x10, 0xF5),
    "D": (0x8A, 0x06, 0xD8),
    "W": (0xFF, 0xF0, 0xFF),
}
# The heart, cell by cell: highlight top left, shade bottom right.
HEART_ART = [
    ".LLL...LLL.",
    "LWWMM.LMMMB",
    "LWMMMMMMMBB",
    "LMMMMMMMBBB",
    "MMMMMMMBBBD",
    ".MMMMMBBBD.",
    "..MMBBBBD..",
    "...BBBDD...",
    "....BDD....",
    ".....D.....",
]
HEART_SCALE = 1.06

# The gear round the heart, clockwise from the warning sign: (item, angle clockwise from up, aims a point at it).
GEAR = [("netherite_axe", 102, True), ("totem_of_undying", 150, False), ("end_crystal", 197, False),
        ("totem_of_undying", 245, False), ("mace", 293, True), ("netherite_sword", 345, True)]
GEAR_RADIUS = 90
GEAR_SIZE = 46
SIGN_ANGLE = 45
SIGN_RADIUS = 88
# Black metal round each piece instead of a white sticker rim, and a purple glow so dark netherite still reads.
METAL = (14, 4, 28)
AURA = (190, 70, 255)

DROP_ART = [
    "...O...",
    "..OWO..",
    ".OWLLO.",
    ".OLLLBO",
    "OLLLLBO",
    "OLLLBBO",
    ".OBBBO.",
    "..OOO..",
]
DROP_COLORS = {"O": (18, 38, 104), "W": (255, 255, 255), "L": (160, 230, 255), "B": (70, 150, 236)}
SPARK_ART = [
    "...R...",
    "...R...",
    "..RPR..",
    "RRPWPRR",
    "..RPR..",
    "...R...",
    "...R...",
]
SPARK_COLORS = {"R": (232, 40, 78), "P": (255, 120, 170), "W": (255, 226, 240)}
AMBER = (255, 198, 52)
AMBER_LIGHT = (255, 236, 150)
INK = (40, 14, 6)

# One attack, in parts of the loop: pull back, strike, stay on the heart, fall back to rest.
WIND, STRIKE, HOLD, BACK = 0.05, 0.05, 0.025, 0.2
HIT = WIND + STRIKE
# Sweat in the GIF: (part of the loop it forms, start offset from the heart's centre, speed per loop, cell size).
SWEAT = [(0.02, (12, -44), (20, -160), 4), (0.52, (-32, -40), (-60, -170), 4)]
SWEAT_LIFE = 0.42
GRAVITY = 700


@lru_cache(maxsize=None)
def jar_texture(path):
    """A texture from the newest Minecraft jar in the Gradle cache."""
    jars = sorted(glob.glob(os.path.expanduser(
        "~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/*/minecraft-clientonly-deobf-*.jar")))
    jars = [j for j in jars if not j.endswith("sources.jar")]
    with zipfile.ZipFile(jars[-1]) as jar:
        return Image.open(io.BytesIO(jar.read(path))).convert("RGBA")


def portal_strip():
    """Minecraft's nether_portal.png (16 x 16*n), one image per frame."""
    strip = jar_texture("assets/minecraft/textures/block/nether_portal.png")
    return [strip.crop((0, i * 16, 16, i * 16 + 16)) for i in range(strip.height // 16)]


def portal_frame(frames, t, scroll, size=SIZE, blend=True):
    """The portal at loop time t (0..1), texels kept sharp at the output size, drifting by one tile per loop."""
    n = len(frames)
    f = t * n
    a = frames[int(f) % n]
    if blend:
        b = frames[(int(f) + 1) % n]
        a = Image.blend(a, b, f - int(f))
    texel = size // 32
    tile = a.resize((16 * texel, 16 * texel), Image.NEAREST)
    # The game draws the portal see-through; in an icon it must be solid or it washes out.
    tile.putalpha(255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    ox = int(round(scroll * tile.width)) % tile.width
    oy = int(round(scroll * tile.height)) % tile.height
    for y in range(-tile.height, size + tile.height, tile.height):
        for x in range(-tile.width, size + tile.width, tile.width):
            out.paste(tile, (x - ox, y - oy))
    # Darker overall, and darker still behind the heart, so the subject reads at icon size.
    out = Image.blend(Image.new("RGBA", (size, size), (8, 0, 20, 255)), out, 0.86)
    shade = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(shade)
    c = size / 2
    steps = 24
    for k in range(steps, 0, -1):
        r = size * 0.44 * k / steps
        draw.ellipse((c - r, c - r, c + r, c + r), fill=int(185 * (1 - k / steps) ** 0.7))
    dark = Image.new("RGBA", (size, size), (6, 0, 16, 255))
    return Image.composite(dark, out, shade)


def heart_image():
    """The heart at cell size, with a dark outline one cell wide around it."""
    w, h = len(HEART_ART[0]), len(HEART_ART)
    img = Image.new("RGBA", ((w + 2) * CELL, (h + 2) * CELL), (0, 0, 0, 0))
    px = img.load()
    filled = lambda x, y: 0 <= y < h and 0 <= x < w and HEART_ART[y][x] != "."
    for y in range(-1, h + 1):
        for x in range(-1, w + 1):
            if filled(x, y):
                color = HEART[HEART_ART[y][x]]
            elif any(filled(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                color = OUTLINE
            else:
                continue
            for yy in range(CELL):
                for xx in range(CELL):
                    px[(x + 1) * CELL + xx, (y + 1) * CELL + yy] = color + (255,)
    return img


def beat(t):
    """Heart scale through one beat (0..1): a strong 'lub' and a softer 'dub', then rest."""
    def bump(center, width, height):
        d = (t - center) / width
        return height * math.exp(-d * d)
    return 1.0 + bump(0.08, 0.05, 0.16) + bump(0.28, 0.05, 0.09)


def glow(radius, color, strength):
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.ellipse((CX - radius, CY - radius, CX + radius, CY + radius), fill=color + (int(255 * strength),))
    return layer.filter(ImageFilter.GaussianBlur(radius * 0.45))


def frame_mask():
    mask = Image.new("L", (SIZE * 4, SIZE * 4), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, SIZE * 4 - 1, SIZE * 4 - 1), radius=CORNER * 4, fill=255)
    return mask.resize((SIZE, SIZE), Image.LANCZOS)


def frame_border():
    layer = Image.new("RGBA", (SIZE * 4, SIZE * 4), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle((0, 0, SIZE * 4 - 1, SIZE * 4 - 1), radius=CORNER * 4, outline=FRAME_OUTER + (255,), width=6 * 4)
    draw.rounded_rectangle((6 * 4, 6 * 4, SIZE * 4 - 1 - 6 * 4, SIZE * 4 - 1 - 6 * 4), radius=(CORNER - 6) * 4,
                           outline=FRAME_INNER + (255,), width=3 * 4)
    return layer.resize((SIZE, SIZE), Image.LANCZOS)


def finish(base, front, size):
    """Front layer over the portal, then the rounded corners cut out."""
    if front.size != (size, size):
        front = front.resize((size, size), Image.LANCZOS)
    out = base.copy()
    out.alpha_composite(front)
    mask = frame_mask().resize((size, size), Image.LANCZOS)
    out.putalpha(Image.composite(out.getchannel("A"), Image.new("L", out.size, 0), mask))
    return out


def pixel_art(art, colors, cell):
    img = Image.new("RGBA", (len(art[0]) * cell, len(art) * cell), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    for y, row in enumerate(art):
        for x, key in enumerate(row):
            if key in colors:
                draw.rectangle((x * cell, y * cell, x * cell + cell - 1, y * cell + cell - 1), fill=colors[key] + (255,))
    return img


def alert_sign(cell=4):
    """A warning triangle in pixel art: amber, dark rim, an exclamation mark, light on its left edge."""
    w, h = 19, 17
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).polygon([(9, 1), (17.6, 15.5), (0.4, 15.5)], fill=255)
    m = mask.load()
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    inside = lambda x, y: 0 <= x < w and 0 <= y < h and m[x, y] > 0
    for y in range(h):
        for x in range(w):
            if inside(x, y):
                left_edge = not inside(x - 1, y) or not inside(x - 1, y - 1)
                px[x, y] = (AMBER_LIGHT if left_edge else AMBER) + (255,)
            elif any(inside(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                px[x, y] = INK + (255,)
    for y in range(6, 12):
        px[9, y] = INK + (255,)
    px[9, 13] = INK + (255,)
    return img.resize((w * cell, h * cell), Image.NEAREST)


def outlined(sprite):
    """A black-metal outline round a sprite and a soft purple glow behind it."""
    body = sprite.getchannel("A").point(lambda v: 255 if v > 90 else 0)
    out = Image.new("RGBA", sprite.size, AURA + (0,))
    out.putalpha(body.filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.GaussianBlur(5)).point(lambda v: int(v * 0.7)))
    edge = Image.new("RGBA", sprite.size, METAL + (0,))
    edge.putalpha(body.filter(ImageFilter.MaxFilter(5)))
    out.alpha_composite(edge)
    out.alpha_composite(sprite)
    return out


def item_sprite(name, size, rotate_cw):
    """A Minecraft item texture blown up in pixel art, turned (clockwise degrees) and outlined."""
    tex = jar_texture("assets/minecraft/textures/item/" + name + ".png")
    pad = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    pad.alpha_composite(tex, (8, 8))
    big = pad.resize((32 * 16, 32 * 16), Image.NEAREST).rotate(-rotate_cw, resample=Image.BICUBIC)
    return outlined(big.resize((32 * size // 16, 32 * size // 16), Image.LANCZOS))


def place(layer, sprite, cx, cy, alpha=1.0):
    if alpha < 1.0:
        sprite = sprite.copy()
        sprite.putalpha(sprite.getchannel("A").point(lambda v: int(v * alpha)))
    layer.alpha_composite(sprite, (int(round(cx - sprite.width / 2)), int(round(cy - sprite.height / 2))))


def on_circle(angle, radius):
    rad = math.radians(angle)
    return CX + math.sin(rad) * radius, CY - math.cos(rad) * radius


def gear_turn(angle, weapon):
    """Item textures point up and to the right: a weapon turns to aim at the heart, the rest lean in towards it."""
    return angle + 135 if weapon else -math.sin(math.radians(angle)) * 20


def reach(sprite, angle):
    """How far the solid part of a sprite pokes out of its centre towards the heart."""
    rad = math.radians(angle)
    ux, uy = -math.sin(rad), math.cos(rad)
    a = sprite.getchannel("A").load()
    w, h = sprite.size
    return max((x - w / 2) * ux + (y - h / 2) * uy for y in range(h) for x in range(w) if a[x, y] > 200)


def heart_edge(heart, angle):
    """Distance from the heart's centre to the outside of its outline, at angle (clockwise from up)."""
    a = heart.getchannel("A").load()
    w, h = heart.size
    rad = math.radians(angle)
    d = 0.0
    while True:
        x = w / 2 + math.sin(rad) * d / HEART_SCALE
        y = h / 2 - math.cos(rad) * d / HEART_SCALE
        if not (0 <= x < w and 0 <= y < h) or a[int(x), int(y)] == 0:
            return d
        d += 1.0


def draw_heart(img, heart, scale, dx=0.0, dy=0.0):
    hw, hh = heart.size
    scaled = heart.resize((int(hw * scale), int(hh * scale)), Image.NEAREST)
    img.alpha_composite(scaled, (int(round(CX + dx - scaled.width / 2)), int(round(CY + dy - scaled.height / 2))))


def draw_sign(img, scale, halo_alpha):
    sx, sy = on_circle(SIGN_ANGLE, SIGN_RADIUS)
    halo = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse((sx - 34, sy - 30, sx + 34, sy + 38), fill=(255, 190, 60, int(255 * halo_alpha)))
    img.alpha_composite(halo.filter(ImageFilter.GaussianBlur(14)))
    sign = alert_sign(3)
    if scale != 1.0:
        sign = sign.resize((int(sign.width * scale), int(sign.height * scale)), Image.LANCZOS)
    place(img, sign, sx, sy + 4)


def streaks(img, x, y, angle):
    """Short violet streaks behind a piece of gear, fading away from it: it is closing in."""
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    rad = math.radians(angle)
    ox, oy = math.sin(rad), -math.cos(rad)
    for k, off in enumerate((-9, 0, 9)):
        px, py = math.cos(rad) * off, math.sin(rad) * off
        start = 30 + k % 2 * 4
        for j in range(7):
            a0, a1 = start + j * 2.4, start + (j + 1) * 2.4
            d.line((x + ox * a0 + px, y + oy * a0 + py, x + ox * a1 + px, y + oy * a1 + py),
                   fill=(214, 150, 255, int(190 * (1 - j / 7))), width=3 if j < 3 else 2)
    img.alpha_composite(layer)


def attack(u, depth):
    """One attack, u = part of the loop since it began: (distance moved towards the heart, swing in degrees)."""
    if u < WIND:
        k = u / WIND
        return -6 * math.sin(k * math.pi / 2), -14 * k
    u -= WIND
    if u < STRIKE:
        k = u / STRIKE
        return -6 + (depth + 6) * k * k, -14 + 24 * k
    u -= STRIKE
    if u < HOLD:
        return depth, 10.0
    u -= HOLD
    if u < BACK:
        e = 1 - (1 - u / BACK) ** 3
        return depth * (1 - e), 10 * (1 - e)
    return 0.0, 0.0


def gear_list(heart):
    """Each piece with where it rests, how far it must lunge to hit the heart, and when in the loop it attacks."""
    gear = []
    for k, (name, angle, weapon) in enumerate(GEAR):
        turn = gear_turn(angle, weapon)
        edge = heart_edge(heart, angle)
        depth = GEAR_RADIUS - reach(item_sprite(name, GEAR_SIZE, turn), angle) - edge + 4
        phase = round(k * FRAMES / len(GEAR)) / FRAMES
        gear.append({"name": name, "angle": angle, "weapon": weapon, "turn": turn, "edge": edge,
                     "depth": max(depth, 8.0), "phase": phase})
    return gear


def static_overlay(heart, gear):
    """The heart sweating in the middle, PvP gear closing in on it from all sides, a warning sign up and to the right."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    img.alpha_composite(glow(86, (225, 70, 255), 0.55))
    for g in gear:
        x, y = on_circle(g["angle"], GEAR_RADIUS)
        streaks(img, x, y, g["angle"])
        place(img, item_sprite(g["name"], GEAR_SIZE, g["turn"]), x, y)
    draw_sign(img, 1.0, 150 / 255)
    draw_heart(img, heart, HEART_SCALE)
    # Sweat flying off the heart's upper left, between the sword and the mace, where the GIF throws it.
    place(img, pixel_art(DROP_ART, DROP_COLORS, 4), CX - 44, CY - 50)
    place(img, pixel_art(DROP_ART, DROP_COLORS, 3), CX - 62, CY - 68)
    img.alpha_composite(frame_border())
    return img


def gif_overlay(heart, gear, t):
    """The GIF at loop time t (0..1): the heart beats twice a loop and sweats while the gear strikes it, clockwise."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    s = beat((t * 2.0) % 1.0)
    pulse = (s - 1.0) / 0.16
    img.alpha_composite(glow(int(66 + 26 * pulse), (225, 70, 255), 0.30 + 0.45 * pulse))
    draw_sign(img, 1.0 + 0.12 * pulse, 0.45 + 0.4 * pulse)
    # Sweat forms on the heart, flies off and falls; drawn under the heart so it seems to come out of it.
    for start, (x0, y0), (vx, vy), cell in SWEAT:
        age = (t - start) % 1.0
        if age > SWEAT_LIFE:
            continue
        drop = pixel_art(DROP_ART, DROP_COLORS, cell)
        grow = min(1.0, 0.45 + age / 0.08)
        if grow < 1.0:
            drop = drop.resize((max(1, int(drop.width * grow)), max(1, int(drop.height * grow))), Image.LANCZOS)
        fade = 1.0 if age < SWEAT_LIFE * 0.6 else 1.0 - (age - SWEAT_LIFE * 0.6) / (SWEAT_LIFE * 0.4)
        place(img, drop, CX + x0 + vx * age, CY + y0 + vy * age + 0.5 * GRAVITY * age * age, fade)
    # Each hit knocks the heart a little away from the piece that landed it.
    poses, dx, dy = [], 0.0, 0.0
    for g in gear:
        u = (t - g["phase"]) % 1.0
        move, swing = attack(u, g["depth"])
        hit = 1.0 - (u - HIT) / 0.1 if HIT <= u < HIT + 0.1 else 0.0
        rad = math.radians(g["angle"])
        dx -= math.sin(rad) * 4 * hit
        dy += math.cos(rad) * 4 * hit
        poses.append((g, u, move, swing, hit))
    draw_heart(img, heart, s * HEART_SCALE / 1.06, dx, dy)
    for g, u, move, swing, hit in poses:
        scale = 1.0 if g["weapon"] else 0.6
        if WIND <= u < HIT + HOLD:
            # A faint copy one frame behind while it strikes, so the lunge reads at 20 frames a second.
            prev_move, prev_swing = attack(u - 1.0 / FRAMES, g["depth"])
            x, y = on_circle(g["angle"], GEAR_RADIUS - prev_move)
            place(img, item_sprite(g["name"], GEAR_SIZE, g["turn"] + prev_swing * scale), x, y, 0.35)
        x, y = on_circle(g["angle"], GEAR_RADIUS - move)
        place(img, item_sprite(g["name"], GEAR_SIZE, g["turn"] + swing * scale), x, y)
    for g, u, move, swing, hit in poses:
        if hit > 0.0:
            spark = pixel_art(SPARK_ART, SPARK_COLORS, 4)
            size = int(spark.width * (0.6 + 0.6 * hit))
            x, y = on_circle(g["angle"], g["edge"] - 4)
            place(img, spark.resize((size, size), Image.NEAREST), x + dx, y + dy, min(1.0, hit * 1.6))
    img.alpha_composite(frame_border())
    return img


def main():
    portal_frames = portal_strip()
    heart = heart_image()
    gear = gear_list(heart)

    # Static: an exact portal frame, the heart under attack and sweating, the warning sign up and to the right.
    static = finish(portal_frame(portal_frames, 0.25, 0.0, SIZE, blend=False), static_overlay(heart, gear), SIZE)
    static.save(ICON)

    os.makedirs(MEDIA, exist_ok=True)
    frames = []
    for i in range(FRAMES):
        t = i / FRAMES
        # The portal moves on every other frame (the game animates it at 10 frames a second).
        pt = (i - i % 2) / FRAMES
        frames.append(finish(portal_frame(portal_frames, pt, pt, 128), gif_overlay(heart, gear, t), 128))

    # One palette for every frame, index 255 kept for see-through. After the first frame, pixels that did not
    # change are written as see-through over the previous frame (disposal 1), which the GIF packs tightly.
    strip = Image.new("RGB", (128, 128 * FRAMES))
    for i, f in enumerate(frames):
        strip.paste(f.convert("RGB"), (0, 128 * i))
    # Sweat, sparks and the sign are too small to win colours of their own from the portal's purples: keep theirs.
    fixed = list(DROP_COLORS.values()) + list(SPARK_COLORS.values()) + [AMBER, AMBER_LIGHT, INK]
    found = strip.quantize(colors=COLORS - len(fixed), method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    palette = Image.new("P", (1, 1))
    palette.putpalette(found.getpalette()[:3 * (COLORS - len(fixed))] + [c for rgb in fixed for c in rgb])
    drawn = []
    previous = None
    for f in frames:
        mapped = f.convert("RGB").quantize(palette=palette, dither=Image.Dither.NONE)
        cut = f.getchannel("A").point(lambda v: 255 if v < 128 else 0)
        mapped.paste(255, mask=cut)
        if previous is not None:
            same = Image.frombytes("L", mapped.size, bytes(
                255 if a == b else 0 for a, b in zip(mapped.tobytes(), previous.tobytes())))
            delta = mapped.copy()
            delta.paste(255, mask=same)
            drawn.append(delta)
        else:
            drawn.append(mapped)
        previous = mapped
    gif = os.path.join(MEDIA, "icon-animated.gif")
    drawn[0].save(gif, save_all=True, append_images=drawn[1:], duration=MILLIS, loop=0, transparency=255, disposal=1, optimize=False)
    sheet = Image.new("RGBA", (128 * 8, 128 * (FRAMES // 8)))
    for i, f in enumerate(frames):
        sheet.paste(f, (128 * (i % 8), 128 * (i // 8)))
    sheet.save(os.path.join(MEDIA, "icon-animated-frames.png"))
    print(f"icon.png {os.path.getsize(ICON) // 1024} KiB, icon-animated.gif {os.path.getsize(gif) // 1024} KiB, {FRAMES} frames")
    print("depths", [(g["name"], round(g["depth"]), round(g["edge"])) for g in gear])


if __name__ == "__main__":
    main()
