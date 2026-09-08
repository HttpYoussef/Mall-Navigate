package com.example.mallar.data

import androidx.annotation.StringRes
import com.example.mallar.R

/**
 * STATIC PLACEHOLDER DATA MODEL.
 *
 * This mirrors the shape PlaceRepository already uses (a simple data class +
 * a repository object) so that swapping VoucherRepository's static list for a
 * real backend call later is a one-file change — nothing that consumes
 * Voucher/VoucherRepository needs to change.
 *
 * [storeBrand] must exactly match a real Place.brand from PlaceRepository so
 * the "Start Navigation" action on the voucher details screen can reuse the
 * app's existing destination-selection flow instead of a separate one.
 *
 * [expirationDateRes] points at hand-written per-locale copy ONLY because these
 * are static demo vouchers; a real backend/domain model should carry a real
 * date/instant + a localized template, and must not copy this opaque-string approach.
 */
data class Voucher(
    val id: String,
    val storeBrand: String,
    val logoAssetPath: String,
    val category: String,
    @StringRes val discountTitleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val expirationDateRes: Int,
    val floor: Int,
    @StringRes val termsRes: Int
)

object VoucherRepository {

    /** Categories shown as filter chips on the Offers screen. */
    val categories: List<String> = listOf(
        "All",
        StoreCategory.FASHION,
        StoreCategory.DINING,
        StoreCategory.JEWELLERY,
        StoreCategory.PERFUMES_COSMETICS,
        StoreCategory.PHARMACY
    )

    /**
     * STATIC PLACEHOLDER DATA — for UX validation only, not a real offers
     * backend. Each entry's `storeBrand` matches a real store already in
     * PlaceRepository (Starbucks, Mango, Zara, Adidas Kids, Pinkberry), so the
     * navigation handoff at the end of the voucher flow is fully real even
     * though the offer content itself is placeholder.
     */
    fun loadPlaceholderVouchers(): List<Voucher> = listOf(
        Voucher(
            id = "v_starbucks_upsize",
            storeBrand = "Starbucks",
            logoAssetPath = "logos/Starbucks.png",
            category = StoreCategory.DINING,
            discountTitleRes = R.string.voucher_starbucks_upsize_title,
            descriptionRes = R.string.voucher_starbucks_upsize_description,
            expirationDateRes = R.string.voucher_starbucks_upsize_expiration,
            floor = 2,
            termsRes = R.string.voucher_starbucks_upsize_terms
        ),
        Voucher(
            id = "v_mango_20off",
            storeBrand = "Mango",
            logoAssetPath = "logos/Mango.png",
            category = StoreCategory.FASHION,
            discountTitleRes = R.string.voucher_mango_20off_title,
            descriptionRes = R.string.voucher_mango_20off_description,
            expirationDateRes = R.string.voucher_mango_20off_expiration,
            floor = 1,
            termsRes = R.string.voucher_mango_20off_terms
        ),
        Voucher(
            id = "v_zara_15off",
            storeBrand = "Zara",
            logoAssetPath = "logos/ZARA.png",
            category = StoreCategory.FASHION,
            discountTitleRes = R.string.voucher_zara_15off_title,
            descriptionRes = R.string.voucher_zara_15off_description,
            expirationDateRes = R.string.voucher_zara_15off_expiration,
            floor = 1,
            termsRes = R.string.voucher_zara_15off_terms
        ),
        Voucher(
            id = "v_adidaskids_bogo",
            storeBrand = "Adidas Kids",
            logoAssetPath = "logos/adidas kids.png",
            category = StoreCategory.FASHION,
            discountTitleRes = R.string.voucher_adidaskids_bogo_title,
            descriptionRes = R.string.voucher_adidaskids_bogo_description,
            expirationDateRes = R.string.voucher_adidaskids_bogo_expiration,
            floor = 1,
            termsRes = R.string.voucher_adidaskids_bogo_terms
        ),
        Voucher(
            id = "v_pinkberry_topping",
            storeBrand = "Pinkberry",
            logoAssetPath = "logos/pinkberry.png",
            category = StoreCategory.DINING,
            discountTitleRes = R.string.voucher_pinkberry_topping_title,
            descriptionRes = R.string.voucher_pinkberry_topping_description,
            expirationDateRes = R.string.voucher_pinkberry_topping_expiration,
            floor = 2,
            termsRes = R.string.voucher_pinkberry_topping_terms
        )
    )
}
