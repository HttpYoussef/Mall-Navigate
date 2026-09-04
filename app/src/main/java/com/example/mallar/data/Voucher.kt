package com.example.mallar.data

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
 */
data class Voucher(
    val id: String,
    val storeBrand: String,
    val logoAssetPath: String,
    val category: String,
    val discountTitle: String,
    val description: String,
    val expirationDate: String,
    val floor: Int,
    val terms: String
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
            discountTitle = "Free Upsize",
            description = "Upgrade any handcrafted beverage to the next size at no extra cost.",
            expirationDate = "Valid until Aug 31, 2026",
            floor = 2,
            terms = "One redemption per customer per visit. Cannot be combined with other offers. Valid on handcrafted beverages only. Management reserves the right to modify or withdraw this offer at any time."
        ),
        Voucher(
            id = "v_mango_20off",
            storeBrand = "Mango",
            logoAssetPath = "logos/Mango.png",
            category = StoreCategory.FASHION,
            discountTitle = "20% OFF",
            description = "Enjoy 20% off on all full-priced items storewide.",
            expirationDate = "Valid until Sep 15, 2026",
            floor = 1,
            terms = "Excludes sale items and gift cards. Discount applied at checkout. Cannot be combined with other promotions. While stocks last."
        ),
        Voucher(
            id = "v_zara_15off",
            storeBrand = "Zara",
            logoAssetPath = "logos/ZARA.png",
            category = StoreCategory.FASHION,
            discountTitle = "15% OFF",
            description = "15% off on a selected range of new-season pieces.",
            expirationDate = "Valid until Aug 20, 2026",
            floor = 1,
            terms = "Valid on selected items only, as marked in-store. Not valid in conjunction with any other offer. One voucher per transaction."
        ),
        Voucher(
            id = "v_adidaskids_bogo",
            storeBrand = "Adidas Kids",
            logoAssetPath = "logos/adidas kids.png",
            category = StoreCategory.FASHION,
            discountTitle = "Buy 1 Get 1",
            description = "Buy any pair of kids' shoes and get a second pair free.",
            expirationDate = "Valid until Sep 5, 2026",
            floor = 1,
            terms = "Second pair must be of equal or lesser value. Valid on regular-priced footwear only. Limited to two redemptions per customer."
        ),
        Voucher(
            id = "v_pinkberry_topping",
            storeBrand = "Pinkberry",
            logoAssetPath = "logos/pinkberry.png",
            category = StoreCategory.DINING,
            discountTitle = "Free Topping",
            description = "Get a free topping of your choice with any regular-size cup.",
            expirationDate = "Valid until Aug 25, 2026",
            floor = 2,
            terms = "One free topping per cup purchased. Valid on regular and large sizes. Not valid with mini cups or delivery orders."
        )
    )
}
