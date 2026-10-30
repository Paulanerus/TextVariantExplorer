package dev.paulee.api.other

interface IUpdateService {

    suspend fun isNewerVersionAvailable(): Boolean

}