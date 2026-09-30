package com.example.domain.engine

/**
 * User-facing Alexandria area names.
 *
 * This catalog intentionally stores names only — never fake rectangular
 * boundaries. The real polygon is resolved on demand from map data.
 */
object AlexandriaZoneCatalog {

    data class AreaPreset(
        val id: String,
        val name: String,
        val searchQuery: String = name
    )

    val areas: List<AreaPreset> = listOf(
        AreaPreset("mandara", "المندرة"),
        AreaPreset("asafra", "العصافرة"),
        AreaPreset("miami", "ميامي"),
        AreaPreset("sidi_beshr", "سيدي بشر"),
        AreaPreset("victoria", "فيكتوريا"),
        AreaPreset("el_seyouf", "السيوف"),
        AreaPreset("san_stefano", "سان ستيفانو"),
        AreaPreset("gianaclis", "جناكليس"),
        AreaPreset("bakos", "باكوس"),
        AreaPreset("fleming", "فلمنج"),
        AreaPreset("smouha", "سموحة"),
        AreaPreset("ezbet_saad", "عزبة سعد"),
        AreaPreset("sidi_gaber", "سيدي جابر"),
        AreaPreset("sporting", "سبورتنج"),
        AreaPreset("ibrahimia", "الإبراهيمية"),
        AreaPreset("camp_cesar", "كامب شيزار"),
        AreaPreset("shatby", "الشاطبي"),
        AreaPreset("azarita", "الأزاريطة"),
        AreaPreset("raml_station", "محطة الرمل"),
        AreaPreset("mansheya", "المنشية"),
        AreaPreset("bahary", "بحري"),
        AreaPreset("moharam_bek", "محرم بك"),
        AreaPreset("karmouz", "كرموز"),
        AreaPreset("wardian", "الورديان"),
        AreaPreset("dekheila", "الدخيلة"),
        AreaPreset("agami", "العجمي")
    )

    fun find(id: String): AreaPreset? = areas.firstOrNull { it.id == id }

    const val alexandriaNorth = 31.36
    const val alexandriaEast = 30.16
    const val alexandriaSouth = 31.04
    const val alexandriaWest = 29.55
}
