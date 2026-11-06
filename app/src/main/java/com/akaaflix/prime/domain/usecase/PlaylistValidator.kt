package com.akaaflix.prime.domain.usecase

import com.akaaflix.prime.domain.repository.PlaylistInput
import com.akaaflix.prime.network.ServerUrl

/** Validates the four Add Playlist fields (Name, Username, Password, URL). */
object PlaylistValidator {
    enum class Field { NAME, USERNAME, PASSWORD, URL }

    const val MAX_NAME = 60

    /**
     * Returns the invalid fields (empty when valid). When [passwordOptional] is true (editing),
     * an empty password means "keep the stored password".
     */
    fun validate(input: PlaylistInput, passwordOptional: Boolean = false): List<Field> = buildList {
        if (input.name.isBlank() || input.name.trim().length > MAX_NAME) add(Field.NAME)
        if (input.username.isBlank() || input.username.trim().any { it.isWhitespace() }) add(Field.USERNAME)
        if (!passwordOptional && input.password.isEmpty()) add(Field.PASSWORD)
        if (ServerUrl.normalize(input.url) == null) add(Field.URL)
    }
}
