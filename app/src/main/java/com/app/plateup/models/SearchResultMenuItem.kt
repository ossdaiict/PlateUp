package com.app.plateup.models

data class SearchResultMenuItem(
    var menuItem: MenuItem = MenuItem(),
    var canteenName: String = "",
    var canteenOpen: Boolean = true
)
