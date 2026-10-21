"""Resolution-independent, theme-aware icons and animated controls."""
import time
from functools import lru_cache
import customtkinter as ctk
from PIL import Image, ImageDraw


def draw_icon(name, color, size=96):
    im = Image.new("RGBA", (96, 96))
    d = ImageDraw.Draw(im)
    if name == "wifi":
        for box in ((6, 16, 90, 100), (22, 34, 74, 86), (36, 51, 60, 75)):
            d.arc(box, 225, 315, fill=color, width=7)
        d.ellipse((44, 70, 52, 78), fill=color)
    elif name in ("eye", "eye_off"):
        d.line([(12, 48), (28, 32), (48, 25), (68, 32), (84, 48),
                (68, 64), (48, 71), (28, 64), (12, 48)], fill=color, width=5, joint="curve")
        d.ellipse((36, 36, 60, 60), outline=color, width=5)
        if name == "eye_off":
            d.line((17, 15, 79, 81), fill=color, width=7)
    elif name == "chevron":
        d.line([(25, 36), (48, 59), (71, 36)], fill=color, width=7, joint="curve")
    elif name == "power":
        d.arc((18, 18, 78, 80), -55, 235, fill=color, width=7)
        d.line((48, 10, 48, 48), fill=color, width=7)
    return im.resize((size, size), Image.Resampling.LANCZOS)


@lru_cache(maxsize=32)
def icon(name, size=22, white=False):
    return ctk.CTkImage(
        light_image=draw_icon(name, "white" if white else "#152238"),
        dark_image=draw_icon(name, "white" if white else "#F3F6FC"),
        size=(size, size))


class AnimatedSwitch(ctk.CTkFrame):
    def __init__(self, master, variable, **kwargs):
        super().__init__(master, width=52, height=30, corner_radius=15,
                         fg_color="#3978F6" if variable.get() else ("#CFD7E3", "#34445C"), **kwargs)
        self.variable = variable
        self.position = float(variable.get())
        self.animation = None
        self.knob = ctk.CTkFrame(self, width=22, height=22, corner_radius=11, fg_color="white")
        self.knob.place(x=4 + 22*self.position, y=4)
        self.bind("<Button-1>", self.toggle)
        self.knob.bind("<Button-1>", self.toggle)
        self.bind("<space>", self.toggle)
        self.configure(cursor="hand2")
        self._canvas.configure(takefocus=1)
        self._canvas.bind("<space>", self.toggle)

    def toggle(self, _event=None):
        if self.animation:
            self.after_cancel(self.animation)
        self.variable.set(not self.variable.get())
        start, target, started = self.position, float(self.variable.get()), time.monotonic()
        self.configure(fg_color="#3978F6" if target else ("#CFD7E3", "#34445C"))

        def tick():
            t = min(1, (time.monotonic() - started) / .18)
            self.position = start + (target-start) * (1-(1-t)**3)
            self.knob.place(x=4+22*self.position, y=4)
            self.animation = self.after(16, tick) if t < 1 else None
        tick()

    def destroy(self):
        if self.animation:
            self.after_cancel(self.animation)
        super().destroy()
