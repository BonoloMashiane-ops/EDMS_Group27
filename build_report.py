"""Generate the combined EDMS submission report PDF from the Markdown drafts.

Requires reportlab (bundled in the Codex authoring environment).
Run: python3 scripts/build_report.py
"""

from __future__ import annotations

import html
import re
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    Image,
    KeepTogether,
    PageBreak,
    PageTemplate,
    Paragraph,
    Preformatted,
    Spacer,
    Table,
    TableStyle,
)

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "output" / "pdf" / "EDMS_Group27_GUI_Report_DRAFT.pdf"
DEST.parent.mkdir(parents=True, exist_ok=True)

FONT_ROOT = Path("/usr/share/fonts/truetype/dejavu")
pdfmetrics.registerFont(TTFont("DejaVu", str(FONT_ROOT / "DejaVuSans.ttf")))
pdfmetrics.registerFont(TTFont("DejaVu-Bold", str(FONT_ROOT / "DejaVuSans-Bold.ttf")))
pdfmetrics.registerFont(TTFont("DejaVuMono", str(FONT_ROOT / "DejaVuSansMono.ttf")))
pdfmetrics.registerFontFamily("DejaVu", normal="DejaVu", bold="DejaVu-Bold")

NAVY = colors.HexColor("#142438")
TEAL = colors.HexColor("#157F86")
SLATE = colors.HexColor("#44566B")
PALE = colors.HexColor("#EEF6F6")
GREY = colors.HexColor("#EEF1F4")
WIDTH, HEIGHT = A4
LEFT, RIGHT, TOP, BOTTOM = 22 * mm, 22 * mm, 23 * mm, 20 * mm
CONTENT = WIDTH - LEFT - RIGHT

styles = {}
styles["normal"] = ParagraphStyle(
    "EDMSNormal", fontName="DejaVu", fontSize=9.2, leading=14,
    textColor=NAVY, spaceAfter=8, allowWidows=0, allowOrphans=0,
)
styles["small"] = ParagraphStyle(
    "EDMSSmall", parent=styles["normal"], fontSize=7.1, leading=10,
    spaceAfter=0,
)
styles["headercell"] = ParagraphStyle(
    "EDMSHeaderCell", parent=styles["small"],
    fontName="DejaVu-Bold", textColor=colors.white,
)
styles["title"] = ParagraphStyle(
    "EDMSTitle", fontName="DejaVu-Bold", fontSize=27, leading=36,
    textColor=NAVY, spaceAfter=15,
)
styles["subtitle"] = ParagraphStyle(
    "EDMSSubtitle", fontName="DejaVu", fontSize=13, leading=21,
    textColor=SLATE, spaceAfter=10,
)
styles["heading1"] = ParagraphStyle(
    "EDMSH1", fontName="DejaVu-Bold", fontSize=17, leading=23,
    textColor=NAVY, spaceBefore=16, spaceAfter=10, keepWithNext=True,
)
styles["heading2"] = ParagraphStyle(
    "EDMSH2", fontName="DejaVu-Bold", fontSize=11.3, leading=16,
    textColor=TEAL, spaceBefore=11, spaceAfter=6, keepWithNext=True,
)
styles["heading3"] = ParagraphStyle(
    "EDMSH3", fontName="DejaVu-Bold", fontSize=9.7, leading=14,
    textColor=NAVY, spaceBefore=8, spaceAfter=4, keepWithNext=True,
)
styles["bullet"] = ParagraphStyle(
    "EDMSBullet", parent=styles["normal"], leftIndent=13,
    firstLineIndent=-9, spaceAfter=5,
)
styles["code"] = ParagraphStyle(
    "EDMSCode", fontName="DejaVuMono", fontSize=7.1, leading=10,
    textColor=NAVY, leftIndent=9, rightIndent=5, spaceBefore=4, spaceAfter=8,
)
styles["caption"] = ParagraphStyle(
    "EDMSCaption", fontName="DejaVu", fontSize=8, leading=12,
    textColor=SLATE, alignment=TA_CENTER, spaceAfter=11,
)


def clean_unicode(value: str) -> str:
    # Text emoji require separate color fonts, which are not reliably embedded.
    for original, replacement in {
        "🔐": "[lock]", "✅": "[check]", "🇿🇦": "[ZA]",
        "→": " to ", "↔": " <-> ", "–": "-", "—": "-", "‑": "-",
        "✓": "[pass]", "❌": "[error]", "✔": "[check]",
    }.items():
        value = value.replace(original, replacement)
    return value


def inline_markup(value: str) -> str:
    value = html.escape(clean_unicode(value))
    # Restore safe formatting after HTML escaping. URLs in drafts are fixed files.
    value = re.sub(
        r"\[([^\]]+)\]\((https?://[^)]+|[^)]+\.md)\)",
        lambda m: '<link href="{}" color="#157F86">{}</link>'.format(
            html.unescape(m.group(2)), m.group(1)
        ),
        value,
    )
    value = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", value)
    value = re.sub(r"`([^`]+)`", r'<font name="DejaVuMono" size="8">\1</font>', value)
    return value


