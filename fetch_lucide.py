import urllib.request
import re
import os

icons = ['highlighter', 'pen', 'eraser', 'palette', 'undo', 'square', 'settings', 'maximize', 'zoom-in', 'zoom-out', 'menu', 'chevron-left', 'chevron-right', 'circle-check', 'trash', 'star']

out_file = 'app/src/main/java/com/example/zoterohelpernative/ui/icons/LucideIcons.kt'
os.makedirs(os.path.dirname(out_file), exist_ok=True)

with open(out_file, 'w') as f:
    f.write('package com.example.zoterohelpernative.ui.icons\n\n')
    f.write('import androidx.compose.ui.graphics.vector.ImageVector\n')
    f.write('import androidx.compose.ui.unit.dp\n')
    f.write('import androidx.compose.ui.graphics.SolidColor\n')
    f.write('import androidx.compose.ui.graphics.Color\n')
    f.write('import androidx.compose.ui.graphics.StrokeCap\n')
    f.write('import androidx.compose.ui.graphics.StrokeJoin\n')
    f.write('import androidx.compose.ui.graphics.vector.path\n\n')
    f.write('object LucideIcons {\n')

    for icon in icons:
        url = f'https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/{icon}.svg'
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req) as response:
                svg = response.read().decode('utf-8')
                
                paths = re.findall(r'<path[^>]*d="([^"]+)"', svg)
                circles = re.findall(r'<circle[^>]*cx="([^"]+)"[^>]*cy="([^"]+)"[^>]*r="([^"]+)"', svg)
                polygons = re.findall(r'<polygon[^>]*points="([^"]+)"', svg)
                lines = re.findall(r'<line[^>]*x1="([^"]+)"[^>]*y1="([^"]+)"[^>]*x2="([^"]+)"[^>]*y2="([^"]+)"', svg)
                rects = re.findall(r'<rect[^>]*x="([^"]+)"[^>]*y="([^"]+)"[^>]*width="([^"]+)"[^>]*height="([^"]+)"(?:[^>]*rx="([^"]+)")?', svg)
                
                name = ''.join(word.title() for word in icon.split('-'))
                f.write(f'    val {name}: ImageVector\n')
                f.write(f'        get() = ImageVector.Builder(\n')
                f.write(f'            name = "{name}",\n')
                f.write(f'            defaultWidth = 24.dp,\n')
                f.write(f'            defaultHeight = 24.dp,\n')
                f.write(f'            viewportWidth = 24f,\n')
                f.write(f'            viewportHeight = 24f\n')
                f.write('        )')
                
                def draw_path():
                    f.write('.path(\n')
                    f.write('            stroke = SolidColor(Color.Black),\n')
                    f.write('            strokeLineWidth = 2f,\n')
                    f.write('            strokeLineCap = StrokeCap.Round,\n')
                    f.write('            strokeLineJoin = StrokeJoin.Round\n')
                    f.write('        ) {\n')
                
                for path in paths:
                    draw_path()
                    for cmd in re.findall(r'[a-zA-Z][^a-zA-Z]*', path):
                        cmd = cmd.strip()
                        if not cmd: continue
                        letter = cmd[0]
                        nums = [float(x) for x in re.findall(r'-?(?:\d+\.\d*|\.\d+|\d+)', cmd[1:])]
                        
                        if letter == 'M': 
                            for i in range(0, len(nums), 2):
                                f.write(f'            moveTo({nums[i]}f, {nums[i+1]}f)\n')
                        elif letter == 'm': 
                            for i in range(0, len(nums), 2):
                                if i == 0:
                                    f.write(f'            moveToRelative({nums[i]}f, {nums[i+1]}f)\n')
                                else:
                                    f.write(f'            lineToRelative({nums[i]}f, {nums[i+1]}f)\n')
                        elif letter == 'L': 
                            for i in range(0, len(nums), 2):
                                f.write(f'            lineTo({nums[i]}f, {nums[i+1]}f)\n')
                        elif letter == 'l': 
                            for i in range(0, len(nums), 2):
                                f.write(f'            lineToRelative({nums[i]}f, {nums[i+1]}f)\n')
                        elif letter == 'H': 
                            for i in range(0, len(nums), 1):
                                f.write(f'            horizontalLineTo({nums[i]}f)\n')
                        elif letter == 'h': 
                            for i in range(0, len(nums), 1):
                                f.write(f'            horizontalLineToRelative({nums[i]}f)\n')
                        elif letter == 'V': 
                            for i in range(0, len(nums), 1):
                                f.write(f'            verticalLineTo({nums[i]}f)\n')
                        elif letter == 'v': 
                            for i in range(0, len(nums), 1):
                                f.write(f'            verticalLineToRelative({nums[i]}f)\n')
                        elif letter == 'C': 
                            for i in range(0, len(nums), 6):
                                f.write(f'            curveTo({nums[i]}f, {nums[i+1]}f, {nums[i+2]}f, {nums[i+3]}f, {nums[i+4]}f, {nums[i+5]}f)\n')
                        elif letter == 'c':
                            for i in range(0, len(nums), 6):
                                f.write(f'            curveToRelative({nums[i]}f, {nums[i+1]}f, {nums[i+2]}f, {nums[i+3]}f, {nums[i+4]}f, {nums[i+5]}f)\n')
                        elif letter == 'Q': 
                            for i in range(0, len(nums), 4):
                                f.write(f'            quadTo({nums[i]}f, {nums[i+1]}f, {nums[i+2]}f, {nums[i+3]}f)\n')
                        elif letter == 'q': 
                            for i in range(0, len(nums), 4):
                                f.write(f'            quadToRelative({nums[i]}f, {nums[i+1]}f, {nums[i+2]}f, {nums[i+3]}f)\n')
                        elif letter == 'A': 
                            for i in range(0, len(nums), 7):
                                f.write(f'            arcTo({nums[i]}f, {nums[i+1]}f, {nums[i+2]}f, {"true" if nums[i+3] == 1 else "false"}, {"true" if nums[i+4] == 1 else "false"}, {nums[i+5]}f, {nums[i+6]}f)\n')
                        elif letter == 'a':
                            for i in range(0, len(nums), 7):
                                f.write(f'            arcToRelative({nums[i]}f, {nums[i+1]}f, {nums[i+2]}f, {"true" if nums[i+3] == 1 else "false"}, {"true" if nums[i+4] == 1 else "false"}, {nums[i+5]}f, {nums[i+6]}f)\n')
                        elif letter == 'Z' or letter == 'z': f.write(f'            close()\n')
                        else: f.write(f'            // unhandled: {letter} {nums}\n')
                    f.write('        }')
                
                for cx, cy, r in circles:
                    cx, cy, r = float(cx), float(cy), float(r)
                    draw_path()
                    f.write(f'            moveTo({cx + r}f, {cy}f)\n')
                    f.write(f'            arcToRelative({r}f, {r}f, 0f, true, true, {-2*r}f, 0f)\n')
                    f.write(f'            arcToRelative({r}f, {r}f, 0f, true, true, {2*r}f, 0f)\n')
                    f.write('        }')
                    
                for pts in polygons:
                    pts = [float(x) for x in re.findall(r'-?\d+\.?\d*', pts)]
                    draw_path()
                    f.write(f'            moveTo({pts[0]}f, {pts[1]}f)\n')
                    for i in range(2, len(pts), 2):
                        f.write(f'            lineTo({pts[i]}f, {pts[i+1]}f)\n')
                    f.write(f'            close()\n')
                    f.write('        }')
                    
                for x1, y1, x2, y2 in lines:
                    draw_path()
                    f.write(f'            moveTo({float(x1)}f, {float(y1)}f)\n')
                    f.write(f'            lineTo({float(x2)}f, {float(y2)}f)\n')
                    f.write('        }')
                
                for x, y, w, h, rx in rects:
                    x, y, w, h = float(x), float(y), float(w), float(h)
                    rx = float(rx) if rx else 0
                    draw_path()
                    if rx == 0:
                        f.write(f'            moveTo({x}f, {y}f)\n')
                        f.write(f'            horizontalLineToRelative({w}f)\n')
                        f.write(f'            verticalLineToRelative({h}f)\n')
                        f.write(f'            horizontalLineToRelative({-w}f)\n')
                        f.write(f'            close()\n')
                    else:
                        f.write(f'            moveTo({x + rx}f, {y}f)\n')
                        f.write(f'            horizontalLineToRelative({w - 2*rx}f)\n')
                        f.write(f'            arcToRelative({rx}f, {rx}f, 0f, false, true, {rx}f, {rx}f)\n')
                        f.write(f'            verticalLineToRelative({h - 2*rx}f)\n')
                        f.write(f'            arcToRelative({rx}f, {rx}f, 0f, false, true, {-rx}f, {rx}f)\n')
                        f.write(f'            horizontalLineToRelative({-(w - 2*rx)}f)\n')
                        f.write(f'            arcToRelative({rx}f, {rx}f, 0f, false, true, {-rx}f, {-rx}f)\n')
                        f.write(f'            verticalLineToRelative({-(h - 2*rx)}f)\n')
                        f.write(f'            arcToRelative({rx}f, {rx}f, 0f, false, true, {rx}f, {-rx}f)\n')
                        f.write(f'            close()\n')
                    f.write('        }')

                f.write('\n.build()\n\n')
                print(f"Downloaded {icon}")
        except Exception as e:
            print(f"Error {icon}: {e}. Letter: {locals().get('letter')}, Nums: {locals().get('nums')}")

    f.write('}\n')
