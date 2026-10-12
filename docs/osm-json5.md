# The Terra++ `osm.json5` configuration file

This document describes the OpenStreetMap interpretation configuration consumed by
Terra++ (`net.buildtheearth.terraminusminus`), based on the parsing code in
`net.buildtheearth.terraminusminus.dataset.osm` and the default config at
`src/main/resources/net/buildtheearth/terraminusminus/dataset/osm/osm.json5`.

It is the reference for terrapreview's live-reload preview and for the IntelliJ
plugin's JSON schema / autocompletion support.

## File format and loading

* The file is named `osm.json5` and is **JSON5-flavored JSON**: `//` and `/* */`
  comments are allowed, as are trailing commas and other lenient syntax.
* Resolution order (`OSMMapper.load()`):
  1. `Disk.configFile("osm.json5")` — a user-supplied file in the Terra++ config
     directory, if it exists;
  2. otherwise the bundled resource `osm.json5` from the Terra++ JAR.
* Parsing failures throw a `JsonParseException` wrapping the underlying error,
  together with the reader position.

## How the file is parsed (important for tooling)

Terra++ does **not** parse this file with Gson's tree model. It uses custom
streaming `TypeAdapter`s built on `JsonParser` / `JsonReader`
(`net.buildtheearth.terraminusminus.dataset.osm.JsonParser`). Two consequences
matter for anyone writing a schema or a validator:

1. **Duplicate object keys are meaningful and preserved.** `JsonReader` yields
   every member in document order, and the type-discriminating parsers
   (`JsonParser.Typed`) and the list parsers (`readTypedList`) consume each
   member as it appears. An object like

   ```json5
   { "condition": {...}, "condition": {...}, "condition": {...} }
   ```

   is not "last key wins" — it is an **ordered sequence** of mappers. The
   standard JSON data model (RFC 8259, and therefore JSON Schema) cannot
   express this; see the note at the end of this document.

2. **Objects double as typed unions.** A single-member object selects a type by
   its key name, e.g. `{ "wide": { ... } }` selects the `LineWide` mapper, and
   the value is parsed as that type. Unknown type keys are a hard error
   (`"type X is not supported by Y!"`), and unknown properties inside a
   structurally-parsed object throw `IllegalStateException("invalid property: ...")`.

## Top-level structure

```json5
{
    "line":   { /* LineMapper  - applied to OSM ways  */ },
    "polygon": { /* PolygonMapper - applied to OSM relations/ways forming polygons */ }
}
```

Parsed by `Root.Parser`. Only `line` and `polygon` are valid keys; anything
else throws. Both are required (`@NonNull`).

At runtime (`Root.apply`), each OSM element is projected and dispatched:

* points/multi-points are discarded (never generated);
* `LineString` / `MultiLineString` geometries go to `line`;
* `Polygon` / `MultiPolygon` geometries go to `polygon`;
* single geometries are wrapped into their multi- equivalent;
* any other geometry type throws.

## Mapper types

Mappers are the union types selected by the object-key discriminator. `line`
uses `LineMapper` types; `polygon` uses `PolygonMapper` types. Each mapper maps
an OSM element (id, tags, original geometry, projected geometry) to a
collection of generateable `VectorGeometry`s, or `null` for "nothing matched".

### Line mappers (`LineParser` TYPES)

| Key | Class | Meaning |
| --- | --- | --- |
| `all` | `All.Line` | Applies every child; result is the concatenation, or `null` if **any** child returns `null`. |
| `any` | `Any.Line` | Applies every child; concatenates all non-`null` results, ignoring `null` children. |
| `condition` | `Condition.Line` | If `match` matches the element, applies `emit`; otherwise `null`. |
| `first` | `First.Line` | Applies children in order; returns the first non-`null` result. |
| `nothing` | `Nothing.Line` | Always emits nothing (non-`null` empty result). |
| `narrow` | `LineNarrow` | Draws a narrow (1-block) line. |
| `sharp` | `LineSharp` | Draws a sharp-edged line. |
| `wide` | `LineWide` | Draws a wide line with a computed radius. |

