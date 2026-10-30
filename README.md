# Blockout

Remake of the DOS game *Blockout* (California Dreams, 1989) with Java 27 and JavaFX 27.

Game rules, block shapes, falling speed, scoring and rotation behaviour follow the original
(factors taken from the measurements documented by BlockOut II by Jean-Luc Pons). The look is based
on the DOS VGA version: EGA palette, green wireframe, layer colours from the bottom up
blue, green, cyan, red, magenta, brown, grey.

## Running

Requirement: Maven must run on a JDK 27, so set `JAVA_HOME` to a JDK 27. Maven itself is provided
by the wrapper.

```bash
./mvnw compile exec:exec
```

Tests: `./mvnw test`. Render scenes as PNG: `./mvnw test -Dtest=ScreenshotTest -Dshots=<directory>`.

## Controls

| Key | Action |
|---|---|
| Arrow keys / numpad 2,4,6,8 | Move block |
| Home, Page Up, End, Page Down / numpad 7,9,1,3 | Move diagonally |
| Q / W / E | Rotate around X / Y / Z |
| A / S / D | Rotate in the opposite direction |
| Space | Drop |
| P | Pause |
| Esc | Quit game |
| F11 / Alt+Enter | Full screen |

The earlier a block is dropped, the more points it scores. An empty pit counts as two additional
layers.

## Data

Settings and hall of fame are stored in `~/.blockout/`.
