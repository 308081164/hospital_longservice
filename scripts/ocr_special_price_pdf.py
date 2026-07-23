#!/usr/bin/env python3
"""Render scan PDF with PyMuPDF + Tesseract chi_sim+eng; write OCR txt under 测试用例."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

try:
    import fitz  # pymupdf
except ImportError:
    print("pip install pymupdf", file=sys.stderr)
    raise

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_LANG = "chi_sim+eng"


def ocr_page(page: fitz.Page, zoom: float = 2.5) -> str:
    mat = fitz.Matrix(zoom, zoom)
    pix = page.get_pixmap(matrix=mat, alpha=False)
    png = pix.tobytes("png")
    proc = subprocess.run(
        ["tesseract", "stdin", "stdout", "-l", DEFAULT_LANG, "--psm", "6"],
        input=png,
        capture_output=True,
        check=False,
    )
    if proc.returncode != 0:
        proc = subprocess.run(
            ["tesseract", "stdin", "stdout", "-l", DEFAULT_LANG],
            input=png,
            capture_output=True,
            check=False,
        )
    return proc.stdout.decode("utf-8", errors="replace")


def ocr_pdf(pdf_path: Path, scales: tuple[float, ...] = (2.0, 2.5, 3.0)) -> str:
    doc = fitz.open(pdf_path)
    parts: list[str] = []
    for i, page in enumerate(doc):
        best = ""
        for z in scales:
            text = ocr_page(page, z)
            if len(text.strip()) > len(best.strip()):
                best = text
        parts.append(f"\n===== PAGE {i + 1} =====\n")
        parts.append(best)
    doc.close()
    return "".join(parts)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("pdf", type=Path, help="PDF path")
    ap.add_argument("-o", "--out", type=Path, required=True, help="Output .txt path")
    args = ap.parse_args()
    pdf = args.pdf if args.pdf.is_absolute() else ROOT / args.pdf
    out = args.out if args.out.is_absolute() else ROOT / args.out
    out.parent.mkdir(parents=True, exist_ok=True)
    text = ocr_pdf(pdf)
    out.write_text(text.lstrip(), encoding="utf-8")
    print(f"Wrote {out} ({len(text)} chars)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
