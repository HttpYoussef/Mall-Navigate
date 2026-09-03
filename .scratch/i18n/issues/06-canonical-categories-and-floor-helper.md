# 06: Canonical categories and floor-label helper

**What to build:** Store categories and floor references are drawn from one consistent,
localized vocabulary. The same category is named the same way on every non-assistant surface,
and every floor reference reads "Floor N" / "الطابق N" through a single helper.

**Blocked by:** 04, 05.

**Status:** ready-for-agent

- [ ] A presentation-only `categoryKey → displayStringRes` mapping for the five **Canonical
      category** values (Fashion, Jewellery, Perfumes & Cosmetics, Dining, Pharmacy), with
      `values/` + `values-ar/` strings. The mall graph's stored key (including the unnormalized
      `Perfumes& Cosmetics`) is unchanged and still used for filtering/matching; normalization
      lives only in this mapping.
- [ ] Category labels hardcoded in the non-assistant display sites (destination selection, home
      store cards, search filters) replaced with the mapping. Category names that appear only in
      code and not the data ("Entertainment", "Services", "Cafés", "Food & Dining") dropped from
      those surfaces. `ChatSystem.kt` is **not** touched.
- [ ] A floor-label helper: `Int` floor → localized `"Floor %1$s"` fed a Western-digit-formatted
      number. Every non-navigation floor call site routed through it — destination selection,
      `HomeSharedComponents` (including its "Level 1 / Level 2" phrasing), the home offers list.
      "Ground/First Floor" and "Level N" naming removed.
- [ ] `Voucher.floorLabel: String` and `OfferItem.floor: String` become `floor: Int`; labels are
      derived at composition time via the helper, **not** cached inside `remember(...)`.
      `rememberPlaceMetadata()` reworked so the floor/category label recomputes each composition
      (so it updates after a locale `recreate()`). `Voucher.category` value `"Food"` normalized
      to the `Dining` key.
- [ ] The Egypt-only phone-auth constraint (`+20` constructed in Kotlin in all three auth paths)
      documented in the codebase (a comment or a doc note).
- [ ] Unit tests: each of the 5 canonical keys → its display resource; unknown key → no label;
      voucher `"Food"` alias → `Dining` key; floor `2` → floor resource + Western-digit arg `"2"`.
- [ ] `./gradlew compileDebugKotlin`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin`,
      `lintDebug` green.
