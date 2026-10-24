#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 ci/validate_workflow.py
