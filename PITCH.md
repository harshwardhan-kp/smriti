# Smriti — the three-minute pitch

Demo and presentation is 10% of the score, but the demo is also where the jury forms their
opinion of the 30% "end product quality" line. Treat this as a performance with a script, not
a walkthrough. Rehearse it at least four times, twice on the actual loaner phone.

**One person talks. One person drives the phone. Never the same person.** The talker faces the
jury; the driver never looks up. This is the single biggest difference between a demo that
lands and one that mumbles at a screen.

---

## The script

### 0:00 — 0:25 · The problem, in one breath

> "Every productivity tool you have ever used assumes three things: a desk, a keyboard, and a
> network connection.
>
> Most of the work in this country has none of them. A site engineer logging a cracked beam in
> a basement. A shop owner with twenty invoices and one hand free. A lab technician reading a
> machine plate into a notebook nobody can search.
>
> For all of them, the phone is the only computer that's actually there. And every AI notes app
> on it ships their work to somebody else's server."

*Driver: app open on the capture screen, camera live. Nothing tapped yet.*

### 0:25 — 0:40 · Airplane mode, visibly

> "So before I show you anything — watch this."

*Driver: pull down the shade, tap airplane mode, hold the phone up so the jury sees the icon.
Count two full seconds. Say nothing during those two seconds.*

> "That stays on for the rest of the demo."

### 0:40 — 1:40 · Three captures

*First capture — a whiteboard covered in messy handwriting.*

> "One button. The camera sees what's in front of me, and I say one line about it."

*Driver: press and hold, speak clearly:*
> **"Rohit ships the API by Friday, and we need two hundred more units."**

*Release. Wait. The card appears.*

> "Four seconds. A title. Two tasks — one of them assigned, with a date. A quantity. Tags.
> I didn't type a character, and nothing left the phone."

*Second capture — a printed invoice or delivery challan.*

> "It doesn't care what it's looking at."

*Driver: capture, say:* **"Delivery from Sharma Traders, checked against the order."**

> "It read the printed text off the paper. That's on-device OCR — and it handles Devanagari,
> because half the paperwork in a real Indian workplace is bilingual."

*Third capture — fast, no narration, just to show speed.*

### 1:40 — 2:20 · The part that matters

> "Capturing is the easy half. Here's the half nobody does offline."

*Driver: switch to Ask. Hold the mic button.*
> **"What did I commit to this week?"**

*Answer appears with the source photograph beneath it.*

> "It answered in my own words — and it showed me the photograph it got that from. You are not
> being asked to trust a summary. You can see the evidence.
>
> That's a language model, an embedding model, OCR and speech recognition, all running on this
> handset, still in airplane mode."

### 2:20 — 2:45 · Architecture, one breath, no slides

> "Gemma 3 1B, four-bit, five hundred and fifty megabytes, through MediaPipe's LLM Inference
> API on the GPU backend. ML Kit for text. On-device speech. Embeddings and cosine similarity
> in Kotlin — no vector database, because at a few hundred records it would be a dependency we
> spent the night debugging for nothing.
>
> And the app declares no internet permission at all. Not 'we chose not to call the network' —
> it cannot."

**If asked about the NPU, answer this exactly:**

> "We're on the GPU, not the NPU. MediaPipe's backend enum is CPU and GPU — there's no NPU
> option in it. The real Hexagon path is LiteRT-LM with an AOT-compiled per-SoC model, and
> Google publishes one for SM8850, which is this phone. We scoped it and kept it off the
> critical path for thirty hours."

*Do not claim NPU. A jury with iQOO engineers on it will know, and the honest answer is the
stronger one.*

### 2:45 — 3:00 · Close

> "No account. No server. No signal. The work stays on the device it was captured on, because
> for the person doing that work, that's the only version worth having.
>
> It's still in airplane mode. Have a look."

*Driver: hand the phone to the nearest judge, unlocked, on the timeline screen.*

---

## Contingencies — decide these before you walk up, not during

| If | Then |
|---|---|
| The model is slow to first token | Keep talking through it. The script has ~15 s of slack at 0:40 and 1:40. Never watch the screen in silence. |
| A capture returns a bad title | Say "the model got that one wrong, here's the raw capture" and open the detail view showing photo and transcript. Owning it beats pretending. Never re-run the same capture twice in front of a jury. |
| Speech recognition fails | Fall back to typing the query on the Ask screen. Say "no offline voice pack on this build" — it's a real constraint, not an excuse. |
| The app crashes | Relaunch and go straight to the recall demo with seeded records. That's the differentiated half anyway. |
| You are cut off at 2:00 | The airplane-mode reveal and one capture are non-negotiable. Everything else is expendable. |

## Rehearsal checklist

- [ ] Seed 8–10 realistic records the night before, so recall has something to search
- [ ] Screen brightness at maximum; auto-rotate off; notifications silenced
- [ ] The demo whiteboard and invoice printed and in your bag, not improvised at the venue
- [ ] Both people can deliver both roles, in case one is unwell
- [ ] Timed twice with a stopwatch. If it runs over 3:00, cut the third capture, not the recall.
