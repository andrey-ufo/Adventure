# Engine conditions

**Status:** stable. Describes the condition grammar introduced in
Ф3.2 (N7.base, 2026-11-13). Changes here are breaking for every adventure
file — extend with care.

This document is the reference for **condition strings** in the adventure
JSON: the syntax that authors write in `"condition": "..."`, how the
parser turns it into a `Condition` AST, how the writer round-trips it
back, and what the type checker validates on top.

It is the companion to:
- [`engine-types.md`](./engine-types.md) — the variable type system;
  `has_X`, `Compare`, `InRange` in a condition are typed against the
  declared `VarType`s.
- [`engine-architecture.md`](./engine-architecture.md) — the module
  structure; `ConditionParser` (this doc) and `TypeChecker` (types doc)
  are two of its four pipeline stages.

## Grammar (Ф3.2 / N7.base)

```
expr    = or
or      = and ("||" and)*
and     = not ("&&" not)*
not     = "!" not | primary
primary = "(" expr ")"
        | "true"
        | "false"
        | "has_" IDENT
        | IDENT "in" INT ".." INT
        | IDENT COMPARE_OP INT

COMPARE_OP = ">=" | "<=" | "==" | "!=" | ">" | "<"
```

Whitespace between tokens is ignored. IDENT is any string without the
reserved characters (§"Reserved characters") — it may contain spaces,
so `has_my flag` is `Has("my flag")`.

**Precedence** (highest to lowest): `!` > `&&` > `||`. `&&` and `||` are
**left-associative**: `a && b && c` parses as `And(And(a, b), c)`.
Parentheses override precedence.

## Leaves

### `has_X` — Bool flag

`has_sword` → `Condition.Has("sword")`, true iff the player's
`flags` contains `"sword"` at evaluation time.

**Only for `Bool` variables.** A `has_` on an `ItemCount` / `IntMin` /
`IntRange` variable is a **type error** (`TypeError.HasOnNonBool`)
with a fix hint (`use 'sword > 0' instead`). The parser does not enforce
this — the type checker (Ф2.2) does.

`has_` is the **canonical** syntax for Bool variables. There is no bare
`sword` variable reference — see §"What is not supported (`VarRef`)" .

### `X <op> N` — comparison

`health >= 50` → `Condition.Compare("health", GE, 50)`, true iff
`stats["health"] >= 50`. `X` must be numeric (`Integer`, `IntMin`,
`IntRange`, `ItemCount`); `N` is a signed integer.

Operators: `>=`, `<=`, `==`, `!=`, `>`, `<`. Two-character operators are
parsed first, so `>=` is not consumed as `>` followed by `=`.
Variable names may contain spaces (`my stat > 0`); trailing `/ leading`
whitespace is trimmed.

### `X in A..B` — inclusive range

`health in 50..100` → `Condition.InRange("health", 50, 100)`, true iff
`stats["health"]` is in the inclusive range `[50, 100]`. `X` must be
numeric. `A` and `B` are signed integers. Spaces around `..` are
tolerated (`health in 50 .. 100`).

### `true` / `false` — literal

`Condition.Literal(true)` / `Condition.Literal(false)`.

Rarely useful directly — the author usually writes a condition that
**always holds** by using `has_some_permanent_flag` from initial state.
`Literal` is useful in generated IR and in future AST optimization (e.g.
`Literal(true) && X` → `X`). It is **not** a shortcut for "empty
condition": an empty `"condition"` string is an error; the choice must
either omit the field (meaning "always available") or set an explicit
condition.

## Operators

### `&&` — logical AND

`A && B` → `Condition.And(A, B)`. Short-circuits left-to-right in
`Evaluator.evaluate`: if `A` is false, `B` is not evaluated.

### `||` — logical OR

`A || B` → `Condition.Or(A, B)`. Short-circuits left-to-right.

### `!` — logical NOT

`!A` → `Condition.Not(A)`. **Unary prefix.** `!has_sword` is
`Not(Has("sword"))`. `!!has_x` is `Not(Not(Has("x")))`. `!(A && B)` is
`Not(And(A, B))`.

