package com.example.zoterohelpernative.data

data class MappedColor(
    val zoteroHex: String,
    val uiHex: String,
    val name: String
)

data class Palette(
    val id: String,
    val name: String,
    val colors: List<MappedColor>
)
