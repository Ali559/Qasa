#!/usr/bin/env python3
"""Writes the favicon test images for docs/site-icons.md "Shape" (step 7). Each app's unit
tests decode these, run steps 6-7 and check the kind (and edge colour) listed under
"shapes" in docs/site-icons-vectors.json. Run once; the PNGs are committed."""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent

def img(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))

def save(name, im):
    im.save(HERE / f"{name}.png")

# bleed: solid to the edge of its circle
im = img(64, 64); d = ImageDraw.Draw(im); d.rectangle([0, 0, 63, 63], fill="#C8102E"); d.rectangle([24, 14, 40, 50], fill="#FFFFFF"); save("square-solid", im)
im = img(128, 128); d = ImageDraw.Draw(im); d.rounded_rectangle([0, 0, 127, 127], radius=28, fill="#3550DC"); d.polygon([(36, 80), (64, 36), (92, 80)], fill="#FFFFFF"); save("rounded-square", im)
im = img(48, 48); d = ImageDraw.Draw(im); d.ellipse([0, 0, 47, 47], fill="#12805C"); save("circle", im)
im = img(32, 32); d = ImageDraw.Draw(im); d.rectangle([0, 0, 31, 31], fill="#F5F5F5"); d.rectangle([7, 7, 24, 24], fill="#FF6A00"); save("square-light-background", im)
# edge: solid, not square (or small inside its canvas), one colour round its border
im = img(128, 56); d = ImageDraw.Draw(im); d.rectangle([0, 0, 127, 55], fill="#111111"); d.rectangle([20, 18, 108, 38], fill="#FFD400"); save("wide-solid", im)
im = img(32, 64); d = ImageDraw.Draw(im); d.rectangle([0, 0, 31, 63], fill="#2F9E6C"); d.rectangle([10, 20, 21, 43], fill="#FFFFFF"); save("tall-solid", im)
im = img(32, 32); d = ImageDraw.Draw(im); d.rectangle([6, 6, 25, 25], fill="#2F67D0"); save("small-square-padded", im)
# glyph: see-through around its shape, or a border of many colours
im = img(64, 64); d = ImageDraw.Draw(im); d.polygon([(32, 4), (54, 32), (32, 60), (10, 32)], fill="#1E8F6C"); save("glyph-diamond", im)
im = img(64, 64); d = ImageDraw.Draw(im); d.rectangle([4, 26, 59, 37], fill="#722F99"); d.rectangle([26, 4, 37, 59], fill="#722F99"); save("glyph-plus", im)
im = img(128, 64)
for x in range(128):
    for y in range(64):
        im.putpixel((x, y), (x * 2, 255 - x * 2, 120, 255))
save("wide-gradient", im)
print("wrote", len(list(HERE.glob("*.png"))), "images")