def paragraph(text: str, style: str = "normal") -> Paragraph:
    return Paragraph(inline_markup(text), styles[style])


def make_table(rows: list[list[str]]):
    columns = len(rows[0])
    if columns == 5:
        widths = [CONTENT * f for f in (0.065, 0.13, 0.37, 0.325, 0.11)]
    elif columns == 3:
        widths = [CONTENT * f for f in (0.16, 0.37, 0.47)]
    elif columns == 2:
        widths = [CONTENT * 0.31, CONTENT * 0.69]
    else:
        widths = [CONTENT / columns] * columns
    data = [[paragraph(value, "headercell" if row_index == 0 else "small")
             for value in row] for row_index, row in enumerate(rows)]
    table = Table(data, colWidths=widths, repeatRows=1, hAlign="LEFT")
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), NAVY),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, GREY]),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ("LINEBELOW", (0, 0), (-1, 0), 0.8, TEAL),
    ]))
    return table


def parse_markdown(path: Path, skip_top_heading: bool = True, stop_at: str | None = None):
    lines = path.read_text(encoding="utf-8").splitlines()
    result = []
    i = 0
    top_skipped = False
    while i < len(lines):
        line = lines[i].rstrip()
        if stop_at is not None and line.startswith(stop_at):
            break
        if not line.strip():
            i += 1
            continue
        if line.startswith("```"):
            i += 1
            code = []
            while i < len(lines) and not lines[i].startswith("```"):
                code.append(clean_unicode(lines[i]))
                i += 1
            i += 1
            # Indented compact code to avoid clipping. Long lines are truncated
            # only in visual transcript; full exact evidence remains in Markdown.
            result.append(Preformatted("\n".join(code), styles["code"], maxLineLength=91))
            continue
        if line.startswith("|") and i + 1 < len(lines) and re.match(r"^\|[\s:|-]+\|$", lines[i + 1]):
            rows = []
            rows.append([s.strip() for s in line.strip().strip("|").split("|")])
            i += 2
            while i < len(lines) and lines[i].startswith("|"):
                rows.append([s.strip() for s in lines[i].strip().strip("|").split("|")])
                i += 1
            # Separate a few oversized table rows from nearby paragraphs naturally.
            result.append(Spacer(1, 4))
            result.append(make_table(rows))
            result.append(Spacer(1, 8))
            continue
        if line.startswith("# "):
            if skip_top_heading and not top_skipped:
                top_skipped = True
            else:
                result.append(paragraph(line[2:], "heading1"))
            i += 1
            continue
        if line.startswith("## "):
            result.append(paragraph(line[3:], "heading1"))
            i += 1
            continue
        if line.startswith("### "):
            result.append(paragraph(line[4:], "heading2"))
            i += 1
            continue
        if re.match(r"^\d+\.\s", line) or line.startswith("- "):
            marker = re.match(r"^(\d+\.|-)\s+(.*)$", line)
            result.append(paragraph("• " + marker.group(2), "bullet"))
            i += 1
            continue
        if line.startswith("!["):
            i += 1
            continue
        if line == "---":
            result.append(Spacer(1, 9))
            i += 1
            continue
        block = [line]
        i += 1
        while i < len(lines):
            nxt = lines[i]
            if (not nxt.strip() or nxt.startswith(("#", "```", "|", "- ", "!["))
                    or re.match(r"^\d+\.\s", nxt)):
                break
            block.append(nxt.strip())
            i += 1
        result.append(paragraph(" ".join(block)))
    return result


