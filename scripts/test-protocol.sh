#!/usr/bin/env sh
set -eu
repo=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
classes="$repo/build/protocol-tests/classes"
source="$repo/app/src/main/java/dev/wristlink"
mkdir -p "$classes"
javac --release 17 -d "$classes" \
  "$source/AncsParser.java" "$source/AncsAppParser.java" \
  "$source/ActionPolicy.java" "$source/DismissalKey.java" \
  "$repo/tests/AncsParserTest.java" "$repo/tests/AncsAppParserTest.java" \
  "$repo/tests/NotificationBehaviorTest.java"
for test in AncsParserTest AncsAppParserTest NotificationBehaviorTest; do
  java -cp "$classes" "$test"
done
