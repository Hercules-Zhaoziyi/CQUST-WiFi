"""Generate the application icon from our vector primitives (no user image edits)."""
from PIL import Image, ImageDraw
from widgets import draw_icon
from pathlib import Path

if __name__ == "__main__":
    # Use the user's supplied bitmap unchanged except ICO size conversion.
    image = Image.open(Path(__file__).parent / 'assets' / 'wifi_reference.jpg').convert('RGBA')
    image = image.resize((256, 256), Image.Resampling.LANCZOS)
    (Path(__file__).parent / "assets").mkdir(exist_ok=True)
    image.save(Path(__file__).parent / "assets" / "wifi.ico",
               sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
