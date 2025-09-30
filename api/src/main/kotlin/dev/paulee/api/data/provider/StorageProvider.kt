package dev.paulee.api.data.provider

import dev.paulee.api.data.DataInfo
import java.io.Closeable
import java.nio.file.Path
import java.util.LinkedHashMap

typealias QueryOrder = Pair<String, Boolean>

enum class StorageType {
    Default,

    @Deprecated("SQLITE support has been removed. Kept only to detect legacy data pools; use StorageType.Default.")
    SQLITE,
}

enum class ProviderStatus {
    Success,
    Failed,
    Exists,
}

interface IStorageProvider : Closeable {

    fun init(dataInfo: DataInfo, path: Path): ProviderStatus

    fun get(
        name: String,
        ids: List<Long> = emptyList(),
        whereClause: List<String> = emptyList(),
        filter: List<String> = emptyList(),
        order: QueryOrder? = null,
        offset: Int = 0,
        limit: Int = Int.MAX_VALUE,
    ): List<Map<String, String>>

    fun count(
        name: String,
        ids: List<Long> = emptyList(),
        whereClause: List<String> = emptyList(),
        filter: List<String> = emptyList(),
    ): Long

    fun suggestions(name: String, field: String, value: String, amount: Int): List<String>

    fun streamData(name: String): Sequence<LinkedHashMap<String, String>>
}