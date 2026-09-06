#!/usr/bin/env bash
# Verify everything built in the offline-pipeline-rework branch, on a real device.
#
#   ./scripts/verify-session.sh
#
# Checks, in the order that matters most if something is broken:
#   1. LiteRT-LM is actually the live engine, and how fast it is
#   2. Whisper ASR still transcribes reference audio correctly
#   3. DueDateResolver stops inventing dates
#   4. The screen-capture service arms and grabs a frame
# Everything is read back from the device's own database or logcat, never assumed.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$HOME/Claude/iqoo-hackathon/env.sh"
PKG=com.smriti.app
D="${1:-$(adb devices | awk 'NR==2{print $1}')}"
[ -n "$D" ] || { echo "no device"; exit 1; }
sql() { adb -s "$D" shell "run-as $PKG sqlite3 databases/smriti.db \"$1\"" 2>/dev/null | tr -d '\r'; }
step() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
ok()   { printf '\033[32mok\033[0m   %s\n' "$*"; }
bad()  { printf '\033[31mFAIL\033[0m %s\n' "$*"; }

step "device"
adb -s "$D" shell getprop ro.product.model | tr -d '\r'
adb -s "$D" shell getprop ro.soc.model | tr -d '\r'

step "1. LLM engine — which one is live, and how fast"
adb -s "$D" shell am force-stop $PKG; adb -s "$D" logcat -c
adb -s "$D" shell am start -n $PKG/.MainActivity --ez smriti_selftest true >/dev/null 2>&1
for i in $(seq 1 60); do
  sleep 5
  adb -s "$D" logcat -d -s SmritiBench:V 2>/dev/null | grep -q "SELF TEST END" && break
  adb -s "$D" logcat -d -b crash 2>/dev/null | grep -q "Fatal signal" && { bad "native crash"; break; }
done
adb -s "$D" logcat -d -s SmritiEngine:V -v raw 2>/dev/null | sed 's/^/   /'
adb -s "$D" logcat -d -s SmritiBench:V -v raw 2>/dev/null | grep -E "^backend:|^load:|^generate:|tokens/sec|^extract:|^actions:|^   -" | sed 's/^/   /'
BK=$(adb -s "$D" logcat -d -s SmritiBench:V -v raw 2>/dev/null | grep "^backend:" | head -1)
case "$BK" in
  *"quarantined"*) bad "MediaPipe fallback is live, NOT LiteRT-LM" ;;
  *) ok "engine label: $BK" ;;
esac

step "2. Whisper ASR against reference audio"
adb -s "$D" shell am force-stop $PKG; adb -s "$D" logcat -c
adb -s "$D" shell am start -n $PKG/.MainActivity --ez smriti_asrtest true >/dev/null 2>&1
for i in $(seq 1 30); do sleep 4; adb -s "$D" logcat -d -s SmritiAsr:V 2>/dev/null | grep -q "ASR SELF TEST END" && break; done
adb -s "$D" logcat -d -s SmritiAsr:V -v raw 2>/dev/null | sed 's/^/   /'

step "3. Due dates — must not be invented"
RID=$(sql "SELECT id FROM records ORDER BY id DESC LIMIT 1;")
sql "DELETE FROM tasks WHERE recordId=$RID;"
sql "UPDATE records SET transcript='Rohit ships the API by Friday and we need two hundred more units from Sharma Traders', enrichmentState='PENDING', enrichmentAttempts=0 WHERE id=$RID;"
adb -s "$D" shell am force-stop $PKG; adb -s "$D" logcat -c
adb -s "$D" shell am start -n $PKG/.MainActivity >/dev/null 2>&1
for i in $(seq 1 60); do
  sleep 5
  [ "$(sql "SELECT enrichmentState FROM records WHERE id=$RID;")" = "DONE" ] && break
done
adb -s "$D" logcat -d -s SmritiEnrich:V -v raw 2>/dev/null | sed 's/^/   /'
echo "   tasks:"
sql "SELECT '     ' || text || '  due=' || COALESCE(date(dueDateMillis/1000,'unixepoch','localtime'),'NULL') FROM tasks WHERE recordId=$RID;"
echo "   (expect: 'ships the API' due=2026-09-11 Friday; the units task due=NULL)"

step "4. screen capture service"
adb -s "$D" logcat -c
adb -s "$D" shell am start -n $PKG/.MainActivity --ez smriti_screencap true >/dev/null 2>&1
sleep 4
adb -s "$D" exec-out screencap -p > "$ROOT/notes/consent-dialog.png" 2>/dev/null
echo "   consent dialog screenshot -> notes/consent-dialog.png (accept it on the phone, then re-run with --ez smriti_screengrab true)"
adb -s "$D" logcat -d -s SmritiScreenCap:V -v raw 2>/dev/null | sed 's/^/   /'

step "crash check"
C=$(adb -s "$D" logcat -d -b crash 2>/dev/null | grep -c "Fatal signal")
[ "$C" -eq 0 ] && ok "no native crashes" || bad "$C fatal signals"
