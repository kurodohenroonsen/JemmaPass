#!/usr/bin/env bash
set -eu
for run in "$@"; do
  [ -d "$run" ] || continue
  find "$run" -mindepth 1 -maxdepth 1 ! -name report.md ! -name screenshots -exec rm -rf {} +
  echo "pruned $run -> $(find "$run" -type f | wc -l | tr -d ' ') files"
done
