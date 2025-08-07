package dev.paulee.api.data.provider

import dev.paulee.api.data.DataInfo
import java.io.Closeable
import java.nio.file.Path

typealias QueryOrder = Pair<String, Boolean>

enum class StorageType {
    SQLITE,
}

enum class ProviderStatus {
    SUCCESS,
    FAILED,
    EXISTS,
}

interface IStorageProvider : Closeable {

    fun init(dataInfo: DataInfo, path: Path, lock: Boolean = false): ProviderStatus

    fun insert(name: String, entries: List<Map<String, String>>)

    fun get(
        name: String,
        ids: Set<Long> = emptySet(),
        whereClause: List<String> = emptyList(),
        filter: List<String> = emptyList(),
        order: QueryOrder? = null,
        offset: Int = 0,
        limit: Int = Int.MAX_VALUE,
    ): List<Map<String, String>>

    fun count(
        name: String,
        ids: Set<Long> = emptySet(),
        whereClause: List<String> = emptyList(),
        filter: List<String> = emptyList(),
    ): Long
}