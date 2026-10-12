#!/usr/bin/env bash
# Run from the ROOT of a fresh clone of MetrolistGroup/Metrolist:
#   git clone https://github.com/MetrolistGroup/Metrolist HoneyBeat && cd HoneyBeat
#   bash rename_to_honeybeat.sh
# Metrolist is GPL-3.0: LICENSE stays untouched and the source must stay public.
set -euo pipefail

NEW_PKG="com.neonbear.honeybeat"

echo "==> 1/4 Replacing text inside files"
git ls-files -z | while IFS= read -r -d '' f; do
  case "$f" in LICENSE|README.md|changelog.md) continue ;; esac
  [ -f "$f" ] || continue
  grep -Iq . "$f" 2>/dev/null || continue            # skip binaries / empty files
  perl -pi -e '
    # protect upstream services the app talks to (listen-together, update links, org)
    s{github\.com/MetrolistGroup}{__KEEP_GH__}g;
    s{metrolist\.cc}{__KEEP_CC__}g;
    s{metroserver}{__KEEP_MS__}g;
    s{MetrolistGroup}{__KEEP_MG__}g;
    # the actual rename
    s{com\.metrolist}{'"$NEW_PKG"'}g;
    s{Metrolist}{HoneyBeat}g;
    s{METROLIST}{HONEYBEAT}g;
    s{metrolist}{honeybeat}g;
    # restore protected strings
    s{__KEEP_GH__}{github.com/MetrolistGroup}g;
    s{__KEEP_CC__}{metrolist.cc}g;
    s{__KEEP_MS__}{metroserver}g;
    s{__KEEP_MG__}{MetrolistGroup}g;
  ' "$f"
done

echo "==> 2/4 Moving package folders com/metrolist -> com/neonbear/honeybeat"
find . -type d -path '*/com/metrolist' -not -path './.git/*' -prune | while read -r d; do
  parent="$(dirname "$d")"                            # .../com
  mkdir -p "$parent/neonbear"
  git mv "$d" "$parent/neonbear/honeybeat"
done

echo "==> 3/4 Renaming files and folders that still have 'metrolist' in the name"
find . -depth -iname '*metrolist*' -not -path './.git/*' -not -name LICENSE | while read -r p; do
  dir="$(dirname "$p")"; base="$(basename "$p")"
  new="$(echo "$base" | sed -e 's/Metrolist/HoneyBeat/g' -e 's/metrolist/honeybeat/g' -e 's/METROLIST/HONEYBEAT/g')"
  [ "$base" != "$new" ] && git mv "$p" "$dir/$new"
done

echo "==> 4/4 Attribution notice"
cat > NOTICE.md <<'EOF'
# HoneyBeat

HoneyBeat by NeonBear is based on [Metrolist](https://github.com/MetrolistGroup/Metrolist)
by Mo Agamy and contributors, licensed under GPL-3.0. This project is distributed under
the same license (see LICENSE). Not affiliated with YouTube, Google or Metrolist Group.
EOF

echo
echo "Done. Next:"
echo "  1. Check app/build.gradle.kts -> applicationId (should be $NEW_PKG or $NEW_PKG.music)"
echo "  2. Point the in-app update checker at YOUR repo (grep -r MetrolistGroup app/src)"
echo "  3. Add the UI file, commit, push, build in GitHub Actions"
