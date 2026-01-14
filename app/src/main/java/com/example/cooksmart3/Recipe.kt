package com.example.cooksmart3

data class Recipe(
    val title: String,
    val directions: List<String>,
    val ingredients: List<String> = emptyList(),
    val NER: List<String> = emptyList(),
    val link: String = "",
    val site: String = "",
    var matchPercentage: Float = 0f,
    var id: String = "",
    var isFavorite: Boolean = false
)