The children of `all`, `any` and `first` are encoded as a JSON **object whose
repeated keys form an ordered list** (via `readTypedList`), each key naming the
child's type:

```json5
"first": {
    "condition": { ... },
    "condition": { ... },
    "wide":      { ... }
}
```

At least one member is required (`"at least one member required!"`).

### Polygon mappers (`PolygonParser` TYPES)

| Key | Class | Meaning |
| --- | --- | --- |
| `all` | `All.Polygon` | As `all` above, for polygons. |
| `any` | `Any.Polygon` | As `any` above, for polygons. |
| `condition` | `Condition.Polygon` | As `condition` above, for polygons. |
| `convert` | `PolygonConvert` | Converts the polygon's outline to a line and applies a `LineMapper` to it. |
| `first` | `First.Polygon` | As `first` above, for polygons. |
| `nothing` | `Nothing.Polygon` | Always emits nothing. |
| `distance` | `PolygonDistance` | Draws into all blocks within `maxDist` blocks of the polygon boundary. |
| `fill` | `PolygonFill` | Draws into every block whose column intersects the polygon. |

`convert` has one nested union member, `line`, whose value is a `LineMapper`
object (same union as above):

```json5
"convert": {
    "line": { "sharp": { ... } }
}
```

### Common mapper properties

| Mapper | Properties |
| --- | --- |
| `narrow`, `sharp`, `wide`, `fill`, `distance` | `draw` (DrawFunction, required), `layer` (DValue, required) |
| `wide` | additionally `radius` (DValue, required) |
| `distance` | additionally `maxDist` (int, default `2`) |
| `condition` | `match` (MatchCondition, required), `emit` (mapper of the same geometry kind, required) |

Unknown properties throw.

## Match conditions (`MatchParser` TYPES)

Used by `condition.match` and combinators. All are union types selected by key.

| Key | Class | Meaning |
| --- | --- | --- |
| `and` | `And` | Object of repeated keys; all children must match. |
| `or` | `Or` | Object of repeated keys; any child must match. |
| `not` | `Not` | Single nested member; inverts it. |
| `tag` | `Tag (match)` | Matches an OSM tag (see below). |
| `id` | `Id` | Matches the OSM element id: a string (exact) or array of strings (any). |
| `intersects` | `Intersects` | True if the element's **original** (geographic) bounds intersect the given bounding box `minX`, `maxX`, `minZ`, `maxZ` (doubles; these are coordinates in the generator's projected space as used by the config, e.g. the Caspian Sea special-case). |

`and`/`or` children are again encoded as an object with repeated keys (ordered
list via `readTypedList`).

### `tag` match

A single-member object whose key is the tag key and whose value is one of:

* `null` — matches if the tag key is present, regardless of value;
* a string — matches if the tag equals that exact value;
* an array of strings — matches if the tag equals any of the values.

```json5
"tag": { "highway": null }
"tag": { "highway": "motorway" }
"tag": { "highway": ["motorway", "trunk"] }
```

## Dynamic values — `DValue` (`DValueParser` TYPES)

A `DValue` is a numeric expression evaluated against the element's tags,
used for `layer` and `radius`. Again a key-discriminated union; binary
operators take exactly **two** members, in order (first operand, then second):

| Key | Class | Meaning |
| --- | --- | --- |
| `constant` | `Constant` | A JSON number, used as-is. |
| `tag` | `Tag (dvalue)` | `key` (string) + `fallback` (number); parses the tag value as a double, else `fallback`. |
| `+` | `BiOp.Add` | `first + second` |
| `-` | `BiOp.Subtract` | `first - second` |
| `*` | `BiOp.Multiply` | `first * second` |
| `/` | `BiOp.Divide` | `first / second` |
| `floor_div` | `BiOp.FloorDiv` | `floor(first / second)` |
| `min` | `BiOp.Min` | `min(first, second)` |
| `max` | `BiOp.Max` | `max(first, second)` |

Example (radius = 6 + floor((lanes*3)/2)):

