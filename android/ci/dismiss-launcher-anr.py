"""仅关闭隔离模拟器中已知的系统桌面 ANR，不忽略被测应用 ANR。"""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

def launcher_button(xml):
    nodes=list(ET.fromstring(xml).iter('node'))
    titles=[n.get('text','') for n in nodes if n.get('resource-id')=='android:id/alertTitle']
    if "CQUST Wi-Fi isn't responding" in titles:
        raise RuntimeError('Application ANR: signed installation check failed')
    if "Pixel Launcher isn't responding" not in titles:
        return None
    button=next(n for n in nodes if n.get('resource-id')=='android:id/aerr_close' and n.get('text')=='Close app' and n.get('package')=='android')
    match=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',button.get('bounds',''))
    if not match:raise RuntimeError('Invalid system dialog bounds')
    left,top,right,bottom=map(int,match.groups())
    return (left+right)//2,(top+bottom)//2

if __name__=='__main__':
    serial=sys.argv[1]
    if not serial.startswith('emulator-'):raise RuntimeError('Only isolated emulators are allowed')
    point=launcher_button(Path(sys.argv[2]).read_text(encoding='utf-8'))
    if point:
        subprocess.run(['adb','-s',serial,'shell','input','tap',*map(str,point)],check=True)
        print('Dismissed known Pixel Launcher ANR only')
