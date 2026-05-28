#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <path-to-bahmni_config/openmrs/apps>"
  exit 1
fi

TARGET_APPS_DIR="$1"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MODULE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ASSET_JS="$MODULE_ROOT/api/src/main/resources/bahmni/customDisplayControl/js/nationalHistoryDashboard.js"
ASSET_HTML="$MODULE_ROOT/api/src/main/resources/bahmni/customDisplayControl/views/nationalHistoryDashboard.html"

CUSTOM_JS="$TARGET_APPS_DIR/customDisplayControl/js/customControl.js"
DASHBOARD_JSON="$TARGET_APPS_DIR/clinical/dashboard.json"
TARGET_JS="$TARGET_APPS_DIR/customDisplayControl/js/nationalHistoryDashboard.js"
TARGET_HTML="$TARGET_APPS_DIR/customDisplayControl/views/nationalHistoryDashboard.html"

for required in "$ASSET_JS" "$ASSET_HTML" "$CUSTOM_JS" "$DASHBOARD_JSON"; do
  if [ ! -f "$required" ]; then
    echo "Missing required file: $required"
    exit 2
  fi
done

mkdir -p "$(dirname "$TARGET_JS")" "$(dirname "$TARGET_HTML")"
cp -f "$ASSET_JS" "$TARGET_JS"
cp -f "$ASSET_HTML" "$TARGET_HTML"

python3 - "$CUSTOM_JS" "$TARGET_JS" "$DASHBOARD_JSON" <<'PY'
import sys
import re
from pathlib import Path

custom_js = Path(sys.argv[1])
directive_js = Path(sys.argv[2])
dashboard_json = Path(sys.argv[3])

custom_text = custom_js.read_text()
pattern = r"angular\.module\('bahmni\.common\.displaycontrol\.custom'\)\s*\.directive\('nationalHistoryDashboard'[\s\S]*?\}\]\);"
custom_text = re.sub(pattern, "", custom_text, flags=re.MULTILINE)
custom_text = custom_text.rstrip() + "\n\n" + directive_js.read_text().rstrip() + "\n"
custom_js.write_text(custom_text)

content = dashboard_json.read_text()
if '"nationalHistory"' not in content:
    section = (
        '            "nationalHistory": {\n'
        '                "type": "custom",\n'
        '                "displayOrder": 9,\n'
        '                "config": {\n'
        '                    "title": "National Medical History",\n'
        '                    "template": "<national-history-dashboard section=\\"section\\" patient=\\"patient\\"></national-history-dashboard>"\n'
        '                }\n'
        '            },\n'
    )
    idx = content.find('"patientDocument":{')
    if idx < 0:
        idx = content.find('"patientDocument": {')
    if idx >= 0:
        content = content[:idx] + section + content[idx:]
        dashboard_json.write_text(content)
    else:
        raise SystemExit("Could not find patientDocument section in dashboard.json")
PY

python3 -m json.tool "$DASHBOARD_JSON" >/dev/null

echo "Applied National History Bahmni UI config successfully."
echo "Restart bahmni-web, bahmni-apps-frontend, and proxy (or hard refresh browser)."
