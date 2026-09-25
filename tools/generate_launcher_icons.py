"""Generate adaptive launcher icons and splash drawable from the source PNG."""

from __future__ import annotations

import math
from collections import deque
from pathlib import Path
from xml.sax.saxutils import escape

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
DOCS = ROOT / "docs"
SRC = ROOT / "tools" / "icon-source.png"

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

# Splash artwork uses smooth, authored curves. Pixel tracing preserves the PNG's
# staircase edges and makes them visible when the animated vector is enlarged.
# Keep the original 108-unit viewport, silhouette and two circular eye cutouts.
SPLASH_PATH_DATA = " ".join("""
M50.00,20.75 L58.00,20.75
C58.75,20.75 59.10,21.25 59.30,22.10
L60.15,26.10 Q60.30,26.85 61.05,27.05
Q65.65,28.25 69.35,30.60 Q69.90,30.95 70.50,30.50
L73.65,28.25 Q74.65,27.55 75.50,28.40
L80.60,33.50 Q81.40,34.30 80.75,35.35
L78.65,38.65 Q78.25,39.25 78.65,39.90
Q80.70,43.45 81.65,47.15 Q81.80,47.90 82.55,48.10
L86.45,49.10 Q87.75,49.40 87.75,50.65
L87.75,57.90 Q87.75,59.15 86.50,59.50
L82.45,60.65 Q81.80,60.85 81.60,61.55
Q80.60,65.15 78.65,68.25 Q78.25,68.85 78.65,69.50
L80.70,72.75 Q81.35,73.70 80.55,74.50
L75.55,79.40 Q74.75,80.20 73.70,79.50
L70.35,77.35 Q69.80,77.00 69.20,77.35
Q65.40,79.65 61.10,80.75 Q60.35,80.95 60.15,81.70
L59.15,85.75 Q58.85,87.00 57.55,87.00
L50.20,87.00 Q48.95,87.00 48.65,85.75
L47.65,81.70 Q47.45,80.95 46.70,80.75
Q42.30,79.65 38.65,77.40 Q38.05,77.00 37.45,77.40
L34.10,79.55 Q33.10,80.20 32.25,79.40
L27.20,74.40 Q26.35,73.60 27.00,72.60
L29.10,69.25 Q29.50,68.65 29.10,68.05
Q27.15,64.80 26.20,61.55 Q26.00,60.80 25.25,60.60
L21.55,59.65 Q20.25,59.30 20.25,58.05
L20.25,50.65 Q20.25,49.40 21.55,49.05
L25.25,48.10 Q26.00,47.90 26.20,47.15
Q27.30,43.20 29.20,39.90 Q29.60,39.25 29.20,38.65
L27.05,35.20 Q26.40,34.20 27.25,33.35
L32.35,28.40 Q33.20,27.55 34.20,28.25
L37.45,30.50 Q38.05,30.95 38.65,30.60
Q42.45,28.25 46.85,27.05 Q47.60,26.85 47.80,26.10
L48.80,22.10 Q49.10,20.75 50.00,20.75 Z
M30.75,52.75
C31.65,40.85 41.65,31.50 54.00,31.50
C66.85,31.50 77.25,41.90 77.25,54.75
C77.25,63.05 72.80,70.45 65.25,74.25
C66.45,69.45 65.85,64.95 63.50,60.80
L69.35,57.40 C72.30,55.70 70.15,51.75 67.20,53.45
L61.00,57.05 C57.35,52.40 52.15,49.40 46.25,48.75
L46.25,41.75 C46.25,38.75 41.75,38.75 41.75,41.75
L41.75,48.75 C37.75,49.15 34.10,50.45 30.75,52.75 Z
M38.22,57.68 a2.35,2.35 0 1,0 4.70,0 a2.35,2.35 0 1,0 -4.70,0 Z
M52.82,66.42 a2.37,2.37 0 1,0 4.74,0 a2.37,2.37 0 1,0 -4.74,0 Z
""".split())


def is_near_white(pixel: tuple[int, int, int], threshold: int = 248) -> bool:
    return pixel[0] >= threshold and pixel[1] >= threshold and pixel[2] >= threshold


