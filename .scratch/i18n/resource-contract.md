# String Resource Contract & Conventions

Reference conventions for string externalization and localization in MallAR.

## Conventions

- **Key-naming scheme**:
  - Always use `lower_snake_case`.
  - Prefix keys by screen or functional area (e.g. `home_`, `parking_`, `nav_`, `auth_`, `mall_`), matching existing conventions in `app/src/main/res/values/strings.xml`.

- **Interpolation**:
  - ALWAYS use `%1$s`, `%2$s`, etc.
  - NEVER use `%d`, `%1$d`, `%.0f`, or other numeric format specifiers.
  - *Reason*: `stringResource(id, arg)` formats numeric placeholders using the active resource locale, causing `%d` to render Eastern Arabic-Indic digits (٠-٩) under `values-ar`. In MallAR, all numbers must display in Western digits (0-9). Every number must be pre-formatted to a Western-digit `String` via the shared formatter (ticket 05) and supplied as `%1$s`.

- **Plurals**:
  - Use `<plurals>` for any count or duration string where grammatical forms vary across languages (e.g. singular vs. plural: "%1$s results").

- **Translator metadata & Context**:
  - Wrap non-translatable text, brand names, numbers, units, and codes in `<xliff:g id="..." example="...">...</xliff:g>`.
  - When `<xliff:g>` is used, ensure the root `<resources>` tag declares `xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2"`.
  - Add an explanatory XML comment (`<!-- comment -->`) directly above any key whose meaning or grammatical context is ambiguous.

- **Parity & Source of Truth**:
  - `app/src/main/res/values/strings.xml` is the single source of truth and must be complete.
  - `app/src/main/res/values-ar/strings.xml` must match `values/strings.xml` key-for-key. Any missing Arabic key triggers a `MissingTranslation` lint error failing `./gradlew lintDebug`.
  - Spanish and French resource files (`values-es/`, `values-fr/`) are intentionally NOT shipped in this effort.

- **Pure-debug strings**:
  - Do not externalize developer-only / pure-debug strings (e.g. `"BUG"`, raw debug logs, test assertions).
