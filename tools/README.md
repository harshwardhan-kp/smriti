# Smriti Prompt Iteration Harness

> **⚠️ Laptop-side development tool only.**
> This harness must **never** be imported, bundled, or referenced by the
> Android app.  Smriti has no `INTERNET` permission and must keep it that way.

## What this is

Smriti runs a small local LLM (Gemma 3 1B or Qwen2.5 0.5B) on-device via
MediaPipe to convert a photo's OCR text plus a voice transcript into a
structured JSON record.  A single generation takes **12–60 seconds** on the
test handset, making prompt iteration on-device impractical.

This harness lets you iterate the prompt on your laptop against the
**Gemini API** in seconds.  Once a prompt variant scores well here, paste it
into the Android app and do a single confirmation run on-device.

## Prerequisites

* Python 3.9+
* A Gemini API key, provided via **either**:
  * `GEMINI_API_KEY` environment variable, **or**
  * `~/.config/gemini-key` (plain text, one line).

No `pip install` needed — the harness uses only the Python standard library.

## Quick start

## Measured reality, 1 September 2026

Ran both prompts against `gemini-3.6-flash` with the key in `~/.config/gemini-key`:

| prompt | result | mean latency |
|---|---|---|
| v1 (production schema prompt) | **8/8 passed** | 35.4 s |
| v2 (few-shot, built for a 1B model) | 5/5 before rate limiting | 47.7 s |

Two findings that matter more than the pass rates:

**1. The API is not faster than the phone right now.** 35-96 s per call against
`gemini-3.6-flash`, with repeated 503s and a 429 carrying `retryDelay: 41s`. On-device Gemma 3
1B generates in 12.2 s and Qwen2.5 0.5B in 5.3 s. The premise that a cloud API would speed up
prompt iteration does not hold under current load.

**2. A frontier model cannot tell you which prompt suits a 1B model.** Both v1 and v2 pass
everything, because Gemini follows any reasonable schema instruction. The thing we actually
need to discriminate — whether a 1B model honours `actions` vs inventing `actionItems` — is
invisible here.

**So iterate prompts on the small LOCAL model instead.** Qwen2.5-0.5B q8 at ~5 s per
generation is the fastest discriminating loop available, and it fails in the same ways Gemma
does. Confirm the winner once on Gemma before shipping.

This harness keeps its value as a regression check on `repair.py` / `Extractor.parseJson`:
it exercises the repair path against many real model outputs cheaply. That is what it should
be used for.
