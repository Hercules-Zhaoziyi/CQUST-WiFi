"""Generate the application icon from our vector primitives (no user image edits)."""
from PIL import Image, ImageDraw
from widgets import draw_icon
from pathlib import Path

if __name__ == "__main__":
    image = Image.new("RGBA", (256, 256))
    ImageDraw.Draw(image).rounded_rectangle((0, 0, 255, 255), radius=64, fill="#3978F6")
    image.alpha_composite(draw_icon("wifi", "white", 218), (19, 19))
    (Path(__file__).parent / "assets").mkdir(exist_ok=True)
    image.save(Path(__file__).parent / "assets" / "wifi.ico",
               sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
