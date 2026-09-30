# To do

Bugs and polish that don't belong to a milestone yet. The roadmap lives in README.md.

## Bugs and polish

- **Exercise picker: fast scrolling stutters, and photos seem to load again** (reported 2026-09-30 on
  the Fold 7). Scrolling the list of exercises quickly, e.g. when adding one to a workout, isn't smooth
  any more. Thumbnails look like they're loaded again each time a row comes back into view. Places to
  look:
  - Measure first: recomposition counts in the Layout Inspector, and a trace of a fast fling.
  - Photos: whether Coil's memory cache hits when rows scroll back (the app uses Coil's default
    loader), and whether thumbnails decode at their small size or at the photo's full size.
  - Stability: `Exercise` comes from `core:model`, which has no Compose compiler, so rows that take one
    can't skip recomposition. A Compose stability config for `app.liora.core.model` may fix that.
    `ExerciseThumbnail` moved from the exercises feature to `core:ui` in `deca2e8`.
  - The lazy list: stable keys and a `contentType` for rows versus headers.
- **Workout notification: the header countdown runs 1–2 s behind the title.** Android ticks the header
  itself and rounds down; the title ("Rest 1:23") rounds up and updates twice a second. Make them agree,
  or drop the time from the title.
- **Save as routine: check the name dialog on a device.** UI tests can't drive it (see the Robolectric
  gotcha in CLAUDE.md); saving itself is covered in `core:data`.
- **Robolectric: a text field in a dialog never lets Compose go idle.** Find the cause, so the rename
  and save-as-routine dialogs can get UI tests.
