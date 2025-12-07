package com.example.medfinder

data class OpenRouteServiceResponse(
    val routes: List<Route>
)

data class Route(
    val geometry: Geometry,
    val segments: List<Segment>,
    val summary: Summary
)

data class Geometry(
    val coordinates: List<List<Double>>
)

data class Segment(
    val steps: List<Step>
)

data class Step(
    val distance: Double,
    val duration: Double,
    val instruction: String,
    val name: String,
    val way_points: List<Int>
)

data class Summary(
    val distance: Double,
    val duration: Double
)