class EdmsDocument(BaseDocTemplate):
    def __init__(self, filename: Path):
        super().__init__(str(filename), pagesize=A4, leftMargin=LEFT,
                         rightMargin=RIGHT, topMargin=TOP, bottomMargin=BOTTOM,
                         title="EDMS Group 27 Swing Desktop GUI and Design Report - Draft",
                         author="EDMS Group 27 (draft for group review)")
        frame = Frame(LEFT, BOTTOM, CONTENT, HEIGHT - TOP - BOTTOM,
                      leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
        self.addPageTemplates(PageTemplate(id="main", frames=[frame], onPage=self.decorate))

    def decorate(self, canvas, doc):
        canvas.saveState()
        if doc.page == 1:
            canvas.setFillColor(NAVY)
            canvas.rect(0, HEIGHT - 14 * mm, WIDTH, 14 * mm, stroke=0, fill=1)
        else:
            canvas.setStrokeColor(colors.HexColor("#D5DEE4"))
            canvas.line(LEFT, HEIGHT - 15 * mm, WIDTH - RIGHT, HEIGHT - 15 * mm)
            canvas.setFillColor(SLATE)
            canvas.setFont("DejaVu", 7.5)
            canvas.drawString(LEFT, HEIGHT - 12 * mm, "EDMS  |  GROUP 27  |  DESKTOP GUI DESIGN REPORT")
        canvas.setStrokeColor(colors.HexColor("#D5DEE4"))
        canvas.line(LEFT, 15 * mm, WIDTH - RIGHT, 15 * mm)
        canvas.setFont("DejaVu", 7)
        canvas.setFillColor(SLATE)
        canvas.drawString(LEFT, 11 * mm, "DRAFT - review team contributions and course rubric")
        canvas.drawRightString(WIDTH - RIGHT, 11 * mm, f"Page {doc.page}")
        canvas.restoreState()


story = []
story.extend([
    Spacer(1, 41 * mm),
    Paragraph("ENCRYPTED DIRECT<br/>MESSAGING SYSTEM", styles["title"]),
    paragraph("Swing Desktop GUI, Software Design, Testing and Graduate Attribute Reflection", "subtitle"),
    Spacer(1, 11 * mm),
    paragraph("**SDN260S - Software Design II**"),
    paragraph("**Group 27**"),
    paragraph("26 October 2026 | Java 17 | Swing Desktop GUI Trial"),
    Spacer(1, 12 * mm),
    Table([[paragraph("DRAFT FOR GROUP VERIFICATION - Swing GUI and 23 passing tests", "small")]],
          colWidths=[CONTENT], style=TableStyle([
              ("BACKGROUND", (0, 0), (-1, -1), PALE),
              ("BOX", (0, 0), (-1, -1), 0.7, TEAL),
              ("TOPPADDING", (0, 0), (-1, -1), 14),
              ("BOTTOMPADDING", (0, 0), (-1, -1), 14),
              ("LEFTPADDING", (0, 0), (-1, -1), 14),
          ])),
    Spacer(1, 34 * mm),
    paragraph("Prepared from the group's supplied Java console prototype and UML image. Review all names, authorship, technical claims and reflection before submission.", "normal"),
    PageBreak(),
    paragraph("Original UML Class Diagram", "heading1"),
    paragraph("This is the group's supplied five-class model. The implementation adds explicit authentication, network and logging services and refines several field types.", "normal"),
])

uml = ROOT / "docs" / "ORIGINAL_UML.jpeg"
if uml.exists():
    img = Image(str(uml))
    img.drawWidth = CONTENT
    img.drawHeight = CONTENT * 1199 / 1312
    story.append(img)
    story.append(Spacer(1, 5 * mm))
    story.append(paragraph("Figure 1. Original supplied Secure Chat Application UML.", "caption"))

story.extend([
    paragraph("Implementation mapping", "heading2"),
    paragraph("User / AuthManager handles accounts; Message stores the UUID, participants, time and content; ChatHistory uses FileStorage for encrypted records; EncryptionService applies AES-GCM; NetworkManager handles sockets; AuditLogger keeps operational events. EdmsGui is the new Swing desktop view/controller. The exact updated diagram is provided in UML_UPDATED.md with the source archive."),
    PageBreak(),
    paragraph("New Swing Desktop Chat Interface", "heading1"),
    paragraph("The new UI adds account registration/login, server/client connection setup, encrypted chat bubbles, a history viewer and disconnect/logout controls. This image is rendered from the actual Swing ChatPanel component with example messages, not captured from a live chat session."),
])
gui_image = ROOT / "docs" / "GUI_PREVIEW.png"
if gui_image.exists():
    preview = Image(str(gui_image))
    preview.drawWidth = CONTENT
    preview.drawHeight = CONTENT * 740 / 1100
    story.append(preview)
    story.append(Spacer(1, 5 * mm))
    story.append(paragraph("Figure 2. Swing desktop chat preview with sample conversation.", "caption"))
story.extend([
    paragraph("GUI implementation", "heading2"),
    paragraph("EdmsGui uses SwingWorker and a dedicated receiver thread so network waits and encryption do not block the interface. It shares EdmsPrototype services and the same encrypted handshake with the console view. A complete interactive GUI rehearsal on the group's desktop is still required."),
    PageBreak(),
    paragraph("Part A. Software Design Report", "heading1"),
])
story.extend(parse_markdown(ROOT / "docs" / "SOFTWARE_DESIGN_REPORT.md"))
story.extend([PageBreak(), paragraph("Part B. Test Evidence", "heading1")])
story.extend(parse_markdown(ROOT / "docs" / "TEST_EVIDENCE.md"))
story.extend([PageBreak(), paragraph("Part C. GA2 and GA7 Reflection", "heading1")])
story.extend(parse_markdown(ROOT / "docs" / "GA2_GA7_REFLECTION.md",
                            stop_at="## Evidence mapping for the assessor"))

EdmsDocument(DEST).build(story)
print(f"Generated: {DEST}")