```json5
"radius": {
    "+": {
        "floor_div": {
            "*": { "tag": { "key": "lanes", "fallback": 2 }, "constant": 3 },
            "constant": 2
        },
        "constant": 6
    }
}
```

## Draw functions (`DrawFunctionParser` TYPES)

Used by `draw` in the drawing mappers (`narrow`, `sharp`, `wide`, `fill`,
`distance`). Key-discriminated union:

| Key | Class | Meaning |
| --- | --- | --- |
| `all` | `All` | Object of repeated keys; every child draw function is applied. |
| `block` | `Block` | Places a block state: `id` (string, required) and optional `properties` (object of string/number/boolean values). |
| `no_trees` | `NoTrees` | Suppresses tree generation here. |
| `ocean` | `Ocean` | Marks the column as ocean. |
| `water` | `Water` | Places water (uses the column's weight value). |
| `weight_add` | `WeightAdd` | `delegate` (DrawFunction) + `value` (number); offsets the column weight. |
| `weight_clamp` | `WeightClamp` | Clamps weight. |
| `weight_greater_than` | `WeightGreaterThan` | `delegate` (DrawFunction) + `value` (number); delegates only when weight exceeds `value`. |
| `weight_less_than` | `WeightLessThan` | `delegate` (DrawFunction) + `value` (number); delegates only when weight is below `value`. |

## What the default config does (summary)

The bundled `osm.json5` is a semantic port of Terra++'s old hard-coded road
behavior:

* **line**: a `first` chain of conditions, evaluated in document order:
  1. tunnels (`tunnel` tag set and not `no`) emit **nothing** — tunnels are
     never generated;
  2. `highway=motorway|trunk` — wide gray concrete road, radius
     `6 + floor(lanes*3/2)`, no trees;
  3. `highway=motorway_link|trunk_link` (interchange) — as above with radius
     `4 + floor(min(lanes,2)*3/2)`;
  4. `highway=primary|raceway` (main) — radius `4 + lanes*2`;
  5. `highway=tertiary|residential` (minor) — radius `4 + lanes`;
  6. `highway=secondary|primary_link|secondary_link|living_street|bus_guideway|service|unclassified`
     (side) — radius `4 + floor((lanes*3+1)/2)`;
  7. any other `highway=*` — narrow dirt path;
  8. any `building=*` — sharp brick outline;
  9. `waterway=stream` — water line, radius 1.5;
  10. `waterway=river|canal` — water with reduced weight (−4), radius 9, no trees.
* **polygon**: a `first` chain:
  1. `building=*` — no trees within 4 blocks of the building (`distance`), plus
     the outline converted to a sharp brick line;
  2. `water=*`, `natural=water` or `waterway=riverbank` — water fill within 6
     blocks of the boundary;
  3. `natural=coastline` intersecting the Caspian Sea bounding box — treated as
     water, not ocean;
  4. any other `natural=coastline` — ocean within 7 blocks of the boundary.

Every drawing condition also reads the `layer` tag (fallback `0`) as the
generation layer.

## Note for the IntelliJ plugin / JSON Schema work

Because `all`, `any`, `first`, `and`, `or` and the whole `condition` chain rely
on **repeated keys with order significance**, this format is deliberately
outside the JSON data model (RFC 8259 §4: names within an object SHOULD be
unique; JSON Schema's instance data model makes duplicate-key behavior
undefined). Consequences:

* Standard JSON Schema **can** still drive autocompletion: IntelliJ derives
  completion from `properties`/type discriminators and is lenient about the
  duplicate-key inspection, so a schema that describes the union types
  (with `maxProperties`-free objects whose `additionalProperties` point back
  at the mapper union) gives correct key and value completion.
* Standard JSON Schema **cannot** validate the ordered/duplicate-key aspect;
  constraints like `minProperties`/`required` would be the only partial
  approximations. Full validation of this aspect requires either a custom
  inspection in the plugin or the terrapreview server, both of which see the
  raw member order.
* Alternative encodings (e.g. arrays of `{type, value}` objects) would be
  schema-friendly, but would change the config format consumed by Terra++'s
  `readTypedList`-based parsers, so they are not compatible with the current
  upstream implementation.
