import xml.etree.ElementTree as ET
import re
from deep_translator import GoogleTranslator

def fix_placeholders(text):
    # Fix messed up placeholders like % 1 $ s -> %1$s
    text = re.sub(r'%\s*(\d+)\s*\$\s*([sd])', r'%\1$\2', text)
    # Fix % s -> %s
    text = re.sub(r'%\s*([sd])', r'%\1', text)
    return text

def translate_xml():
    translator = GoogleTranslator(source='en', target='hi')
    tree = ET.parse(r'app\src\main\res\values\strings.xml')
    root = tree.getroot()

    count = 0
    for string_elem in root.findall('string'):
        original_text = string_elem.text
        if original_text and not original_text.startswith('@'):
            try:
                # Do not translate app_name to keep it as LocalMind if wanted, but Google translates it fine usually.
                if string_elem.get('name') == 'app_name':
                    continue

                translated = translator.translate(original_text)
                translated = fix_placeholders(translated)
                string_elem.text = translated
                count += 1
                if count % 20 == 0:
                    print(f"Translated {count} strings...")
            except Exception as e:
                print(f"Failed to translate '{original_text}': {e}")

    # Save the modified XML
    tree.write(r'app\src\main\res\values-hi\strings.xml', encoding='utf-8', xml_declaration=True)
    print(f"Finished translation of {count} strings. Saved to values-hi/strings.xml")

if __name__ == '__main__':
    translate_xml()