def crop_white_border(image: Image.Image) -> Image.Image:
    pixels = image.load()
    width, height = image.size
    min_x, min_y, max_x, max_y = width, height, 0, 0
    for y in range(height):
        for x in range(width):
            if not is_near_white(pixels[x, y]):
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
    return image.crop((min_x, min_y, max_x + 1, max_y + 1))


def average_blue(image: Image.Image) -> tuple[int, int, int]:
    pixels = image.load()
    width, height = image.size
    total = [0, 0, 0]
    count = 0
    for y in range(height):
        for x in range(width):
            red, green, blue = pixels[x, y]
            if blue > 180 and red < 80 and 100 < green < 190:
                total[0] += red
                total[1] += green
                total[2] += blue
                count += 1
    return tuple(channel // count for channel in total)  # type: ignore[return-value]


def flood_outside(image: Image.Image) -> set[tuple[int, int]]:
    pixels = image.load()
    width, height = image.size
    outside: set[tuple[int, int]] = set()
    queue: deque[tuple[int, int]] = deque()
    for x in range(width):
        queue.append((x, 0))
        queue.append((x, height - 1))
    for y in range(height):
        queue.append((0, y))
        queue.append((width - 1, y))
    while queue:
        x, y = queue.popleft()
        if (x, y) in outside:
            continue
        if x < 0 or y < 0 or x >= width or y >= height:
            continue
        if not is_near_white(pixels[x, y], threshold=236):
            continue
        outside.add((x, y))
        queue.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    return outside


def extract_foreground(image: Image.Image, brand: tuple[int, int, int]) -> Image.Image:
    width, height = image.size
    outside = flood_outside(image)
    inset = max(8, int(min(width, height) * 0.04))
    pixels = image.load()
    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    dest = out.load()
    brand_r = brand[0]
    for y in range(inset, height - inset):
        for x in range(inset, width - inset):
            if (x, y) in outside:
                continue
            red, green, blue = pixels[x, y]
            mix = (red - brand_r) / max(1, 242 - brand_r)
            mix = min(1.0, max(0.0, mix))
            alpha = int(round(mix * 255))
            if alpha < 20:
                continue
            dest[x, y] = (245, 245, 245, alpha)
    return keep_largest_component(out).filter(ImageFilter.UnsharpMask(radius=1.0, percent=60, threshold=3))


def keep_largest_component(image: Image.Image, alpha_min: int = 24) -> Image.Image:
    width, height = image.size
    alpha = image.split()[-1]
    pix = alpha.load()
    seen = [[False] * width for _ in range(height)]
    best: list[tuple[int, int]] = []
    for start_y in range(height):
        for start_x in range(width):
            if seen[start_y][start_x] or pix[start_x, start_y] < alpha_min:
                continue
            blob: list[tuple[int, int]] = []
            queue = deque([(start_x, start_y)])
            seen[start_y][start_x] = True
            while queue:
                x, y = queue.popleft()
                blob.append((x, y))
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= nx < width and 0 <= ny < height and not seen[ny][nx] and pix[nx, ny] >= alpha_min:
                        seen[ny][nx] = True
                        queue.append((nx, ny))
            if len(blob) > len(best):
                best = blob
    keep = set(best)
    src = image.load()
    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    dest = out.load()
    for x, y in keep:
        dest[x, y] = src[x, y]
    return out


def opaque_bbox(image: Image.Image, alpha_min: int = 24) -> tuple[int, int, int, int]:
    alpha = image.split()[-1]
    return alpha.getbbox() or (0, 0, image.width, image.height)


def place_in_adaptive_canvas(foreground: Image.Image, canvas: int, safe_ratio: float = 68 / 108) -> Image.Image:
    left, top, right, bottom = opaque_bbox(foreground)
    cropped = foreground.crop((left, top, right, bottom))
    safe = int(round(canvas * safe_ratio))
    scale = min(safe / cropped.width, safe / cropped.height)
    new_size = (
        max(1, int(round(cropped.width * scale))),
        max(1, int(round(cropped.height * scale))),
    )
    scaled = cropped.resize(new_size, Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    out.paste(scaled, ((canvas - scaled.width) // 2, (canvas - scaled.height) // 2), scaled)
    return out


def to_black(image: Image.Image) -> Image.Image:
    red, green, blue, alpha = image.split()
    black = Image.new("L", image.size, 0)
    return Image.merge("RGBA", (black, black, black, alpha))


def composite_full(foreground: Image.Image, brand: tuple[int, int, int], size: int) -> Image.Image:
    bg = Image.new("RGBA", (size, size), brand + (255,))
    scaled = foreground.resize((size, size), Image.Resampling.LANCZOS)
    return Image.alpha_composite(bg, scaled)


def rounded_composite(foreground: Image.Image, brand: tuple[int, int, int], size: int, radius: float) -> Image.Image:
    square = composite_full(foreground, brand, size)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    square.putalpha(mask)
    return square


def circle_composite(foreground: Image.Image, brand: tuple[int, int, int], size: int) -> Image.Image:
    square = composite_full(foreground, brand, size)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    square.putalpha(mask)
    return square


def rdp(points: list[tuple[float, float]], epsilon: float) -> list[tuple[float, float]]:
    if len(points) < 3:
        return points
    start, end = points[0], points[-1]
    dx = end[0] - start[0]
    dy = end[1] - start[1]
    denom = math.hypot(dx, dy) or 1.0
    max_dist = -1.0
    index = 0
    for i in range(1, len(points) - 1):
        dist = abs(dy * points[i][0] - dx * points[i][1] + end[0] * start[1] - end[1] * start[0]) / denom
        if dist > max_dist:
            index = i
            max_dist = dist
    if max_dist > epsilon:
        return rdp(points[: index + 1], epsilon)[:-1] + rdp(points[index:], epsilon)
    return [start, end]


DIRECTIONS = (
    (1, 0),
    (1, 1),
    (0, 1),
    (-1, 1),
    (-1, 0),
    (-1, -1),
    (0, -1),
    (1, -1),
)


def in_mask(mask: list[list[int]], x: int, y: int, value: int) -> bool:
    return 0 <= y < len(mask) and 0 <= x < len(mask[0]) and mask[y][x] == value


def moore_trace(mask: list[list[int]], start: tuple[int, int], value: int) -> list[tuple[int, int]]:
    contour = [start]
    incoming = 0
    x, y = start
    for _ in range(len(mask) * len(mask[0])):
        incoming = (incoming + 6) % 8
        found = False
        for step in range(8):
            direction = (incoming + step) % 8
            nx = x + DIRECTIONS[direction][0]
            ny = y + DIRECTIONS[direction][1]
            if in_mask(mask, nx, ny, value):
                x, y = nx, ny
                incoming = direction
                found = True
                break
        if not found:
            break
        if (x, y) == start:
            break
        contour.append((x, y))
    return contour


def component_starts(mask: list[list[int]], value: int) -> list[tuple[int, int]]:
    height = len(mask)
    width = len(mask[0])
    seen = [[False] * width for _ in range(height)]
    starts: list[tuple[int, int]] = []
    for y in range(height):
        for x in range(width):
            if seen[y][x] or mask[y][x] != value:
                continue
            starts.append((x, y))
            queue = deque([(x, y)])
            seen[y][x] = True
            while queue:
                cx, cy = queue.popleft()
                for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                    if in_mask(mask, nx, ny, value) and not seen[ny][nx]:
                        seen[ny][nx] = True
                        queue.append((nx, ny))
    return starts


def find_contours(mask: list[list[int]]) -> list[list[tuple[int, int]]]:
    height = len(mask)
    width = len(mask[0])
    padded = [[0] * (width + 2) for _ in range(height + 2)]
    for y in range(height):
        for x in range(width):
            padded[y + 1][x + 1] = mask[y][x]

    exterior = set()
    queue = deque([(0, 0)])
    exterior.add((0, 0))
    while queue:
        x, y = queue.popleft()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if in_mask(padded, nx, ny, 0) and (nx, ny) not in exterior:
                exterior.add((nx, ny))
                queue.append((nx, ny))

    hole_mask = [[0] * (width + 2) for _ in range(height + 2)]
    for y in range(height + 2):
        for x in range(width + 2):
            if padded[y][x] == 0 and (x, y) not in exterior:
                hole_mask[y][x] = 1

    contours: list[list[tuple[int, int]]] = []
    for start in component_starts(padded, 1)[:1]:
        traced = moore_trace(padded, start, 1)
        if len(traced) > 16:
            contours.append([(x - 1, y - 1) for x, y in traced])
    for start in component_starts(hole_mask, 1)[:8]:
        traced = moore_trace(hole_mask, start, 1)
        if len(traced) > 8:
            contours.append([(x - 1, y - 1) for x, y in traced])
    return contours


def path_from_points(points: list[tuple[float, float]]) -> str:
    if not points:
        return ""
    parts = [f"M{points[0][0]:.2f},{points[0][1]:.2f}"]
    for x, y in points[1:]:
        parts.append(f"L{x:.2f},{y:.2f}")
    parts.append("Z")
    return " ".join(parts)


def circle_path(points: list[tuple[float, float]]) -> str | None:
    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    width = max(xs) - min(xs)
    height = max(ys) - min(ys)
    if width > 5.5 or height > 5.5:
        return None
    cx = sum(xs) / len(points)
    cy = sum(ys) / len(points)
    radius = sum(math.hypot(point[0] - cx, point[1] - cy) for point in points) / len(points)
    if radius < 0.6 or radius > 4.5:
        return None
    return (
        f"M{cx - radius:.2f},{cy:.2f} "
        f"a{radius:.2f},{radius:.2f} 0 1,0 {radius * 2:.2f},0 "
        f"a{radius:.2f},{radius:.2f} 0 1,0 {-radius * 2:.2f},0 Z"
    )


def trace_vector(foreground: Image.Image, viewport: int = 108) -> str:
    work = place_in_adaptive_canvas(foreground, 432)
    alpha = work.split()[-1].point(lambda value: 255 if value > 88 else 0)
    width, height = alpha.size
    pix = alpha.load()
    mask = [[1 if pix[x, y] else 0 for x in range(width)] for y in range(height)]
    contours = find_contours(mask)
    scale = viewport / width
    paths = []
    for contour in contours:
        scaled = [(x * scale, y * scale) for x, y in contour]
        circle = circle_path(scaled)
        if circle:
            paths.append(circle)
            continue
        simplified = rdp(scaled, epsilon=0.16)
        if len(simplified) >= 6:
            paths.append(path_from_points(simplified))
    return " ".join(paths)


def write_png(path: Path, image: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG")


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def resolve_source() -> Path:
    if SRC.exists():
        return SRC
    raise FileNotFoundError(f"icon source not found: {SRC}")


def main() -> None:
    source = Image.open(resolve_source()).convert("RGB")
    cropped = crop_white_border(source)
    brand = average_blue(cropped)
    brand_hex = f"#{brand[0]:02X}{brand[1]:02X}{brand[2]:02X}"
    print("brand", brand, brand_hex, "cropped", cropped.size)

    raw_fg = extract_foreground(cropped, brand)

    for name, scale in DENSITIES.items():
        size = int(round(108 * scale))
        fg = place_in_adaptive_canvas(raw_fg, size)
        write_png(RES / f"drawable-{name}" / "ic_launcher_foreground.png", fg)
        mipmap = int(round(48 * scale))
        write_png(RES / f"mipmap-{name}" / "ic_launcher.png", composite_full(fg, brand, mipmap))
        write_png(RES / f"mipmap-{name}" / "ic_launcher_round.png", circle_composite(fg, brand, mipmap))

    write_png(RES / "drawable-nodpi" / "ic_launcher_monochrome.png", to_black(place_in_adaptive_canvas(raw_fg, 432)))
    write_png(DOCS / "logo.png", rounded_composite(place_in_adaptive_canvas(raw_fg, 512), brand, 512, 114))

    write_text(
        RES / "values" / "ic_launcher_background.xml",
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<resources>\n"
        f'    <color name="ic_launcher_background">{brand_hex}</color>\n'
        "</resources>\n",
    )

    adaptive = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <!-- Desktop artwork: 84% of its original size, centered within the background. -->
    <foreground>
        <inset
            android:drawable="@drawable/ic_launcher_foreground"
            android:insetLeft="8%"
            android:insetTop="8%"
            android:insetRight="8%"
            android:insetBottom="8%" />
    </foreground>
    <monochrome>
        <inset
            android:drawable="@drawable/ic_launcher_monochrome"
            android:insetLeft="8%"
            android:insetTop="8%"
            android:insetRight="8%"
            android:insetBottom="8%" />
    </monochrome>
</adaptive-icon>
"""
    write_text(RES / "mipmap-anydpi-v26" / "ic_launcher.xml", adaptive)
    write_text(RES / "mipmap-anydpi-v26" / "ic_launcher_round.xml", adaptive)

    path_data = SPLASH_PATH_DATA
    print("vector path length", len(path_data))
    splash_vector = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="288dp"
    android:height="288dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:name="icon"
        android:pivotX="54"
        android:pivotY="54"
        android:scaleX="1"
        android:scaleY="1">
        <path
            android:fillColor="#F5F5F5"
            android:fillType="evenOdd"
            android:pathData="{escape(path_data)}" />
    </group>
</vector>
'''
    write_text(RES / "drawable" / "splash_icon_vector.xml", splash_vector)

    splash_avd = '''<?xml version="1.0" encoding="utf-8"?>
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:drawable="@drawable/splash_icon_vector">
    <target android:name="icon">
        <aapt:attr name="android:animation">
            <set android:ordering="sequentially">
                <set android:ordering="together">
                    <objectAnimator
                        android:duration="280"
                        android:propertyName="scaleX"
                        android:valueFrom="0.72"
                        android:valueTo="1.0"
                        android:valueType="floatType">
                        <aapt:attr name="android:interpolator">
                            <pathInterpolator android:pathData="M 0,0 C 0.2,0 0.1,1 1,1" />
                        </aapt:attr>
                    </objectAnimator>
                    <objectAnimator
                        android:duration="280"
                        android:propertyName="scaleY"
                        android:valueFrom="0.72"
                        android:valueTo="1.0"
                        android:valueType="floatType">
                        <aapt:attr name="android:interpolator">
                            <pathInterpolator android:pathData="M 0,0 C 0.2,0 0.1,1 1,1" />
                        </aapt:attr>
                    </objectAnimator>
                </set>
                <set android:ordering="together">
                    <objectAnimator
                        android:duration="220"
                        android:propertyName="scaleX"
                        android:valueFrom="1.0"
                        android:valueTo="0.94"
                        android:valueType="floatType">
                        <aapt:attr name="android:interpolator">
                            <pathInterpolator android:pathData="M 0,0 C 0.33,0 0.67,1 1,1" />
                        </aapt:attr>
                    </objectAnimator>
                    <objectAnimator
                        android:duration="220"
                        android:propertyName="scaleY"
                        android:valueFrom="1.0"
                        android:valueTo="0.94"
                        android:valueType="floatType">
                        <aapt:attr name="android:interpolator">
                            <pathInterpolator android:pathData="M 0,0 C 0.33,0 0.67,1 1,1" />
                        </aapt:attr>
                    </objectAnimator>
                </set>
            </set>
        </aapt:attr>
    </target>
</animated-vector>
'''
    write_text(RES / "drawable" / "splash_icon.xml", splash_avd)

    theme = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Starting" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowSplashScreenBackground">@color/ic_launcher_background</item>
        <item name="android:windowSplashScreenAnimatedIcon">@drawable/splash_icon</item>
        <item name="android:windowSplashScreenAnimationDuration">500</item>
    </style>
</resources>
'''
    night = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Starting" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowSplashScreenBackground">@color/ic_launcher_background</item>
        <item name="android:windowSplashScreenAnimatedIcon">@drawable/splash_icon</item>
        <item name="android:windowSplashScreenAnimationDuration">500</item>
    </style>
</resources>
'''
    write_text(RES / "values" / "app_splash.xml", theme)
    write_text(RES / "values-night" / "app_splash.xml", night)
    print("done")


if __name__ == "__main__":
    main()
