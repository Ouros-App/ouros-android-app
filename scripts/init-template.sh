#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cfg="${1:-$root_dir/template.config.json}"

if [[ ! -f "$cfg" ]]; then
  echo "config not found: $cfg" >&2
  exit 1
fi

get_json() {
  local key="$1"
  sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" "$cfg" | head -n 1
}

esc_perl() {
  printf '%s' "$1" | perl -pe 's/([\\\/&])/\\$1/g'
}

project_name="$(get_json projectName)"
app_label="$(get_json appLabel)"
package_name="$(get_json packageName)"
app_class="$(get_json applicationClassName)"

if [[ -z "$project_name" || -z "$app_label" || -z "$package_name" || -z "$app_class" ]]; then
  echo "missing required values in $cfg" >&2
  exit 1
fi

package_path="${package_name//.//}"
work_dir="${2:-$root_dir/out/$project_name}"

rm -rf "$work_dir"
mkdir -p "$work_dir"
rsync -a \
  --exclude '.git/' \
  --exclude '.idea/' \
  --exclude 'out/' \
  --exclude 'scripts/' \
  --exclude 'template/' \
  --exclude 'template.config.example.json' \
  --exclude 'local.properties' \
  --exclude 'gradle.properties' \
  --exclude 'app/src/main/java/com/mobile_template/' \
  --exclude 'app/src/test/java/com/mobile_template/' \
  --exclude 'app/src/androidTest/java/com/mobile_template/' \
  "$root_dir"/ "$work_dir"/

find "$work_dir" -type f \( -name '*.kt' -o -name '*.kts' -o -name '*.xml' -o -name 'README.md' -o -name '*.json' -o -name 'local.properties.example' \) -print0 | while IFS= read -r -d '' f; do
  perl -0pi -e "s/\{\{PROJECT_NAME\}\}/$(esc_perl "$project_name")/g; s/\{\{APP_LABEL\}\}/$(esc_perl "$app_label")/g; s/\{\{PACKAGE_NAME\}\}/$(esc_perl "$package_name")/g; s/\{\{PACKAGE_PATH\}\}/$(esc_perl "$package_path")/g; s/\{\{APPLICATION_CLASS_NAME\}\}/$(esc_perl "$app_class")/g" "$f"
done

old_pkg_dir="$work_dir/app/src/main/java/{{PACKAGE_PATH}}"
new_pkg_dir="$work_dir/app/src/main/java/$package_path"
if [[ -d "$old_pkg_dir" ]]; then
  mkdir -p "$(dirname "$new_pkg_dir")"
  mv "$old_pkg_dir" "$new_pkg_dir"
fi

old_app="$new_pkg_dir/{{APPLICATION_CLASS_NAME}}.kt"
new_app="$new_pkg_dir/$app_class.kt"
if [[ -f "$old_app" ]]; then
  mv "$old_app" "$new_app"
fi

if grep -R -n '{{' "$work_dir" >/dev/null; then
  echo "unreplaced placeholders remain" >&2
  grep -R -n '{{' "$work_dir" >&2
  exit 1
fi

echo "template initialized at $work_dir"
