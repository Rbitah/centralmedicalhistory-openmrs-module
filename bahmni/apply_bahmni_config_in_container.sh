#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "Usage: $0 <bahmni-config-container-name> [apps-path-in-container]"
  echo "Example: $0 bahmni-lite-bahmni-config-1 /usr/local/bahmni_config/openmrs/apps"
  exit 1
fi

CONTAINER="$1"
PRIMARY_APPS_PATH="${2:-/usr/local/bahmni_config/openmrs/apps}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MODULE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

cp "$MODULE_ROOT/api/src/main/resources/bahmni/customDisplayControl/js/nationalHistoryDashboard.js" "$TMP_DIR/nationalHistoryDashboard.js"
cp "$MODULE_ROOT/api/src/main/resources/bahmni/customDisplayControl/views/nationalHistoryDashboard.html" "$TMP_DIR/nationalHistoryDashboard.html"

patch_one_path() {
  local APPS_PATH="$1"
  local SAFE_NAME
  SAFE_NAME="$(echo "$APPS_PATH" | sed 's#[/ ]#_#g')"
  local CUSTOM_JS_LOCAL="$TMP_DIR/customControl${SAFE_NAME}.js"
  local DASHBOARD_LOCAL="$TMP_DIR/dashboard${SAFE_NAME}.json"

  if ! docker exec "$CONTAINER" sh -lc "[ -f \"$APPS_PATH/customDisplayControl/js/customControl.js\" ] && [ -f \"$APPS_PATH/clinical/dashboard.json\" ]"; then
    echo "Skipping missing apps path in container: $APPS_PATH"
    return 0
  fi

  docker cp "$CONTAINER:$APPS_PATH/customDisplayControl/js/customControl.js" "$CUSTOM_JS_LOCAL"
  docker cp "$CONTAINER:$APPS_PATH/clinical/dashboard.json" "$DASHBOARD_LOCAL"

python3 - "$CUSTOM_JS_LOCAL" "$TMP_DIR/nationalHistoryDashboard.js" "$DASHBOARD_LOCAL" <<'PY'
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

  python3 -m json.tool "$DASHBOARD_LOCAL" >/dev/null

  docker cp "$TMP_DIR/nationalHistoryDashboard.js" "$CONTAINER:$APPS_PATH/customDisplayControl/js/nationalHistoryDashboard.js"
  docker cp "$TMP_DIR/nationalHistoryDashboard.html" "$CONTAINER:$APPS_PATH/customDisplayControl/views/nationalHistoryDashboard.html"
  docker cp "$CUSTOM_JS_LOCAL" "$CONTAINER:$APPS_PATH/customDisplayControl/js/customControl.js"
  docker cp "$DASHBOARD_LOCAL" "$CONTAINER:$APPS_PATH/clinical/dashboard.json"

  echo "Patched apps path: $APPS_PATH"
}

patch_one_path "$PRIMARY_APPS_PATH"
if [ "$PRIMARY_APPS_PATH" != "/etc/bahmni_config/openmrs/apps" ]; then
  patch_one_path "/etc/bahmni_config/openmrs/apps"
fi
if [ "$PRIMARY_APPS_PATH" != "/usr/local/bahmni_config/openmrs/apps" ]; then
  patch_one_path "/usr/local/bahmni_config/openmrs/apps"
fi

echo "Applied National History Bahmni config in container '$CONTAINER' (including restart source path when present)."