**What `!` binds to.** `!` applies to its immediate `primary` — the
"not" production recurses right, so `!A && B` is `And(Not(A), B)`, not
`Not(And(A, B))` (the second requires explicit parentheses). Likewise
`!health > 5` parses as `Not(Compare(health, GT, 5))`, **not**
`Compare(Not(health), GT, 5)` — the latter would be a type error
(`Not` returns Bool, `Compare` requires numeric), and the author almost
certainly meant "health is not greater than 5". If you want to negate a
comparison, either form works — `!(health > 5)` and `!health > 5` are
the **same AST**. Use the parenthesized form if you want to be
explicit.

## Precedence table

| Level | Operators | Associativity |
|---|---|---|
| 1 (highest) | `!` | right (prefix) |
| 2 | `&&` | left |
| 3 (lowest) | `\|\|` | left |

Examples:

| Input | Parsed as |
|---|---|
| `a && b \|\| c` | `Or(And(a, b), c)` — `&&` binds tighter |
| `a \|\| b && c` | `Or(a, And(b, c))` |
| `(a \|\| b) && c` | `And(Or(a, b), c)` — parentheses override |
| `!a && b` | `And(Not(a), b)` |
| `!(a && b)` | `Not(And(a, b))` |
| `a && b && c` | `And(And(a, b), c)` — left-associative |
| `a \|\| b \|\| c` | `Or(Or(a, b), c)` |

## Round-trip with `ConditionWriter`

The writer (`engine/.../parse/ConditionWriter.kt`, Ф3.1.2) produces a
**canonical** string for any `Condition`. The contract is:

```
parse(write(c)) == c    (structurally)
```

for the variants the parser can produce. As of Ф3.2, the supported set
is:

| Condition variant | Round-trips |
|---|---|
| `Has`, `Compare` (all 6 ops), `InRange` | ✅ |
| `Not(Has)` | ✅ |
| `And`, `Or` (any nesting) | ✅ |
| `Not(And)`, `Not(Or)`, `Not(Compare)`, `Not(InRange)`, `!!` | ✅ |
| `Literal(true)` / `Literal(false)` | ✅ |
| `VarRef` | ❌ — not produced by the parser (§below) |

The writer's canonical form uses **minimal brackets**: `a && b || c` is
`Or(And(a, b), c)` without brackets on the `And`; brackets appear only
where precedence requires them. A consequence: `parse(write(c)) == c`
holds, but `write(parse(s)) == s` does **not** — the writer normalizes
whitespace and adds only necessary brackets. This is intentional; the
canonical form is what the editor (Ф9.2) saves back to disk.

## Global invariants

**Status (Ф3.5).** Besides `"condition"` on choices, conditions are
used in a second place: `"invariants"` — global rules that fire
regardless of which scene the player is in.

```json
{
  "invariants": [
    { "condition": "health <= 0", "outcome": "death" },
    { "condition": "stamina <= 0 && has_poison", "outcome": "death" }
  ]
}
```

### Semantics

`Invariant(condition, outcome)` fires **when `condition` holds**,
evaluated after every step (choice effects) and after every scene
entry (scene effects). The first matching invariant in the list wins;
later invariants are not evaluated. The only outcome today is
`"death"` (`Outcome.Death`): the reader shows the death screen.

### Fallback when `invariants` is absent or empty

The runtime falls back to the **pre-Ф3.5** hardcoded rule
`health <= 0 => death`. This preserves behaviour for every old file
and for authors who do not declare invariants. A file with a
**non-empty** `invariants` list **replaces** the fallback entirely —
the author's rules are authoritative. If the author wants
`health <= 0 → death` **plus** `stamina <= 0 → death`, both must be
declared; the fallback will not add the health rule automatically.

### Why a list

Multiple invariants model different failure modes that coexist:
- the classic `health <= 0 → death` (always a good default for
  adventure games);
- a poison timer `has_poison && turn_count > 10 → death`;
- (future, Ф12) `roll(1d6) == 1 && has_trap → death`.

Future phases (Ф12 RNG, Ф13 combat, Ф15 external outcomes) will add
**outcome variants** (not "each mechanic is a new invariant") — the
list mechanism is already generic; the outcome is what grows.

