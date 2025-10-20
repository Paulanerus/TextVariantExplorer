package dev.paulee.api.data

import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.api.data.provider.QueryOrder
import dev.paulee.api.internal.Embedding
import java.io.Closeable
import java.nio.file.Path

interface IDataService : Closeable {

    suspend fun createDataPool(dataInfo: DataInfo, onProgress: (progress: Int) -> Unit): Boolean

    suspend fun deleteDataPool(name: String): Boolean

    suspend fun rebuildDataPool(dataInfo: DataInfo, onProgress: (progress: Int) -> Unit): Boolean

    fun selectDataPool(selection: String)

    fun getSelectedPool(): String

    fun hasSelectedPool(): Boolean

    fun getAvailablePools(): Set<String>

    fun getAvailableDataInfo(): Set<DataInfo>

    fun getSuggestions(field: String, value: String): List<String>

    suspend fun downloadModel(model: Embedding.Model, path: Path, onProgress: (progress: Int) -> Unit)

    suspend fun importPool(path: Path): Boolean

    suspend fun exportPool(dataInfo: DataInfo, path: Path): Boolean

    fun getPage(
        query: String,
        isSemantic: Boolean,
        order: QueryOrder?,
        pageCount: Int,
    ): Pair<List<Map<String, String>>, Map<String, List<Map<String, String>>>>

    fun getPageCount(query: String, isSemantic: Boolean): Triple<Long, Long, Set<String>>

    fun createStorageProvider(infoName: String, path: Path): IStorageProvider?

    fun dataInfoToString(dataInfo: DataInfo): String?

    fun dataInfoFromString(dataInfo: String): DataInfo?

    fun appDir(): Path

    fun dataDir(): Path

    fun modelDir(): Path
}
