"""Check real Compose root renders; no emulator/system-bar capture is implied.

Run after rendering MaintenanceRootScreenshotTestKt. Color probes verify the
screen keeps side/bottom safe areas and starts directly below the notice,
while the actual notice's gold title clears explicit top/cutout insets.
"""
from pathlib import Path
import sys
from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[2]
RENDERS = ROOT / "app/build/outputs/screenshotTest-results/preview/debug/dev/rendered/com/hienthai/fastowin/screenshot/MaintenanceRootScreenshotTestKt"


def bounds(image, color):
    bands = ImageChops.difference(image, Image.new("RGB", image.size, color)).point(
        lambda value: 255 if value == 0 else 0
    ).split()
    return ImageChops.multiply(ImageChops.multiply(bands[0], bands[1]), bands[2]).getbbox()


def verify(path):
    image = Image.open(path).convert("RGB")
    portrait = "portrait" in path.name
    width_dp, left, top, right, bottom = (390, 12, 32, 12, 24) if portrait else (844, 40, 24, 20, 16)
    scale = image.width / width_dp
    gold = bounds(image, (255, 201, 40))
    green = bounds(image, (0, 255, 0))
    cyan = bounds(image, (0, 255, 255))
    screen = bounds(image, (255, 0, 255))
    assert gold and green and cyan and screen, "notice/probe colors missing"
    assert gold[0] >= round((left + 16) * scale) - 1, f"title under side cutout: x={gold[0]}"
    assert gold[1] >= round((top + 4) * scale) - 1, f"title under status bar: y={gold[1]}"
    top_area = image.crop((0, 0, image.width, round(top * scale)))
    assert top_area.getcolors(top_area.width * top_area.height) == [(top_area.width * top_area.height, (13, 40, 93))], "notice background does not cover top edge"
    assert abs(green[1] - screen[1]) <= 1, f"duplicate top padding below banner: {green[1] - screen[1]}px"
    assert abs(green[0] - round(left * scale)) <= 1, "screen lost left safe area"
    assert abs(green[2] - (image.width - round(right * scale))) <= 1, "screen lost right safe area"
    assert abs(cyan[3] - (image.height - round(bottom * scale))) <= 1, "screen lost bottom safe area"
    # Magenta side gutter begins at the banner boundary. Above it, the notice
    # background reaches both edges even though its text stays within cutouts.
    assert image.getpixel((0, screen[1] - 1)) == (13, 40, 93)
    assert image.getpixel((image.width - 1, screen[1] - 1)) == (13, 40, 93)
    print(f"PASS {path.name}: title={gold}, screen={screen}, content={green}, bottom={cyan}")


paths = sorted(RENDERS.glob("maintenanceRootInsetProbe_*.png"))
assert len(paths) == 2, f"Expected portrait and landscape renders, found {len(paths)}"
failures = []
for path in paths:
    try:
        verify(path)
    except AssertionError as error:
        failures.append(f"FAIL {path.name}: {error}")
if failures:
    print("\n".join(failures), file=sys.stderr)
    sys.exit(1)
