# Backwater Refresh

Backwater Refresh is a RuneLite Plugin Hub plugin that customises the CPU-intensive **Backwater Sailing** area in OSRS.

The plugin focuses on visual clarity and performance by hiding or replacing Backwater bubbles and optionally adding tile markers and custom chat messages.

---

## Features

### Bubble graphics

- **Hide surface bubbles**  
  Removes the flat cosmetic Backwater surface bubbles so FPS is improved.

- **Replace fetid pools (3D bubbles)**  
  Replaces 3D bubble “fetid pools” with a static model while keeping their collision intact to further improve FPS.
    - Default model: jungle grass (`model id 4741`)
    - Custom model id support via config

- **Hide fetid pools completely (optional)**  
  Hides all 3D bubble graphics entirely while leaving collision as-is.  
  Useful together with tile markers or for debugging.

- **High‑density vs low‑density markers**
    - High density: one replacement model per tile in the pool’s footprint (2x2, 3x3, etc.)
    - Low density: a single marker at the centre of each pool for better FPS
    - Defaults to High for visual clarity during Jubly Jive

- **Random rotation options**
    - Random rotation per tile, stable per world position
    - Optional grid‑aligned mode (0°, 90°, 180°, 270° only)

### Tile markers

Optional tile overlays for fetid pool footprints:

- **Show tile markers**  
  - Draws tiles where 3D bubbles / fetid pools are located.
  - Defaults to off to prioritize FPS.

- **Show overlap**
    - On: draw full‑size pool footprints so overlapping pools darken where they intersect
    - Off: one 1x1 tile marker per tile covered by any pool (higher FPS cost)

- **Custom colours**
    - Fill colour (with alpha)
    - Border colour (with alpha)
    - Default setting is a 20% opacity purely cosmetic complement to the jungle grass.

### Chat message customisation

Replaces the default Backwater Sailing messages with configurable text:

- Slowdown message when the boat enters the fetid pools
- Protection message while the burst of speed protects the boat from the fetid pools

Defaults keep the same colours as the original messages but change the wording to reference **tanglegrass**.

---

## Configuration

All options live under the **Backwater Refresh** plugin in RuneLite’s configuration panel.

Key settings:

- **Bubble Models**
    - *Hide surface bubbles*
    - *Replace fetid pools*
    - *Custom model ID*
    - *High Density*
    - *Random rotation*
    - *Grid‑aligned rotation*
    - *Hide fetid pools*

- **Chat Messages**
    - *Replace slowdown message*
    - *Slowdown message text*
    - *Replace protection message*
    - *Protection message text*

- **Tile Markers**
    - *Show tile markers*
    - *Show overlap*
    - *Tile fill color*
    - *Tile border color*

---

## Building and running locally

This project uses the official RuneLite external plugin template.

To run it locally from IntelliJ:

1. Open the project as a Gradle project.
2. Run the `BackwaterRefreshTest` main class (make sure `-ea` is set in VM options).
3. RuneLite will launch with Backwater Refresh loaded as an external plugin.

---

## License

This plugin is licensed under the **BSD 2‑Clause License**. See the `LICENSE` file for details.
