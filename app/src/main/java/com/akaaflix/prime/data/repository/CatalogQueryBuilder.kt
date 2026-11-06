package com.akaaflix.prime.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.akaaflix.prime.common.ContentText
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.SortOrder
import com.akaaflix.prime.domain.repository.BrowseQuery

/**
 * Builds parameterised SQL for browse grids. Hidden categories/items, hidden keywords and
 * (optionally) adult content are always filtered out at the database level, so hidden
 * content never reaches browsing or search results.
 */
object CatalogQueryBuilder {

    private data class Table(val name: String, val idColumn: String, val latestColumn: String, val hasRating: Boolean)

    private fun table(type: ContentType) = when (type) {
        ContentType.LIVE -> Table("channels", "streamId", "added", hasRating = false)
        ContentType.MOVIE -> Table("movies", "streamId", "added", hasRating = true)
        ContentType.SERIES -> Table("series", "seriesId", "lastModified", hasRating = true)
        ContentType.EPISODE -> error("Episodes are not stored in the catalog")
    }

    fun select(q: BrowseQuery, hiddenKeywords: List<String>): SimpleSQLiteQuery {
        val t = table(q.type)
        val args = mutableListOf<Any>()
        val sql = StringBuilder("SELECT t.* FROM ${t.name} t ")
        if (q.mostWatched) {
            sql.append("INNER JOIN playback_history h ON h.playlistId = t.playlistId AND h.contentType = ? AND h.contentId = CAST(t.${t.idColumn} AS TEXT) ")
            args += q.type.name
        }
        sql.append("WHERE ")
        appendFilters(sql, args, q, t, hiddenKeywords, includeCategory = true)
        sql.append(" ORDER BY ")
        sql.append(
            if (q.mostWatched) "h.playCount DESC, h.lastWatchedAt DESC"
            else orderBy(q.sort, t),
        )
        q.limit?.let { sql.append(" LIMIT ").append(it) }
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    /** Per-category counts using the same visibility filters (without category/search filters). */
    fun categoryCounts(playlistId: Long, type: ContentType, includeAdult: Boolean, hiddenKeywords: List<String>): SimpleSQLiteQuery {
        val t = table(type)
        val args = mutableListOf<Any>()
        val sql = StringBuilder("SELECT t.categoryId AS categoryId, COUNT(*) AS cnt FROM ${t.name} t WHERE ")
        appendFilters(sql, args, BrowseQuery(playlistId, type, includeAdult = includeAdult), t, hiddenKeywords, includeCategory = false)
        sql.append(" GROUP BY t.categoryId")
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    private fun appendFilters(
        sql: StringBuilder,
        args: MutableList<Any>,
        q: BrowseQuery,
        t: Table,
        hiddenKeywords: List<String>,
        includeCategory: Boolean,
    ) {
        sql.append("t.playlistId = ?")
        args += q.playlistId

        if (includeCategory) {
            if (q.categoryId != null) {
                sql.append(" AND t.categoryId = ?")
                args += q.categoryId
            } else if (!q.categoryIds.isNullOrEmpty()) {
                sql.append(" AND t.categoryId IN (").append(q.categoryIds.joinToString(",") { "?" }).append(")")
                args.addAll(q.categoryIds)
            }
        }

        // Hidden categories and items.
        sql.append(
            " AND (t.categoryId IS NULL OR t.categoryId NOT IN (SELECT targetId FROM hidden_content " +
                "WHERE playlistId = ? AND scope = 'CATEGORY' AND restriction = 'HIDDEN' AND contentType = ?))",
        )
        args += q.playlistId; args += q.type.name
        sql.append(
            " AND CAST(t.${t.idColumn} AS TEXT) NOT IN (SELECT targetId FROM hidden_content " +
                "WHERE playlistId = ? AND scope = 'ITEM' AND restriction = 'HIDDEN' AND contentType = ?)",
        )
        args += q.playlistId; args += q.type.name

        // Adult content (parental control).
        if (!q.includeAdult) {
            sql.append(
                " AND t.isAdult = 0 AND (t.categoryId IS NULL OR t.categoryId NOT IN " +
                    "(SELECT categoryId FROM categories WHERE playlistId = ? AND type = ? AND isAdult = 1))",
            )
            args += q.playlistId; args += q.type.name
        }

        // Hidden titles / keywords.
        hiddenKeywords.filter { it.isNotBlank() }.forEach { keyword ->
            sql.append(" AND t.nameNorm NOT LIKE ? ESCAPE '\\'")
            args += ContentText.likePattern(keyword)
        }

        q.search?.takeIf { it.isNotBlank() }?.let { s ->
            sql.append(" AND t.nameNorm LIKE ? ESCAPE '\\'")
            args += ContentText.likePattern(s)
        }

        if (q.favoritesOnly) {
            sql.append(" AND CAST(t.${t.idColumn} AS TEXT) IN (SELECT contentId FROM favorites WHERE playlistId = ? AND contentType = ?)")
            args += q.playlistId; args += q.type.name
        }
        q.groupId?.let { gid ->
            sql.append(" AND CAST(t.${t.idColumn} AS TEXT) IN (SELECT contentId FROM group_contents WHERE groupId = ? AND contentType = ?)")
            args += gid; args += q.type.name
        }
    }

    private fun orderBy(sort: SortOrder, t: Table): String = when (sort) {
        SortOrder.DEFAULT -> "t.sortOrder ASC"
        SortOrder.LATEST -> "t.${t.latestColumn} DESC, t.sortOrder ASC"
        SortOrder.NAME_ASC -> "t.name COLLATE NOCASE ASC"
        SortOrder.NAME_DESC -> "t.name COLLATE NOCASE DESC"
        SortOrder.RATING -> if (t.hasRating) "t.rating IS NULL, t.rating DESC, t.sortOrder ASC" else "t.name COLLATE NOCASE ASC"
        SortOrder.YEAR -> if (t.name != "channels") "t.year IS NULL, t.year DESC, t.sortOrder ASC" else "t.sortOrder ASC"
    }
}
