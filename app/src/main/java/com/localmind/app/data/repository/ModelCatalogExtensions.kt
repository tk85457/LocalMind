package com.localmind.app.data.repository

import com.localmind.app.domain.model.ModelCatalogItem

fun ModelCatalogItem.stopTokensJson(): String {
    if (stopTokens.isEmpty()) return "[]"
    val escaped = stopTokens
        .map { token -> "\"${token.replace("\"", "\\\"")}\"" }
        .joinToString(separator = ",")
    return "[$escaped]"
}
