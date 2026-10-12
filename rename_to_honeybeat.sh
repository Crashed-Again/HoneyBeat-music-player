#!/usr/bin/env bash
# Run from the ROOT of a clone of MetrolistGroup/Metrolist.
# v2: does NOT rename Kotlin packages/classes (that broke the build: git submodules and
# generated code still use com.metrolist). It renames what users actually see:
# app name, application id, icon. Metrolist is GPL-3.0 - LICENSE stays untouched.
set -euo pipefail

NEW_APPID="com.neonbear.honeybeat.ytm"

echo "==> 1/3 App name in string resources (all languages) and manifest labels"
find app/src -type f -path '*/res/values*/strings*.xml' -print0 | xargs -0 -r perl -pi -e '
  s{github\.com/MetrolistGroup}{__KEEP_GH__}g;
  s{MetrolistGroup}{__KEEP_MG__}g;
  s{metrolist\.cc}{__KEEP_CC__}g;
  s{Metrolist}{HoneyBeat}g;
  s{__KEEP_GH__}{github.com/MetrolistGroup}g;
  s{__KEEP_MG__}{MetrolistGroup}g;
  s{__KEEP_CC__}{metrolist.cc}g;
'
find app/src -type f -name 'AndroidManifest.xml' -print0 | xargs -0 -r perl -pi -e \
  's{(android:label="[^"]*)Metrolist}{$1HoneyBeat}g'

echo "==> 2/3 Application id -> $NEW_APPID"
perl -pi -e 's{(applicationId\s*=\s*)"[^"]*"}{$1"'"$NEW_APPID"'"}' app/build.gradle.kts
grep -n 'applicationId' app/build.gradle.kts || echo "WARNING: no applicationId line found"

echo "==> 3/3 Attribution notice"
cat > NOTICE.md <<'EON'
# HoneyBeat

HoneyBeat by NeonBear is based on [Metrolist](https://github.com/MetrolistGroup/Metrolist)
by Mo Agamy and contributors, licensed under GPL-3.0. This project is distributed under
the same license (see LICENSE). Not affiliated with YouTube, Google or Metrolist Group.
EON
echo "Done."
