# Road preset editor

The road preview remains visible while editing. Its width labels are in blocks;
arrows show lane travel direction. Material palettes are shown as a stable sample
of their blocks; the exact mixture in a built road depends on world coordinates.

- **Layout:** click or drag an item from **Add parts** to insert it. Click the
  preview to open **Selection**, where you can set width, direction, material
  overrides and component order. Shift-click selects several components for
  removal. A road must retain at least one vehicle or bus lane and stay within
  the 64-block width limit.
- **Materials:** edit the shared pavement, marking, sidewalk, grass and concrete
  palettes. Each palette supports up to 16 blocks. Every block can be replaced
  or removed individually; the first block is the base material. Components
  using a road material follow its palette. Editing a component's palette creates
  an override; **Use road material** restores inheritance.
- **Junctions:** choose crosswalk defaults, free/yield/stop control, pavement,
  lane markings, crosswalk material and stop-line material. Optional materials
  can be cleared. **Copy road materials to junctions** copies pavement and
  markings while preserving traffic control, crosswalk preference and stop lines.
  Ordinary layout and palette edits preserve the existing junction settings.
- **Details:** edit the name, category, road class, priority and nominal speed.
  Higher priority wins when junctions are formed. Speed accepts decimal values
  from 0 to 500. Reset restores the saved definition; deleting a saved preset
  requires a second confirmation and does not remove placed roads.
- **Saved roads:** search and switch presets, or open **Create Road Pack**.

**Save Road** and **Duplicate / Use as Template** stay in the footer. Built-in
presets remain read-only until copied. Unsaved changes are marked with an asterisk;
switching presets or closing the editor requires confirmation before discarding
them. Hover over a status message to read its full text.

Controls remain available at the minimum 320×240 scaled GUI size. Scroll with
the mouse wheel, arrow buttons, draggable scrollbar, or Page Up / Page Down.
Ctrl+S saves; Delete removes the selected components in Layout when a text field
does not have focus. Escape cancels a drag before closing the editor.

## Verification

Run `gradlew.bat test` (or `./gradlew test`) with a supported Gradle JVM. Editor
tests cover responsive geometry, small-screen scrolling, material inheritance,
classification/junction round trips, drag geometry, selection and request state.
