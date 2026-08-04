package com.app.plateup.utils

import com.app.plateup.models.MenuItem
import org.json.JSONArray
import org.json.JSONObject

sealed class ImportValidationResult {
    data class Success(val summary: ImportSummary, val items: List<MenuItem>, val jsonCanteenName: String?) : ImportValidationResult()
    data class Error(val errors: List<String>) : ImportValidationResult()
}

data class ImportSummary(
    val totalItems: Int,
    val categoryBreakdown: Map<String, Int>
)

object MenuImportUtils {

    fun validateAndParse(jsonString: String, targetCanteenId: String): ImportValidationResult {
        val errors = mutableListOf<String>()
        val items = mutableListOf<MenuItem>()
        val categories = mutableMapOf<String, Int>()
        var jsonCanteenName: String? = null

        try {
            val root = JSONObject(jsonString)
            
            // 1. Schema Version
            if (!root.has("schemaVersion") || root.getInt("schemaVersion") != 1) {
                errors.add("Unsupported schema version. Required: 1")
                return ImportValidationResult.Error(errors)
            }

            // Optional canteen name for warning
            if (root.has("canteen")) {
                jsonCanteenName = root.getString("canteen")
            }

            // 2. Items Array
            if (!root.has("items")) {
                errors.add("Missing 'items' array")
                return ImportValidationResult.Error(errors)
            }

            val itemsArray = root.getJSONArray("items")
            if (itemsArray.length() == 0) {
                errors.add("The 'items' array is empty")
                return ImportValidationResult.Error(errors)
            }

            // 3. Duplicate Detection Set (Normalized category + name)
            val seenItems = mutableSetOf<String>()

            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                val rowNum = i + 1

                // Required fields check
                val name = itemObj.optString("name", "").trim()
                val category = itemObj.optString("category", "").trim()
                
                if (name.isEmpty()) {
                    errors.add("Item #$rowNum: Name is empty or whitespace")
                }
                if (category.isEmpty()) {
                    errors.add("Item #$rowNum: Category is empty or whitespace")
                }

                if (!itemObj.has("price")) {
                    errors.add("Item #$rowNum ($name): Missing price")
                } else {
                    val price = itemObj.optInt("price", -1)
                    if (price <= 0) {
                        errors.add("Item #$rowNum ($name): Price must be greater than zero")
                    }
                }

                if (errors.size > 50) break // Cap error messages for performance

                if (name.isNotEmpty() && category.isNotEmpty()) {
                    val normalizedKey = "${category.lowercase()} : ${name.lowercase()}"
                    if (seenItems.contains(normalizedKey)) {
                        errors.add("Item #$rowNum ($name): Duplicate item found in category '$category'")
                    } else {
                        seenItems.add(normalizedKey)
                    }

                    // Map to model if no critical errors found so far
                    // (We still validate price again here to be safe)
                    val price = itemObj.optInt("price", 0)
                    if (price > 0 && errors.isEmpty()) {
                        val menuItem = MenuItem(
                            id = "", // Will be generated via push().key
                            canteenId = targetCanteenId,
                            name = name,
                            price = price,
                            category = category,
                            available = itemObj.optBoolean("available", true),
                            takeawayAvailable = itemObj.optBoolean("takeawayAvailable", true),
                            averageRating = 0f,
                            reviewCount = 0
                        )
                        items.add(menuItem)
                        categories[category] = categories.getOrDefault(category, 0) + 1
                    }
                }
            }

        } catch (e: Exception) {
            return ImportValidationResult.Error(listOf("Malformed JSON: ${e.localizedMessage}"))
        }

        return if (errors.isEmpty()) {
            ImportValidationResult.Success(
                summary = ImportSummary(items.size, categories),
                items = items,
                jsonCanteenName = jsonCanteenName
            )
        } else {
            ImportValidationResult.Error(errors)
        }
    }
}
