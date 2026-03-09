from PIL import Image, ImageEnhance
import sys

def process_icon(path):
    try:
        img = Image.open(path).convert("RGBA")
        width, height = img.size

        # Enhance the brightness of the lower part (the text)
        text_box = (0, int(height * 0.75), width, height)
        text_region = img.crop(text_box)

        # Increase brightness significantly to ensure text is white
        enhancer = ImageEnhance.Brightness(text_region)
        text_region = enhancer.enhance(4.0)

        # Paste back enhanced text
        img.paste(text_region, text_box, text_region)

        # Scale down the whole image to fit in the safe zone (65% to avoid launcher circle crop)
        new_size = (int(width * 0.65), int(height * 0.65))
        scaled = img.resize(new_size, Image.Resampling.LANCZOS)

        # Create a new transparent background of the original size
        bg = Image.new('RGBA', (width, height), (0, 0, 0, 0))

        # Paste the scaled image into the center
        offset_x = (width - new_size[0]) // 2
        offset_y = (height - new_size[1]) // 2
        bg.paste(scaled, (offset_x, offset_y), scaled)

        # Overwrite the file
        bg.save(path)
        print(f"Successfully processed {path}")
    except Exception as e:
        print(f"Error processing {path}: {e}")

process_icon('c:/Users/tk854/Desktop/New folder/Local Mind/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png')
process_icon('c:/Users/tk854/Desktop/New folder/Local Mind/app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png')
process_icon('c:/Users/tk854/Desktop/New folder/Local Mind/app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png')
process_icon('c:/Users/tk854/Desktop/New folder/Local Mind/app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png')
process_icon('c:/Users/tk854/Desktop/New folder/Local Mind/app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png')
