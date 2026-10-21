"""Resolution-independent, theme-aware icons and animated controls."""
import time
import math
import tkinter as tk
from functools import lru_cache
import customtkinter as ctk
from PIL import Image, ImageDraw


def wifi_brand(size=256):
    """Render the matching SVG geometry at output resolution, never upscale a bitmap."""
    scale = size * 4 / 96
    image = Image.new('RGBA', (size * 4, size * 4))
    d = ImageDraw.Draw(image)
    d.rounded_rectangle((0, 0, size*4-1, size*4-1), radius=20*scale, fill='#2563EB')
    for points in (((25,40),(38,27),(58,27),(71,40)),
                   ((32,49),(41,40),(55,40),(64,49)),
                   ((40,59),(45,54),(51,54),(56,59))):
        curve = []
        for i in range(65):
            t = i/64
            curve.append(tuple(scale*((1-t)**3*points[0][j]+3*(1-t)**2*t*points[1][j]+3*(1-t)*t*t*points[2][j]+t**3*points[3][j]) for j in (0,1)))
        d.line(curve, fill='white', width=round(4.5*scale))
        for x,y in (curve[0], curve[-1]):
            r=2.25*scale
            d.ellipse((x-r,y-r,x+r,y+r), fill='white')
    d.ellipse((45.6*scale,66.6*scale,50.4*scale,71.4*scale), fill='white')
    return image.resize((size,size), Image.Resampling.LANCZOS)


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
    elif name == 'sun':
        d.ellipse((30, 30, 66, 66), outline=color, width=5)
        for i in range(8):
            a = i*math.pi/4
            d.line((48+29*math.cos(a), 48+29*math.sin(a), 48+39*math.cos(a), 48+39*math.sin(a)), fill=color, width=5)
    elif name == 'moon':
        points = []
        for p0,p1,p2,p3 in [((61,13),(10,1),(0,71),(49,83)), ((49,83),(67,86),(82,73),(85,56)), ((85,56),(45,72),(24,37),(61,13))]:
            for i in range(25):
                t = i/24
                points.append(tuple((1-t)**3*p0[j]+3*(1-t)**2*t*p1[j]+3*(1-t)*t*t*p2[j]+t**3*p3[j] for j in (0,1)))
        d.line(points, fill=color, width=5, joint='curve')
    elif name == 'system':
        d.rounded_rectangle((14, 20, 82, 67), radius=7, outline=color, width=5)
        d.line((48, 67, 48, 80), fill=color, width=5)
        d.line((33, 80, 63, 80), fill=color, width=5)
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


class PulsePower(ctk.CTkFrame):
    """Fixed square canvas: icon/font geometry cannot stretch the circular control."""
    def __init__(self, master, command, size=244, connected=False):
        super().__init__(master, width=size, height=size, fg_color='transparent')
        self.pack_propagate(False)
        self.command, self.connected, self.busy = command, connected, False
        self.hover = False
        self.phase = 0.0
        self.animation = None
        self.canvas = tk.Canvas(self, highlightthickness=0, bd=0, takefocus=1, cursor='hand2')
        self.canvas.place(x=0, y=0, relwidth=1, relheight=1)
        self.canvas.bind('<Configure>', lambda e: self._paint())
        self.canvas.bind('<Button-1>', self._click)
        self.canvas.bind('<space>', lambda e: self.command() if not self.busy else None)
        self.canvas.bind('<Enter>', lambda e: self._hover(True))
        self.canvas.bind('<Leave>', lambda e: self._hover(False))
        self._tick()

    def _hover(self, value):
        self.hover = value
        self._paint()

    def _click(self, event):
        self.canvas.focus_set()
        side = min(self.canvas.winfo_width(), self.canvas.winfo_height())
        if not self.busy and math.hypot(event.x-side/2, event.y-side/2) <= side*.32:
            self.command()

    def set_status(self, connected):
        self.connected = connected
        self._paint()

    def set_busy(self, busy):
        self.busy = busy
        self._paint()

    def _set_appearance_mode(self, mode):
        super()._set_appearance_mode(mode)
        if hasattr(self, 'canvas'):
            self._paint()

    def _paint(self):
        if not hasattr(self, 'canvas'):
            return
        side = min(self.canvas.winfo_width(), self.canvas.winfo_height())
        mid, radius = side/2, side*(.323 if self.hover else .315)
        bg = self._apply_appearance_mode(('#FFFFFF', '#111C30'))
        color = '#3978F6' if self.busy else ('#20B58B' if self.connected else '#71819B')
        self.canvas.configure(bg=bg)
        self.canvas.delete('all')
        self.circle_bounds = (mid-radius, mid-radius, mid+radius, mid+radius)
        if self.connected or self.busy:
            for offset in (0, .5):
                p = (self.phase+offset) % 1
                rr = side*(.34+.15*p)
                fg = tuple(int(color[i:i+2], 16) for i in (1,3,5))
                back = tuple(int(bg[i:i+2], 16) for i in (1,3,5))
                tint = '#'+''.join(f'{round(a*(1-p)*.32+b*(1-(1-p)*.32)):02x}' for a,b in zip(fg,back))
                self.canvas.create_oval(mid-rr, mid-rr, mid+rr, mid+rr, outline=tint, width=max(1, side*.007))
        self.canvas.create_oval(*self.circle_bounds, fill=color, outline='')
        r = radius*.35
        self.canvas.create_arc(mid-r, mid-r, mid+r, mid+r, start=135, extent=270,
                               style='arc', outline='white', width=max(2, side*.017))
        self.canvas.create_line(mid, mid-r*1.3, mid, mid-r*.05, fill='white', width=max(2, side*.017), capstyle='round')

    def _tick(self):
        self.phase = (time.monotonic() % 2.4)/2.4
        if self.winfo_ismapped():
            self._paint()
        self.animation = self.after(40, self._tick)

    def destroy(self):
        if self.animation:
            self.after_cancel(self.animation)
        super().destroy()
