#!/bin/sh
# Opens the Mac web editor at http://localhost:5173 (Google sign-in only allows localhost or the Firebase domains).
cd "$(dirname "$0")/../web" || exit 1
( sleep 1; open http://localhost:5173 ) &
exec python3 -m http.server 5173 --bind 127.0.0.1
