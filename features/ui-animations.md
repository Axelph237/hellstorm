# UI animations

## Goal

Smooth, CSS-like motion for Hellstorm's screens and HUD (hover fades, slide-ins, pulses, values that ease to a new
number) without a UI library dependency. The libraries that do this on Forge 1.20.1 are unofficial ports or past end of
life (researched 2026-10-30: LDLib2, Modern UI, Elementa, owo-lib), and the UI is small.

## Data shape

Everything in `client/anim`, pure but for its clock, so it's tested without the game:

- `Easing`: a CSS timing function, `t -> eased t`. `LINEAR`, `EASE`, `EASE_IN`, `EASE_OUT`, `EASE_IN_OUT` (CSS's
  keyword curves), `cubicBezier(x1, y1, x2, y2)` and `steps(n)` (`jump-end`).
- `Interpolator<T>`: how a value blends, `FLOAT` and `ARGB` (each channel on its own).
- `Transition<T>`: a CSS `transition` on one value. `set(target)` eases from the value now to the target over its
  duration, after its delay; a change midway starts from where it is. `snap` jumps.
- `Keyframes<T>`: a CSS `@keyframes` rule, values at offsets 0 to 1, each with an optional timing function for the
  stretch after it. A missing 0 or 1 frame takes the base value, as CSS takes the element's own.
- `Animation<T>`: a CSS `animation`, a record of keyframes, `duration`, `easing` (default `EASE`), `delay`, `iterations`
  (`INFINITE` too, fractions allowed), `Direction` (`NORMAL`, `REVERSE`, `ALTERNATE`, `ALTERNATE_REVERSE`) and `Fill`
  (`NONE`, `FORWARDS`, `BACKWARDS`, `BOTH`), built with `of` and `with...`. `sample(elapsed, base)`, `finished`, and
  `start()` for a `Running` one that counts from then.
- `UiClock`: the client's own milliseconds (`Util.getMillis`), never the game time, which jumps when the server lags
  (`ClientTimingLintTest`). Every method that needs the time also takes it as an argument.

## As built

- **The library** (2026-10-30). As above. JUnit `client/anim/AnimationTest`: the easings' ends and shapes (CSS's `ease`
  at 50% of its time is 80.2% of the way), steps, transitions (midway changes, delays, the same target not restarting),
  color blending, keyframes with missing ends and their own easing, fill modes, directions, whole and half iterations,
  and a running animation's start.
- **First use: hover.** Buttons and dropdowns (`BoxButton.drawBox`, `Dropdown`) ease into and out of the input box's
  hovered look over 120 ms (`EASE_OUT`) by drawing the normal look with the hovered one over it at the transition's
  value (`UiKit.box(..., hover)`). Pressed and disabled still switch at once.
- **Dropdown rows** (2026-10-30). When a dropdown opens, each row of its list fades in from transparent and slides 6 pixels down into place over 140 ms (`EASE_OUT`), each 30 ms after the one above (`Dropdown.ROW_IN`, sampled at the time since opening less the row's stagger; `Fill.BOTH` holds a row hidden until its turn). The row's own dark backing fades with it, so the ladder shows. Hover and clicks use the rows' final places, so a quick click lands where it was aimed. A row's text waits until the row is 5% in: Minecraft draws text with almost no alpha as fully opaque, which would flash.
- **Not yet used** for the HUD: its panels could slide or fade in, the round number could pulse when it changes, and
  the power-up timer's last-seconds flash (now a hard blink) could be a keyframe `Animation`.

## Depends on

`purchasables-foundation.md` (the settings screens and `client/ui`), `hud-and-prompts.md` (the HUD elements).
