from PIL import Image, ImageDraw

BG = (15, 22, 33, 255)      # #0F1621
CELL = (43, 52, 66, 255)    # #2B3442
ACCENT = (76, 155, 232, 255)# #4C9BE8
GRASS = (87, 201, 106, 255) # #57C96A

def make(size=1024):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = int(size * 0.22)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=BG)

    grid = int(size * 0.62)
    origin = (size - grid) // 2
    cell = int(grid / 4.5)
    gap = int(cell * 0.4)
    total = cell * 4 + gap * 3
    origin = (size - total) // 2

    for row in range(4):
        for col in range(4):
            x = origin + col * (cell + gap)
            y = origin + row * (cell + gap)
            center = row in (1, 2) and col in (1, 2)
            d.rounded_rectangle([x, y, x + cell, y + cell], radius=int(cell * 0.18),
                                fill=ACCENT if center else CELL)

    line_y = size // 2
    w = int(size * 0.052)
    d.rounded_rectangle([origin - int(size * 0.055), line_y - w // 2,
                         origin + total + int(size * 0.055), line_y + w // 2],
                        radius=w // 2, fill=GRASS)
    return img

base = make(1024)
sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
base.save(r"G:\terramap\windows\TerraXaleid.App\Assets\app.ico",
          format="ICO", sizes=[(s, s) for s in sizes])
base.resize((256, 256), Image.LANCZOS).save(r"G:\terramap\windows\TerraXaleid.App\Assets\app.png")
print("ok")
