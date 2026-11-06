package com.akaaflix.prime.domain

import com.akaaflix.prime.domain.repository.PlaylistInput
import com.akaaflix.prime.domain.usecase.PlaylistValidator
import com.akaaflix.prime.domain.usecase.PlaylistValidator.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistValidatorTest {
    @Test fun validInput() {
        assertTrue(PlaylistValidator.validate(PlaylistInput("Nova", "user", "pass", "http://host.example:8080")).isEmpty())
    }

    @Test fun reportsEachInvalidField() {
        assertEquals(listOf(Field.NAME, Field.USERNAME, Field.PASSWORD, Field.URL), PlaylistValidator.validate(PlaylistInput(" ", "", "", "bad url")))
        assertEquals(listOf(Field.USERNAME), PlaylistValidator.validate(PlaylistInput("A", "us er", "p", "host.com")))
        assertEquals(listOf(Field.NAME), PlaylistValidator.validate(PlaylistInput("x".repeat(61), "u", "p", "host.com")))
    }

    @Test fun passwordOptionalWhenEditing() {
        assertTrue(PlaylistValidator.validate(PlaylistInput("A", "u", "", "host.com"), passwordOptional = true).isEmpty())
    }
}
