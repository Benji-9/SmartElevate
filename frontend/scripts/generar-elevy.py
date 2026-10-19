"""Genera las poses de Elevy en SVG: cuerpo caricaturesco + cabeza inspirada en el G1 de UADE."""
# Uso (desde frontend/): python3 scripts/generar-elevy.py src/assets/elevy
import pathlib
import sys

OUT = pathlib.Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)

WHITE, SHADE, LINE = '#ffffff', '#dde5ec', '#b9c6d2'
DARK, BLUE = '#141a21', '#3db8ff'

DEFS = f'''<defs>
<linearGradient id="b" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{WHITE}"/><stop offset="1" stop-color="{SHADE}"/></linearGradient>
<linearGradient id="h" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3a4552"/><stop offset="1" stop-color="#11171e"/></linearGradient>
<linearGradient id="v" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1c2530"/><stop offset="1" stop-color="#070b10"/></linearGradient>
<filter id="g" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="1.6" result="s"/><feMerge><feMergeNode in="s"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
</defs>'''

EYES = {
    'happy': 'M79 78q8-11 16 0M105 78q8-11 16 0',
    'sad': 'M79 79l15-6M121 79l-15-6',
    'closed': 'M79 74q8 6 16 0M105 74q8 6 16 0',
}


def eyes(kind):
    if kind == 'look-right':
        return f'<g fill="{BLUE}" filter="url(#g)"><rect x="88" y="64" width="9" height="15" rx="4.5"/><rect x="112" y="64" width="9" height="15" rx="4.5"/></g>'
    if kind == 'look-up':
        return f'<g fill="{BLUE}" filter="url(#g)"><rect x="85" y="60" width="9" height="14" rx="4.5"/><rect x="109" y="60" width="9" height="14" rx="4.5"/></g>'
    return f'<path d="{EYES[kind]}" fill="none" stroke="{BLUE}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" filter="url(#g)"/>'


def head(eye_kind, tilt=0):
    # Casco oscuro, tapa del LIDAR arriba y el aro de luz alrededor de la cara, como el G1.
    return f'''<g transform="rotate({tilt} 100 72)">
<ellipse cx="100" cy="70" rx="50" ry="49" fill="url(#h)" stroke="#56616e" stroke-width="2"/>
<path d="M62 44q38-30 76 0" fill="none" stroke="#56616e" stroke-width="2" stroke-linecap="round"/>
<path d="M74 32q26-12 52 0" fill="none" stroke="#ffffff" stroke-opacity=".25" stroke-width="4" stroke-linecap="round"/>
<rect x="60" y="48" width="80" height="62" rx="30" fill="url(#v)" stroke="{BLUE}" stroke-width="4" filter="url(#g)"/>
<path d="M71 60q6-6 16-7" fill="none" stroke="#ffffff" stroke-opacity=".16" stroke-width="3" stroke-linecap="round"/>
{eyes(eye_kind)}
</g>'''


def arm(points):
    d = 'M' + 'L'.join(f'{x} {y}' for x, y in points)
    hx, hy = points[-1]
    return (f'<path d="{d}" fill="none" stroke="{LINE}" stroke-width="19" stroke-linecap="round" stroke-linejoin="round"/>'
            f'<path d="{d}" fill="none" stroke="{WHITE}" stroke-width="15" stroke-linecap="round" stroke-linejoin="round"/>'
            f'<circle cx="{hx}" cy="{hy}" r="8.5" fill="{DARK}"/>')


BODY = f'''<rect x="90" y="112" width="20" height="14" rx="4" fill="{DARK}"/>
<rect x="82" y="176" width="36" height="10" rx="5" fill="{DARK}"/>
<rect x="77" y="180" width="18" height="30" rx="9" fill="url(#b)" stroke="{LINE}" stroke-width="2"/>
<rect x="105" y="180" width="18" height="30" rx="9" fill="url(#b)" stroke="{LINE}" stroke-width="2"/>
<ellipse cx="85" cy="219" rx="16" ry="6" fill="{DARK}"/><ellipse cx="115" cy="219" rx="16" ry="6" fill="{DARK}"/>
<path d="M69 217q0-12 16-12t16 12z" fill="url(#b)" stroke="{LINE}" stroke-width="2"/>
<path d="M99 217q0-12 16-12t16 12z" fill="url(#b)" stroke="{LINE}" stroke-width="2"/>
<rect x="66" y="122" width="68" height="60" rx="26" fill="url(#b)" stroke="{LINE}" stroke-width="2"/>
<rect x="89" y="140" width="22" height="6" rx="3" fill="{BLUE}" filter="url(#g)"/>'''

DOWN_L = [(72, 136), (60, 158), (62, 180)]


def sparkle(x, y, s=1.0):
    return (f'<path transform="translate({x} {y}) scale({s})" d="M0-9C1-2 2-1 9 0C2 1 1 2 0 9C-1 2-2 1-9 0C-2-1-1-2 0-9z" fill="{BLUE}" filter="url(#g)"/>')


def zz():
    return (f'<g fill="none" stroke="{BLUE}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" filter="url(#g)">'
            '<path d="M150 22h12l-12 14h12"/><path d="M170 4h8l-8 9h8"/></g>')


def dots():
    return (f'<g transform="translate(150 16)"><rect width="44" height="24" rx="12" fill="{WHITE}" stroke="{LINE}" stroke-width="2"/>'
            f'<g fill="{BLUE}"><circle cx="12" cy="12" r="3.5"/><circle cx="22" cy="12" r="3.5"/><circle cx="32" cy="12" r="3.5"/></g></g>')


def drop():
    return f'<path d="M160 40q-7 10 0 14q7-4 0-14z" fill="{BLUE}" fill-opacity=".75"/>'


POSES = {
    # pose: (brazos, ojos, inclinación de la cabeza, extras)
    'saludo': ([arm(DOWN_L), arm([(128, 136), (150, 122), (160, 98)])], 'happy', 4, ''),
    'festejo': ([arm([(72, 136), (52, 118), (42, 94)]), arm([(128, 136), (148, 118), (158, 94)])], 'happy', 0,
                sparkle(26, 60) + sparkle(178, 56, .8) + sparkle(170, 150, .6) + sparkle(30, 140, .5)),
    'senala': ([arm(DOWN_L), arm([(128, 136), (156, 134), (182, 128)])], 'look-right', 0, ''),
    'piensa': ([arm(DOWN_L), arm([(128, 136), (146, 152), (126, 118)])], 'look-up', -6, dots()),
    'triste': ([arm([(72, 136), (64, 160), (72, 178)]), arm([(128, 136), (136, 160), (128, 178)])], 'sad', -4, drop()),
    'duerme': ([arm([(72, 136), (64, 160), (76, 172)]), arm([(128, 136), (136, 160), (124, 172)])], 'closed', -10, zz()),
}


def svg(view_box, body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view_box}">'
            f'{DEFS}{body}</svg>\n')


for name, (arms, eye_kind, tilt, extra) in POSES.items():
    body = BODY + ''.join(arms) + head(eye_kind, tilt) + extra
    (OUT / f'{name}.svg').write_text(svg('0 0 200 230', body))

# Solo la cabeza, para tips, chips y avatares.
(OUT / 'cara.svg').write_text(svg('44 15 112 112', head('happy')))
print(sorted(p.name for p in OUT.iterdir()))
