#!/usr/bin/env python3
"""Convert Markdown (UTF-8) to PDF via HTML + Chrome headless."""
import argparse
import subprocess
import sys
from pathlib import Path
from typing import Optional
from urllib.parse import quote

import markdown

CHROME = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")

HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
@page { margin: 18mm 15mm; size: A4; }
body {
  font-family: "PingFang SC", "Hiragino Sans GB", "STHeiti", "Microsoft YaHei",
               "Noto Sans CJK SC", sans-serif;
  font-size: 11pt;
  line-height: 1.55;
  color: #1a1a1a;
  max-width: 100%;
}
h1 { font-size: 22pt; border-bottom: 2px solid #333; padding-bottom: 0.3em; page-break-after: avoid; }
h2 { font-size: 16pt; margin-top: 1.2em; page-break-after: avoid; }
h3 { font-size: 13pt; page-break-after: avoid; }
h4, h5, h6 { page-break-after: avoid; }
table { border-collapse: collapse; width: 100%; margin: 1em 0; font-size: 10pt; page-break-inside: auto; }
th, td { border: 1px solid #bbb; padding: 5px 8px; text-align: left; vertical-align: top; }
th { background: #f0f0f0; font-weight: 600; }
tr { page-break-inside: avoid; page-break-after: auto; }
code { font-family: "SF Mono", Menlo, monospace; font-size: 0.9em; background: #f5f5f5; padding: 0.1em 0.3em; }
pre { background: #f5f5f5; padding: 10px; overflow-x: auto; font-size: 9pt; }
pre code { background: none; padding: 0; }
blockquote { border-left: 4px solid #ccc; margin: 1em 0; padding-left: 1em; color: #444; }
a { color: #0066cc; word-break: break-all; }
hr { border: none; border-top: 1px solid #ddd; margin: 1.5em 0; }
ul, ol { padding-left: 1.5em; }
</style>
</head>
<body>
{body}
</body>
</html>
"""


def md_to_html(md_text: str) -> str:
    extensions = [
        "markdown.extensions.tables",
        "markdown.extensions.fenced_code",
        "markdown.extensions.nl2br",
        "markdown.extensions.sane_lists",
        "markdown.extensions.toc",
    ]
    body = markdown.markdown(md_text, extensions=extensions, output_format="html5")
    return HTML_TEMPLATE.replace("{body}", body)


def html_to_pdf(html_path: Path, pdf_path: Path) -> None:
    if not CHROME.is_file():
        raise FileNotFoundError(f"Chrome not found at {CHROME}")
    url = html_path.resolve().as_uri()
    cmd = [
        str(CHROME),
        "--headless=new",
        "--disable-gpu",
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-extensions",
        f"--print-to-pdf={pdf_path.resolve()}",
        "--no-pdf-header-footer",
        url,
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"Chrome failed ({result.returncode}): {result.stderr or result.stdout}")
    if not pdf_path.is_file() or pdf_path.stat().st_size == 0:
        raise RuntimeError("PDF was not created or is empty")


def convert(md_path: Path, pdf_path: Optional[Path] = None) -> Path:
    md_path = md_path.resolve()
    if pdf_path is None:
        pdf_path = md_path.with_suffix(".pdf")
    else:
        pdf_path = pdf_path.resolve()
    html_path = md_path.with_suffix(".pdf.tmp.html")
    text = md_path.read_text(encoding="utf-8")
    html_path.write_text(md_to_html(text), encoding="utf-8")
    try:
        html_to_pdf(html_path, pdf_path)
    finally:
        html_path.unlink(missing_ok=True)
    return pdf_path


def collect_md_paths(inputs: list[Path], recursive: bool) -> list[Path]:
    paths: list[Path] = []
    for p in inputs:
        p = p.resolve()
        if p.is_file():
            if p.suffix.lower() == ".md":
                paths.append(p)
            else:
                print(f"WARN: skip non-md file: {p}", file=sys.stderr)
        elif p.is_dir():
            pattern = "**/*.md" if recursive else "*.md"
            found = sorted(p.glob(pattern))
            if not found:
                print(f"WARN: no .md files in {p}", file=sys.stderr)
            paths.extend(found)
        else:
            print(f"ERROR: not found: {p}", file=sys.stderr)
            raise SystemExit(1)
    # dedupe while preserving order
    seen: set[Path] = set()
    unique: list[Path] = []
    for p in paths:
        if p not in seen:
            seen.add(p)
            unique.append(p)
    return unique


def main() -> int:
    parser = argparse.ArgumentParser(description="MD to PDF via Chrome")
    parser.add_argument(
        "inputs",
        nargs="+",
        type=Path,
        help="Markdown file(s) and/or directory(ies) containing .md files",
    )
    parser.add_argument(
        "-r",
        "--recursive",
        action="store_true",
        help="When input is a directory, include .md files in subdirectories",
    )
    args = parser.parse_args()
    md_files = collect_md_paths(args.inputs, args.recursive)
    if not md_files:
        print("ERROR: no markdown files to convert", file=sys.stderr)
        return 1
    failed: list[tuple[Path, str]] = []
    ok_count = 0
    total_bytes = 0
    for p in md_files:
        try:
            out = convert(p)
            size = out.stat().st_size
            total_bytes += size
            ok_count += 1
            print(f"OK: {out} ({size} bytes)")
        except Exception as e:
            failed.append((p, str(e)))
            print(f"FAIL: {p}: {e}", file=sys.stderr)
    print(f"SUMMARY: ok={ok_count} fail={len(failed)} total_bytes={total_bytes}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
