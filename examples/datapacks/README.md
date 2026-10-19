# Example datapacks

Copy a folder into a world's `datapacks/` directory (for a dev world, `run/saves/<world>/datapacks/`), then run `/reload`.
Scripts load when a game opens (`/pap join`). `/pap scripts reload` picks up edits for a game that's already running.

- `pap_example` shows a title when a round ends, says a line every round from round 3 on, and plays a sound on headshot kills. It uses `require`, a module that returns functions, `events.on`, and an EventDef with a round filter.
- `pap_sandbox_check` checks the sandbox: every line it logs should say "blocked". Its runaway `round_start` listener must be stopped and reported while the server keeps running.
