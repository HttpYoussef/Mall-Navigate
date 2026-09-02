package com.example.mallar.data

/**
 * Identifies a shopping centre the app can navigate.
 *
 * [hasNavigationData] is true for malls whose graph, embeddings, and floor-plan
 * assets are bundled in this build.  It is a build-time flag, not an intrinsic
 * property of the mall; once the multi-mall data layer lands it will move there.
 *
 * Display names and location strings live in res/values/strings.xml and are
 * mapped by enum constant in MallSelectionScreen — NOT stored here.
 */
enum class Mall(val hasNavigationData: Boolean) {
    CITY_STARS(hasNavigationData = true),
    CITY_CENTRE_ALMAZA(hasNavigationData = false),
    MALL_OF_EGYPT(hasNavigationData = false),
}
