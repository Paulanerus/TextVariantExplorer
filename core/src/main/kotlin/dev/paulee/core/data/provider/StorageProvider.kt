package dev.paulee.core.data.provider

import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.api.data.provider.StorageType
import dev.paulee.core.data.provider.impl.DefaultProvider

object StorageProvider {
    fun of(type: StorageType): IStorageProvider {
        return when (type) {
            StorageType.Default, StorageType.SQLITE -> DefaultProvider()
        }
    }
}