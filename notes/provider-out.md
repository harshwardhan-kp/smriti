FILE: tools/prompt_harness.py
```python
#!/usr/bin/env python3
"""
prompt_harness.py – Iterate Smriti prompts against the Gemini API on a laptop.

Usage:
    python3 tools/prompt_harness.py
    python3 tools/prompt_harness.py --prompt-file tools/prompts/v2.txt
    python3 tools/prompt_harness.py --model gemini-2.0-flash --cases tools/cases.json
    python3 tools/prompt_harness.py --show 3

See tools/README.md for full documentation.
"""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from datetime import date
from typing import Any, Dict, List, Optional

# Sibling module — same directory.
_TOOLS_DIR = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(_TOOLS_DIR))
import repair  # noqa: E402  (after sys.path manipulation)


# ---------------------------------------------------------------------------
# API key resolution
# ---------------------------------------------------------------------------

def _resolve_api_key(provider: str = "gemini") -> str:
    """Return the API key for *provider* or exit with a clear message."""
    if provider == "muse":
        key = os.environ.get("MUSE_API_KEY", "").strip()
        if key:
            return key
        key_file = pathlib.Path.home() / ".config" / "muse-key"
        if key_file.is_file():
            key = key_file.read_text().strip()
            if key:
                return key
        print(
            "ERROR: Muse API key not found.\n"
            "Set the MUSE_API_KEY environment variable, or write the key to\n"
            f"  {key_file}\n"
            "(one line, no quotes).",
            file=sys.stderr,
        )
        sys.exit(1)
    else:
        key = os.environ.get("GEMINI_API_KEY", "").strip()
        if key:
            return key
        key_file = pathlib.Path.home() / ".config" / "gemini-key"
        if key_file.is_file():
            key = key_file.read_text().strip()
            if key:
                return key
        print(
            "ERROR: Gemini API key not found.\n"
            "Set the GEMINI_API_KEY environment variable, or write the key to\n"
            f"  {key_file}\n"
            "(one line, no quotes).",
            file=sys.stderr,
        )
        sys.exit(1)


# ---------------------------------------------------------------------------
# Gemini API call
# ---------------------------------------------------------------------------

def _call_gemini(prompt: str, model: str, key: str) -> str:
    """Send *prompt* to the Gemini REST API and return the text response."""
    url = (
        f"https://generativelanguage.googleapis.com/v1beta/"
        f"models/{model}:generateContent"
    )
    body = json.dumps(
        {"contents": [{"parts": [{"text": prompt}]}]}
    ).encode()

    req = urllib.request.Request(
        url,
        data=body,
        headers={
            "Content-Type": "application/json",
            "x-goog-api-key": key,
        },
        method="POST",
    )

    # 503 (high demand) and 429 (rate limit) are transient and common on flash models.
    # A benchmark that aborts the whole run on one of them is useless, so back off and retry.
    # 404 and 400 are our fault - fail immediately rather than retrying a broken request.
    last_err = ""
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                data = json.loads(resp.read().decode())
            break
        except urllib.error.HTTPError as exc:
            err_body = exc.read().decode() if exc.fp else ""
            last_err = f"HTTP {exc.code}\n{err_body}"
            if exc.code in (429, 500, 502, 503, 504) and attempt < 4:
                wait = 2 ** attempt * 3
                print(
                    f"  [{exc.code}, retrying in {wait}s "
                    f"({attempt + 1}/4)]",
                    file=sys.stderr,
                )
                time.sleep(wait)
                continue
            print(f"ERROR: Gemini API returned {last_err}", file=sys.stderr)
            sys.exit(2)
        except (urllib.error.URLError, TimeoutError) as exc:
            last_err = str(exc)
            if attempt < 4:
                time.sleep(2 ** attempt * 3)
                continue
            print(f"ERROR: network failure: {last_err}", file=sys.stderr)
            sys.exit(2)
    else:
        print(f"ERROR: gave up after 5 attempts: {last_err}", file=sys.stderr)
        sys.exit(2)

    # Navigate the response to the text part.
    try:
        return data["candidates"][0]["content"]["parts"][0]["text"]
    except (KeyError, IndexError):
        print(
            "ERROR: Unexpected Gemini API response structure:\n"
            + json.dumps(data, indent=2),
            file=sys.stderr,
        )
        sys.exit(2)


# ---------------------------------------------------------------------------
# Muse CLI call
# ---------------------------------------------------------------------------

def _call_muse(prompt: str, model: str, key: str) -> str:
    """Send *prompt* via the `muse` CLI and return the text response."""
    with tempfile.TemporaryDirectory() as tmpdir:
        result = subprocess.run(
            ["muse", "exec", "--api-key-stdin", "--provider", "meta", "--model", model, "--reasoning-effort", "high", prompt],
            input=key,
            capture_output=True,
            text=True,
            timeout=300,
            cwd=tmpdir,
        )
        if result.returncode != 0:
            print(result.stderr, file=sys.stderr)
            sys.exit(2)
        lines = result.stdout.splitlines()
        filtered = [l for l in lines if not l.startswith("muse:")]
        return "\n".join(filtered)


# ---------------------------------------------------------------------------
# Dispatch
# ---------------------------------------------------------------------------

def _call_model(prompt: str, args) -> str:
    """Dispatch to the correct backend based on args.provider."""
    if args.provider == "muse":
        return _call_muse(prompt, args.model, args.api_key)
    else:
        return _call_gemini(prompt, args.model, args.api_key)


# ---------------------------------------------------------------------------
# Template rendering
# ---------------------------------------------------------------------------

def _render_template(template: str, ocr: str, transcript: str) -> str:
    today_str = date.today().isoformat()
    return (
        template
        .replace("{TODAY}", today_str)
        .replace("{OCR}", ocr)
        .replace("{TRANSCRIPT}", transcript)
    )


# ---------------------------------------------------------------------------
# Scoring
# ---------------------------------------------------------------------------

def _score_case(
    record: Optional[Dict[str, Any]],
    expect: Dict[str, Any],
) -> tuple[bool, List[str]]:
    """Return (passed, [reason, ...])."""
    reasons: List[str] = []

    min_actions: int = expect.get("min_actions", 0)
    must_contain: List[str] = expect.get("must_contain", [])

    # --- noise case: expect nothing useful --------------------------------
    if min_actions == 0 and not must_contain:
        # Either None or an empty-ish record is fine.
        if record is None:
            return True, []
        # A record with no actions and an empty/whitespace-only summary is ok
        actions = record.get("actions", [])
        if not actions:
            return True, []
        reasons.append(f"expected no actions but got {len(actions)}")
        return False, reasons

    # --- meaningful case: record must exist --------------------------------
    if record is None:
        return False, ["parse_record returned None"]

    actions = record.get("actions", [])
    if len(actions) < min_actions:
        reasons.append(
            f"expected ≥{min_actions} action(s) but got {len(actions)}"
        )

    # Flatten the entire record to a single string for substring checks.
    flat = json.dumps(record, ensure_ascii=False).lower()
    for token in must_contain:
        if token.lower() not in flat:
            reasons.append(f"missing expected token '{token}'")

    return len(reasons) == 0, reasons


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Iterate Smriti prompt templates against the Gemini API."
    )
    parser.add_argument(
        "--prompt-file",
        default=str(_TOOLS_DIR / "prompts" / "v1.txt"),
        help="Path to the prompt template (default: tools/prompts/v1.txt).",
    )
    parser.add_argument(
        "--cases",
        default=str(_TOOLS_DIR / "cases.json"),
        help="Path to the JSON test-cases file (default: tools/cases.json).",
    )
    parser.add_argument(
        "--model",
        default=None,
        help="Model name (default: gemini-3.6-flash for gemini, muse-spark-1.2-contributor for muse).",
    )
    parser.add_argument(
        "--provider",
        choices=["gemini", "muse"],
        default="gemini",
        help="Backend provider (default: gemini).",
    )
    parser.add_argument(
        "--show",
        type=int,
        default=None,
        metavar="N",
        help="Print the raw model output for case N (1-based) and exit.",
    )
    parser.add_argument(
        "--out",
        default=None,
        dest="out",
        metavar="PATH",
        help="Write a JSON report to PATH.",
    )
    args = parser.parse_args()

    # Resolve default model from provider when --model is not given.
    if args.model is None:
        if args.provider == "muse":
            args.model = "muse-spark-1.2-contributor"
        else:
            args.model = "gemini-3.6-flash"

    # --- load template & cases --------------------------------------------
    template_path = pathlib.Path(args.prompt_file)
    if not template_path.is_file():
        print(f"ERROR: prompt file not found: {template_path}", file=sys.stderr)
        sys.exit(1)
    template = template_path.read_text()

    cases_path = pathlib.Path(args.cases)
    if not cases_path.is_file():
        print(f"ERROR: cases file not found: {cases_path}", file=sys.stderr)
        sys.exit(1)
    cases: List[Dict[str, Any]] = json.loads(cases_path.read_text())

    args.api_key = _resolve_api_key(args.provider)

    # --- single-case debug mode -------------------------------------------
    if args.show is not None:
        idx = args.show - 1
        if idx < 0 or idx >= len(cases):
            print(
                f"ERROR: --show {args.show} out of range (1..{len(cases)})",
                file=sys.stderr,
            )
            sys.exit(1)
        case = cases[idx]
        prompt = _render_template(template, case["ocr"], case["transcript"])
        raw = _call_model(prompt, args)
        print(f"=== Raw output for case {args.show}: {case['name']} ===")
        print(raw)
        print()
        print("=== Repaired JSON ===")
        print(repair.repair_json(raw))
        print()
        record = repair.parse_record(raw)
        print("=== Parsed record ===")
        print(json.dumps(record, indent=2, ensure_ascii=False))
        return

    # --- full run ---------------------------------------------------------
    passed = 0
    total = len(cases)
    latencies: List[float] = []
    report_cases: List[Dict[str, Any]] = []

    # Header
    print(f"{'#':>3}  {'Case':<32} {'Time':>7}  {'Result':<6}  Details")
    print("-" * 90)

    for i, case in enumerate(cases, 1):
        name = case["name"]
        prompt = _render_template(template, case["ocr"], case["transcript"])

        t0 = time.monotonic()
        raw = _call_model(prompt, args)
        elapsed = time.monotonic() - t0
        latencies.append(elapsed)

        record = repair.parse_record(raw)
        ok, reasons = _score_case(record, case["expect"])

        status = "PASS" if ok else "FAIL"
        detail = "; ".join(reasons) if reasons else ""
        print(f"{i:>3}  {name:<32} {elapsed:>6.2f}s  {status:<6}  {detail}")

        if ok:
            passed += 1

        report_cases.append({
            "name": name,
            "seconds": elapsed,
            "passed": ok,
            "detail": detail,
            "raw": raw,
        })

    # Summary
    print("-" * 90)
    mean_lat = sum(latencies) / len(latencies) if latencies else 0.0
    print(
        f"Result: {passed}/{total} passed  |  "
        f"mean latency {mean_lat:.2f}s  |  "
        f"provider {args.provider}  |  "
        f"model {args.model}  |  "
        f"prompt {template_path.name}"
    )

    # --out report
    if args.out is not None:
        report = {
            "provider": args.provider,
            "model": args.model,
            "prompt_file": str(template_path),
            "passed": passed,
            "total": total,
            "mean_latency": mean_lat,
            "cases": report_cases,
        }
        out_path = pathlib.Path(args.out)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")

    sys.exit(0 if passed == total else 1)


if __name__ == "__main__":
    main()
```

