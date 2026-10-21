"""Generate the application icon from our vector primitives (no user image edits)."""
from PIL import Image, ImageDraw
from widgets import wifi_brand
from pathlib import Path

if __name__ == "__main__":
    image = wifi_brand(256)
    (Path(__file__).parent / "assets").mkdir(exist_ok=True)
    image.save(Path(__file__).parent / "assets" / "wifi.ico",
               sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