### Limits (Ф3.5)

- **Numeric unreachability is not checked yet.** `Invariant(health <
  -100)` where `health` is `IntMin(0)` is dead code — `Analyzer` does
  not report it. Ф3.6 (`Analyzer.deadConditions`) will.
- **Flag unreachability is checked.** `Invariant(has_unknown_flag,
  ...)` where `unknown_flag` is never set → `LintKind.UnreachableInvariant`
  (WARNING, non-blocking, sceneId = null).
- **Order matters.** An author writing `[A, B]` where both might
  fire simultaneously sees `A`'s outcome (first match). Put specific
  rules before generic ones; the analyzer does not enforce order.

## What is not supported (`VarRef`)

A bare identifier — `"health"`, `"sword"` — is **not** a valid condition.
The parser rejects it with `ConditionError` ("unrecognized syntax"). Only
`has_X` is accepted for Bool variable references.

**Why not `VarRef` now.** Accepting `"health"` as `VarRef("health")` is
a change that needs a plan for `has_X` disambiguation: if a variable is
named `has_seen`, the author has no way to reference it — the parser
would always see `Has("seen")`. This is a solvable problem (`has_` prefix
reserved, or explicit syntax), but it is **out of scope for Ф3.2**.

`Condition.VarRef` **exists in the AST** (Ф3.1) and is handled by
`Evaluator`, `TypeChecker` (no-op), `TypeInference`, `Analyzer`,
`ConditionWriter` (writes as bare name). Adding the parser branch is a
~30-line change in `ConditionParser` + ~15 lines in `TypeChecker`
(`TypeError.VarRefOnNonBool`) + the `has_`-prefix reservation. It is
tracked as a separate roadmap item — see
[`checklist-tracker.md`](./checklist-tracker.md) Appendix B ("VarRef +
has_X deprecation"). It will land when the first author requests "why
can't I write `sword && key`?" — not before.

## Reserved characters

The following characters are **part of the condition syntax** and cannot
appear in a variable or flag name:

```
&  |  !  <  >  =  (  )
```

A name containing one of these → `ConditionError` with a fix hint.
In particular, `has_sword && health` is **not** `Has("sword && health")`
(an old bug fixed in Ф1.3.3.2); the parser splits on `&&`, parses the
left as `Has("sword")`, then rejects the right (`"health"` is a bare
identifier — see §"What is not supported"). The author who meant
"has_sword AND has_health" writes `has_sword && has_health`.

## Common mistakes

| Symptom | Likely cause | Fix |
|---|---|---|
| `has_sword && health` fails to parse | `health` is a bare identifier, no `VarRef` support | Use `has_sword && health > 0` or `has_sword && has_health` |
| `has_sword` on an `ItemCount` variable; parse OK, typechecker error | `has_X` requires `Bool` | Use `sword > 0` |
| `!(health > 5)` vs `!health > 5` — same AST | `!` binds to `Compare` as a whole | Both are `Not(Compare(health, GT, 5))`; write whichever reads better |
| `"a & b"` fails to parse | `&` is not a valid operator | Use `&&` |
| `"a | b"` fails to parse | `\|` is not a valid operator | Use `\|\|` |
| `"a || "` fails to parse | Empty operand | Remove the trailing `\|\|`, or supply a right side |
| `"(a && b"` fails to parse | Unbalanced parentheses | Add the closing `)` |

## See also

- [`engine-types.md`](./engine-types.md) — the variable type system
  (`has_X` / `Compare` / `InRange` are typed against `VarType`).
- [`engine-architecture.md`](./engine-architecture.md) — pipeline
  stages: `ConditionParser`, `TypeChecker`, `Engine.compile`,
  `Analyzer.lint`.
- [`glossary.md`](./glossary.md) — `has_X`, `Condition`, `Flag`, `Stat`.
- [`checklist-tracker.md`](./checklist-tracker.md) — phase plan
  (Ф3.1, Ф3.2, and Appendix B for the deferred `VarRef`).
- Source: `engine/src/main/kotlin/com/myapp/adventure/engine/parse/`
  (`ConditionParser.kt`, `ConditionWriter.kt`, `ParseError.kt`).