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
        val searchQuery: String = name,
        val fixedPolygon: List<com.example.data.model.LatLngPoint>? = null
    )

    private val userSelectedScope = listOf(
        com.example.data.model.LatLngPoint(31.2140, 29.8850),
        com.example.data.model.LatLngPoint(31.2175, 29.8820),
        com.example.data.model.LatLngPoint(31.2100, 29.8750),
        com.example.data.model.LatLngPoint(31.2000, 29.8850),
        com.example.data.model.LatLngPoint(31.1960, 29.8960),
        com.example.data.model.LatLngPoint(31.1820, 29.9010),
        com.example.data.model.LatLngPoint(31.1710, 29.9040),
        com.example.data.model.LatLngPoint(31.1700, 29.9140),
        com.example.data.model.LatLngPoint(31.1840, 29.9150),
        com.example.data.model.LatLngPoint(31.1850, 29.9320),
        com.example.data.model.LatLngPoint(31.1680, 29.9380),
        com.example.data.model.LatLngPoint(31.1650, 29.9520),
        com.example.data.model.LatLngPoint(31.1820, 29.9580),
        com.example.data.model.LatLngPoint(31.2200, 29.9600),
        com.example.data.model.LatLngPoint(31.2380, 29.9820),
        com.example.data.model.LatLngPoint(31.2260, 29.9920),
        com.example.data.model.LatLngPoint(31.2220, 29.9980),
        com.example.data.model.LatLngPoint(31.2400, 30.0040),
        com.example.data.model.LatLngPoint(31.2650, 30.0180),
        com.example.data.model.LatLngPoint(31.2820, 30.0400),
        com.example.data.model.LatLngPoint(31.2890, 30.0350),
        com.example.data.model.LatLngPoint(31.2920, 30.0150),
        com.example.data.model.LatLngPoint(31.2750, 29.9980),
        com.example.data.model.LatLngPoint(31.2550, 29.9720),
        com.example.data.model.LatLngPoint(31.2360, 29.9450),
        com.example.data.model.LatLngPoint(31.2180, 29.9180),
        com.example.data.model.LatLngPoint(31.2080, 29.9000)
    )

    val areas: List<AreaPreset> = listOf(
        AreaPreset(
            id = "selected_scope",
            name = "النطاق المحدد",
            searchQuery = "النطاق المحدد",
            fixedPolygon = userSelectedScope
        ),
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
