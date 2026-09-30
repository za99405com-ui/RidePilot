package com.example.domain.engine

import com.example.data.model.LatLngPoint

/**
 * Built-in Alexandria work-area catalog.
 *
 * These presets make zone selection deterministic and usable without asking
 * the map to infer a neighbourhood from an arbitrary tap. Boundaries are
 * practical operational polygons for RidePilot and remain editable by the user.
 */
object AlexandriaZoneCatalog {

    data class AreaPreset(
        val id: String,
        val name: String,
        val center: LatLngPoint,
        val polygon: List<LatLngPoint>
    )

    private fun box(
        id: String,
        name: String,
        north: Double,
        east: Double,
        south: Double,
        west: Double
    ): AreaPreset {
        val center = LatLngPoint(
            latitude = (north + south) / 2.0,
            longitude = (east + west) / 2.0
        )

        return AreaPreset(
            id = id,
            name = name,
            center = center,
            polygon = listOf(
                LatLngPoint(north, west),
                LatLngPoint(north, east),
                LatLngPoint(south, east),
                LatLngPoint(south, west)
            )
        )
    }

    val areas: List<AreaPreset> = listOf(
        box("mandara", "المندرة", 31.291, 30.035, 31.274, 30.012),
        box("asafra", "العصافرة", 31.285, 30.024, 31.266, 30.004),
        box("miami", "ميامي", 31.278, 30.016, 31.259, 29.997),
        box("sidi_beshr", "سيدي بشر", 31.270, 30.004, 31.247, 29.982),
        box("victoria", "فيكتوريا", 31.258, 30.006, 31.237, 29.985),
        box("el_seyouf", "السيوف", 31.253, 30.023, 31.226, 29.997),
        box("san_stefano", "سان ستيفانو", 31.249, 29.971, 31.236, 29.951),
        box("gianaclis", "جناكليس", 31.238, 29.974, 31.219, 29.951),
        box("bakos", "باكوس", 31.240, 29.967, 31.218, 29.947),
        box("fleming", "فلمنج", 31.232, 29.959, 31.211, 29.941),
        box("smouha", "سموحة", 31.220, 29.976, 31.195, 29.944),
        box("sidi_gaber", "سيدي جابر", 31.226, 29.950, 31.207, 29.929),
        box("sporting", "سبورتنج", 31.223, 29.941, 31.204, 29.922),
        box("ibrahimia", "الإبراهيمية", 31.216, 29.931, 31.198, 29.911),
        box("camp_cesar", "كامب شيزار", 31.214, 29.921, 31.198, 29.904),
        box("shatby", "الشاطبي", 31.211, 29.914, 31.196, 29.897),
        box("azarita", "الأزاريطة", 31.207, 29.906, 31.192, 29.891),
        box("raml_station", "محطة الرمل", 31.206, 29.899, 31.193, 29.884),
        box("mansheya", "المنشية", 31.204, 29.891, 31.190, 29.875),
        box("bahary", "بحري", 31.216, 29.884, 31.196, 29.862),
        box("moharam_bek", "محرم بك", 31.198, 29.940, 31.174, 29.911),
        box("karmouz", "كرموز", 31.194, 29.916, 31.169, 29.884),
        box("wardian", "الورديان", 31.185, 29.890, 31.157, 29.854),
        box("dekheila", "الدخيلة", 31.143, 29.829, 31.116, 29.791),
        box("agami", "العجمي", 31.126, 29.794, 31.090, 29.720)
    )

    fun find(id: String): AreaPreset? = areas.firstOrNull { it.id == id }

    val alexandriaNorth = 31.36
    val alexandriaEast = 30.16
    val alexandriaSouth = 31.04
    val alexandriaWest = 29.55
}
