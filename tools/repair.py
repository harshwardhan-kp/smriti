"""
repair.py – reference implementation of Smriti's JSON repair-and-parse pipeline.

This file is the AUTHORITATIVE specification.  The Kotlin code in
app/.../Extractor.kt must produce identical behaviour for every input.
If the two diverge, this file wins and the Kotlin must be updated.

Exposed API
-----------
  repair_json(raw: str) -> str
      Best-effort cleanup of an LLM response into parseable JSON.

  parse_record(raw: str) -> dict | None
      Full pipeline: repair → parse → normalise.  Returns None when the
      output has no title, no summary, and no actions (i.e. nothing useful).
"""

from __future__ import annotations

import json
import re
from typing import Any, Dict, List, Optional


# ---------------------------------------------------------------------------
# repair_json
# ---------------------------------------------------------------------------

def repair_json(raw: str) -> str:
    """Best-effort cleanup of an LLM JSON response into something json.loads
    can handle.

    Steps (order matters):
      1. Strip markdown ```json … ``` fences.
      2. Slice from the first '{' to the last '}'.
      3. Remove trailing commas before '}' or ']'.
    """
    text = raw.strip()

    # 1. Strip markdown fences
    text = re.sub(r"```json\s*", "", text)
    text = re.sub(r"```\s*", "", text)
    text = text.strip()

    # 2. Slice to outermost braces
    first = text.find("{")
    last = text.rfind("}")
    if first == -1 or last == -1 or last < first:
        return text  # nothing we can do; let the caller fail gracefully
    text = text[first : last + 1]

    # 3. Trailing commas  (e.g.  {"a":1,}  or  [1,2,] )
    text = re.sub(r",\s*([}\]])", r"\1", text)

    return text


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------

_TITLE_ALIASES: List[str] = ["title", "heading", "name"]
_SUMMARY_ALIASES: List[str] = ["summary", "note", "description"]
_PEOPLE_ALIASES: List[str] = ["people", "persons", "names", "assignees"]
_TAGS_ALIASES: List[str] = ["tags", "labels", "topics"]
_AMOUNTS_ALIASES: List[str] = ["amounts", "quantities", "figures"]
_ACTIONS_ALIASES: List[str] = [
    "actions",
    "actionItems",
    "action_items",
    "tasks",
    "todos",
]

_ACTION_TEXT_ALIASES: List[str] = [
    "text",
    "task",
    "action",
    "item",
    "description",
]
_ACTION_DUE_ALIASES: List[str] = [
    "due",
    "dueDate",
    "due_date",
    "date",
    "deadline",
]


def _pick(src: dict, aliases: List[str], default: Any = None) -> Any:
    """Return the value for the first alias found in *src*, else *default*."""
    for alias in aliases:
        if alias in src:
            return src[alias]
    return default


def _normalise_action(item: Any) -> Optional[Dict[str, Any]]:
    """Normalise a single action entry.

    Accepts either a dict with alias keys or a plain string.
    """
    if isinstance(item, str):
        return {"text": item, "due": None}
    if isinstance(item, dict):
        text = _pick(item, _ACTION_TEXT_ALIASES, "")
        due = _pick(item, _ACTION_DUE_ALIASES)
        if isinstance(due, str) and due.lower() in ("", "null", "none"):
            due = None
        return {"text": str(text), "due": due}
    return None


def _normalise_amount(item: Any) -> Optional[Dict[str, Any]]:
    """Normalise a single amount entry."""
    if isinstance(item, dict):
        value = item.get("value", 0)
        currency = item.get("currency", "INR")
        label = item.get("label", "")
        return {"value": value, "currency": str(currency), "label": str(label)}
    return None


def _first_sentence(text: str) -> str:
    """Return the first sentence (up to the first full stop, question mark,
    exclamation mark, or newline), capped at roughly 8 words."""
    # Split on sentence-ending punctuation or newline.
    m = re.split(r"[.\n!?]", text, maxsplit=1)
    sentence = m[0].strip() if m else text.strip()
    words = sentence.split()
    return " ".join(words[:8])


# ---------------------------------------------------------------------------
# parse_record
# ---------------------------------------------------------------------------

def parse_record(raw: str) -> Optional[Dict[str, Any]]:
    """Full pipeline: repair → JSON parse → normalise into canonical schema.

    Returns ``None`` when the output contains no title, no summary, and no
    actions – i.e. the model could not extract anything useful.

    Canonical schema::

        {
            "title":   str,
            "summary": str,
            "people":  [str, ...],
            "amounts": [{"value": number, "currency": str, "label": str}, ...],
            "tags":    [str, ...],
            "actions": [{"text": str, "due": str|None}, ...]
        }
    """
    repaired = repair_json(raw)

    try:
        data = json.loads(repaired)
    except json.JSONDecodeError:
        return None

    if not isinstance(data, dict):
        return None

    # --- pull with aliases ------------------------------------------------
    title: str = str(_pick(data, _TITLE_ALIASES, "") or "")
    summary: str = str(_pick(data, _SUMMARY_ALIASES, "") or "")
    people: list = list(_pick(data, _PEOPLE_ALIASES, []) or [])
    tags: list = list(_pick(data, _TAGS_ALIASES, []) or [])

    raw_amounts = _pick(data, _AMOUNTS_ALIASES, []) or []
    amounts: List[Dict[str, Any]] = []
    if isinstance(raw_amounts, list):
        for a in raw_amounts:
            norm = _normalise_amount(a)
            if norm is not None:
                amounts.append(norm)

    raw_actions = _pick(data, _ACTIONS_ALIASES, []) or []
    actions: List[Dict[str, Any]] = []
    if isinstance(raw_actions, list):
        for a in raw_actions:
            norm = _normalise_action(a)
            if norm is not None:
                actions.append(norm)

    # --- derive title from summary when missing ---------------------------
    if not title.strip() and summary.strip():
        title = _first_sentence(summary)

    # --- reject empty output ----------------------------------------------
    if not title.strip() and not summary.strip() and not actions:
        return None

    return {
        "title": title,
        "summary": summary,
        "people": [str(p) for p in people],
        "amounts": amounts,
        "tags": [str(t) for t in tags],
        "actions": actions,
    }