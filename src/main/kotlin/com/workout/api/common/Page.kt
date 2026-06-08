package com.workout.api.common

/**
 * A page of results plus the metadata a client needs to page: the total match count and the
 * window (limit/offset) that produced `items`. Use [map] to convert domain items to DTOs.
 */
data class Page<T>(
    val items: List<T>,
    val limit: Int,
    val offset: Int,
    val total: Long,
) {
    fun <R> map(transform: (T) -> R): Page<R> = Page(items.map(transform), limit, offset, total)
}
