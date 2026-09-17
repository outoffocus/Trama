package com.trama.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchQueryTest {
    @Test
    fun `restaurant query keeps locality and maps category`() {
        assertEquals(
            listOf("restaurant", "portonovo"),
            SearchQuery.terms("restaurantes en Portonovo")
        )
    }

    @Test
    fun `results must match every meaningful term`() {
        data class Result(val id: Long)
        val one = Result(1)
        val two = Result(2)

        assertEquals(
            listOf(two),
            SearchQuery.intersect(listOf(listOf(one, two), listOf(two))) { it.id }
        )
    }
}
