"""Build and verify the physical references used by MarkerLayout / DICT_4X4_50.

pip install opencv-python-headless==4.12.0.88 reportlab pymupdf pypdf
python tools/create_marker_templates.py
"""
from pathlib import Path
import shutil
import cv2
import pymupdf as fitz
import numpy as np
from reportlab.pdfgen import canvas
from reportlab.lib.units import mm
from reportlab.lib.colors import HexColor, black, white

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output" / "pdf"
QA = ROOT / "tmp" / "pdfs"
OUT.mkdir(parents=True, exist_ok=True)
QA.mkdir(parents=True, exist_ok=True)
PDF = OUT / "marcadores-dibujo.pdf"
dictionary = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_50)
positions = ["Arriba izquierda", "Arriba derecha", "Abajo derecha", "Abajo izquierda"]
pdf = canvas.Canvas(str(PDF), pagesize=(210 * mm, 297 * mm))
pdf.setTitle("Mural AR - Marcadores para papel, lienzo y pared")
pdf.setAuthor("Mural AR")


def text(x, y, value, size=10, color=black, font="Helvetica"):
    pdf.setFillColor(color)
    pdf.setFont(font, size)
    pdf.drawString(x * mm, (297 - y) * mm, value)


def marker(marker_id, x, y, side):
    # Vector modules avoid raster resampling; dimensions refer to the black outer square.
    bits = cv2.aruco.generateImageMarker(dictionary, marker_id, 6)
    pdf.setFillColor(white)
    pdf.rect((x - 4) * mm, (297 - y - side - 4) * mm,
             (side + 8) * mm, (side + 8) * mm, stroke=0, fill=1)
    pdf.setFillColor(black)
    step = side / 6
    for row in range(6):
        for col in range(6):
            if bits[row, col] == 0:
                pdf.rect((x + col * step) * mm,
                         (297 - y - (row + 1) * step) * mm,
                         step * mm, step * mm, stroke=0, fill=1)


def header(subtitle):
    text(15, 19, "MURAL / DIBUJO", 17, HexColor("#173c2d"), "Helvetica-Bold")
    text(15, 27, subtitle, 10)


def arrow(x, y):
    pdf.setStrokeColor(black)
    pdf.setLineWidth(.5)
    pdf.line(x * mm, (297 - y) * mm, x * mm, (297 - y + 5) * mm)
    pdf.line(x * mm, (297 - y + 5) * mm, (x - 1.5) * mm, (297 - y + 3) * mm)
    pdf.line(x * mm, (297 - y + 5) * mm, (x + 1.5) * mm, (297 - y + 3) * mm)


header("Referencia de cámara para superficies planas. No requiere Google AR.")
text(15, 40, "1 / Marcadores pequeños: lado negro de 25 mm", 12, font="Helvetica-Bold")
text(15, 48, "Imprime esta página al 100 %, sin ajustar. Recorta por las líneas discontinuas.")
for marker_id, (x, y) in enumerate([(35, 69), (145, 69), (145, 125), (35, 125)]):
    pdf.setStrokeColor(HexColor("#777777"))
    pdf.setDash(2, 2)
    pdf.rect((x - 5) * mm, (297 - y - 35) * mm, 35 * mm, 45 * mm, fill=0, stroke=1)
    pdf.setDash()
    marker(marker_id, x, y, 25)
    arrow(x + 12.5, y - 4)
    text(x, y + 32, f"{marker_id} / {positions[marker_id]}", 7)

text(15, 177, "2 / Colócalos por fuera de la zona del dibujo", 12, font="Helvetica-Bold")
text(15, 185, "0 arriba izquierda; 1 arriba derecha; 2 abajo derecha; 3 abajo izquierda.")
text(15, 192, "Todas las flechas hacia arriba. No gires cada recorte para seguir la esquina.")
text(15, 199, "Deja 5 mm desde el borde negro a cada borde de la esquina del dibujo.")
text(15, 206, "Conserva el margen blanco. Sujeta todo en el mismo plano, sin arrugas.")

# Dimensioned corner detail: marker diagonally outside drawing, both gaps are 5 mm.
pdf.setFillColor(HexColor("#e2eee7"))
pdf.rect(47 * mm, (297 - 255) * mm, 63 * mm, 19 * mm, fill=1, stroke=0)
pdf.setFillColor(black)
pdf.rect(30 * mm, (297 - 231) * mm, 12 * mm, 12 * mm, fill=1, stroke=0)
text(34, 227, "0", 12, white, "Helvetica-Bold")
text(44, 229, "5 mm", 7)
text(29, 235, "5 mm", 7)
text(55, 247, "Zona de dibujo", 10)
text(130, 227, "Mide ancho y alto", 9)
text(130, 234, "e introdúcelos en", 9)
text(130, 241, "Formato, en la app.", 9)
text(15, 263, "3 / Encuadra al menos tres marcadores", 12, font="Helvetica-Bold")
text(15, 271, "Carga una imagen y ajusta su opacidad. Comprueba la alineación antes de trazar.", 9)
text(15, 278, "La imagen se ve en la pantalla; el teléfono no la proyecta sobre la superficie.", 9)
text(15, 289, "Papel / lienzo: 2,5 cm en la app. Pared: usa los cuatro marcadores de las páginas siguientes.", 8)
pdf.showPage()

for marker_id in range(4):
    header(f"Marcador grande {marker_id} / {positions[marker_id]} / lado negro 160 mm")
    text(15, 39, "Imprime al 100 %, sin ajustar. En la app: lado del marcador = 16 cm.", 10)
    pdf.setStrokeColor(HexColor("#777777")); pdf.setDash(3, 2)
    pdf.rect(15 * mm, 62 * mm, 180 * mm, 180 * mm, fill=0, stroke=1)
    pdf.setDash()
    marker(marker_id, 25, 65, 160)
    arrow(105, 62)
    text(112, 61, "ARRIBA", 9, font="Helvetica-Bold")
    text(25, 232, f"{marker_id} / {positions[marker_id]}", 9, font="Helvetica-Bold")
    text(15, 246, "Recorta por la línea discontinua. Conserva el margen blanco y la orientación.", 10)
    text(15, 255, f"Colocación: {positions[marker_id].lower()}, por fuera del rectángulo que vas a pintar.", 10)
    text(15, 264, "Deja 5 mm del borde negro a cada borde de la esquina. No tapes el código.", 10)
    text(15, 278, "Mantén al menos tres marcadores visibles. Para murales grandes, trabaja por secciones.", 9)
    text(15, 287, "Referencia plana: no sirve para superficies curvas. Valida la precisión en el lugar de trabajo.", 9)
    pdf.showPage()
pdf.save()

# Render final PDF; verify each printed code through the same OpenCV dictionary.
document = fitz.open(PDF)
detector = cv2.aruco.ArucoDetector(dictionary)
for index, page in enumerate(document):
    pix = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
    pix.save(QA / f"marcadores-{index + 1}.png")
    rgb = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, 3)
    _, ids, _ = detector.detectMarkers(cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY))
    found = sorted(ids.flatten().tolist()) if ids is not None else []
    expected = [0, 1, 2, 3] if index == 0 else [index - 1]
    assert found == expected, (index, found, expected)
    assert abs(page.rect.width / mm - 210) < .01
    assert abs(page.rect.height / mm - 297) < .01
    print(f"Page {index + 1}: A4, marker IDs {found}")
asset = ROOT / "app" / "src" / "main" / "assets" / PDF.name
asset.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(PDF, asset)
print(PDF)
