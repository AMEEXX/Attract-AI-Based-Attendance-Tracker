import os
import math
from PIL import Image, ImageDraw, ImageFilter

def make_gradient(width, height, start_color, end_color):
    """Creates a diagonal linear gradient PIL image."""
    base = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    for y in range(height):
        for x in range(width):
            t = (x / max(1, width - 1) + y / max(1, height - 1)) / 2.0
            r = int(start_color[0] + (end_color[0] - start_color[0]) * t)
            g = int(start_color[1] + (end_color[1] - start_color[1]) * t)
            b = int(start_color[2] + (end_color[2] - start_color[2]) * t)
            a = 255
            base.putpixel((x, y), (r, g, b, a))
    return base

def create_icons():
    source_path = 'Glossy Blue Raised-Arm Emblem.png'
    if not os.path.exists(source_path):
        raise FileNotFoundError(f"Source image '{source_path}' not found!")

    img = Image.open(source_path).convert('RGBA')
    bbox = img.getbbox()
    cropped = img.crop(bbox)
    w, h = cropped.size

    # Make emblem square by padding
    max_dim = max(w, h)
    emblem_sq = Image.new('RGBA', (max_dim, max_dim), (0, 0, 0, 0))
    emblem_sq.paste(cropped, ((max_dim - w) // 2, (max_dim - h) // 2), cropped)

    res_dir = os.path.join('app', 'src', 'main', 'res')
    os.makedirs(res_dir, exist_ok=True)

    # 1. High-Res App Logo for UI (512x512)
    logo_512 = emblem_sq.resize((512, 512), Image.Resampling.LANCZOS)
    drawable_dir = os.path.join(res_dir, 'drawable')
    os.makedirs(drawable_dir, exist_ok=True)
    logo_path = os.path.join(drawable_dir, 'app_logo.png')
    logo_512.save(logo_path, format='PNG')
    print(f"Saved {logo_path}")

    # Also save to docs/art for README / git documentation
    art_dir = os.path.join('docs', 'art')
    os.makedirs(art_dir, exist_ok=True)
    logo_512.save(os.path.join(art_dir, 'app_logo.png'), format='PNG')
    print(f"Saved {os.path.join(art_dir, 'app_logo.png')}")

    # 2. Adaptive Foregrounds (Canvas 108dp, Emblem occupies ~64% safe zone)
    # Sizes: mdpi: 108, hdpi: 162, xhdpi: 216, xxhdpi: 324, xxxhdpi: 432
    adaptive_sizes = {
        'mipmap-mdpi': 108,
        'mipmap-hdpi': 162,
        'mipmap-xhdpi': 216,
        'mipmap-xxhdpi': 324,
        'mipmap-xxxhdpi': 432
    }

    for folder, canvas_size in adaptive_sizes.items():
        target_dir = os.path.join(res_dir, folder)
        os.makedirs(target_dir, exist_ok=True)

        fg = Image.new('RGBA', (canvas_size, canvas_size), (0, 0, 0, 0))
        emblem_size = int(canvas_size * 0.64)
        resized_emblem = emblem_sq.resize((emblem_size, emblem_size), Image.Resampling.LANCZOS)
        offset = (canvas_size - emblem_size) // 2
        fg.paste(resized_emblem, (offset, offset), resized_emblem)

        fg_path = os.path.join(target_dir, 'ic_launcher_foreground.png')
        fg.save(fg_path, format='PNG')
        print(f"Saved {fg_path} ({canvas_size}x{canvas_size})")

    # 3. Legacy Launcher Icons (Square with rounded corners & Round)
    # Sizes: mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192
    legacy_sizes = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192
    }

    # Pre-render high-res 1024x1024 master backgrounds for supersampled quality
    bg_start = (22, 31, 51)  # #161F33
    bg_end = (8, 11, 18)     # #080B12
    border_color = (35, 50, 82, 255) # #233252

    master_size = 1024
    master_bg = make_gradient(master_size, master_size, bg_start, bg_end)

    # Master Round Icon
    round_mask = Image.new('L', (master_size, master_size), 0)
    round_draw = ImageDraw.Draw(round_mask)
    round_draw.ellipse((4, 4, master_size - 4, master_size - 4), fill=255)

    master_round = Image.new('RGBA', (master_size, master_size), (0, 0, 0, 0))
    master_round.paste(master_bg, (0, 0), round_mask)

    # Subtle border for round
    border_draw = ImageDraw.Draw(master_round)
    border_draw.ellipse((4, 4, master_size - 4, master_size - 4), outline=border_color, width=4)

    # Paste emblem on master round (70% scale)
    round_emblem_size = int(master_size * 0.70)
    round_emblem = emblem_sq.resize((round_emblem_size, round_emblem_size), Image.Resampling.LANCZOS)
    round_offset = (master_size - round_emblem_size) // 2
    master_round.paste(round_emblem, (round_offset, round_offset), round_emblem)

    # Master Square/Rounded Rect Icon (radius ~22%)
    squircle_mask = Image.new('L', (master_size, master_size), 0)
    squircle_draw = ImageDraw.Draw(squircle_mask)
    radius = int(master_size * 0.22)
    squircle_draw.rounded_rectangle((4, 4, master_size - 4, master_size - 4), radius=radius, fill=255)

    master_square = Image.new('RGBA', (master_size, master_size), (0, 0, 0, 0))
    master_square.paste(master_bg, (0, 0), squircle_mask)

    # Subtle border for squircle
    sq_border_draw = ImageDraw.Draw(master_square)
    sq_border_draw.rounded_rectangle((4, 4, master_size - 4, master_size - 4), radius=radius, outline=border_color, width=4)

    # Paste emblem on master square (75% scale)
    sq_emblem_size = int(master_size * 0.74)
    sq_emblem = emblem_sq.resize((sq_emblem_size, sq_emblem_size), Image.Resampling.LANCZOS)
    sq_offset = (master_size - sq_emblem_size) // 2
    master_square.paste(sq_emblem, (sq_offset, sq_offset), sq_emblem)

    for folder, size in legacy_sizes.items():
        target_dir = os.path.join(res_dir, folder)
        # Save round icon
        round_icon = master_round.resize((size, size), Image.Resampling.LANCZOS)
        round_path = os.path.join(target_dir, 'ic_launcher_round.png')
        round_icon.save(round_path, format='PNG')
        print(f"Saved {round_path} ({size}x{size})")

        # Save standard launcher icon
        square_icon = master_square.resize((size, size), Image.Resampling.LANCZOS)
        square_path = os.path.join(target_dir, 'ic_launcher.png')
        square_icon.save(square_path, format='PNG')
        print(f"Saved {square_path} ({size}x{size})")

    # Also save ic_launcher.png in drawable for legacy fallbacks
    fallback_icon = master_square.resize((192, 192), Image.Resampling.LANCZOS)
    fallback_path = os.path.join(drawable_dir, 'ic_launcher.png')
    fallback_icon.save(fallback_path, format='PNG')
    print(f"Saved fallback {fallback_path}")

if __name__ == '__main__':
    create_icons()
