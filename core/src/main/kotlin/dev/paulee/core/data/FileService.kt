package dev.paulee.core.data

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.paulee.api.data.*
import org.slf4j.LoggerFactory.getLogger

internal object FileService {

    private val logger = getLogger(FileService::class.java)

    private val mapper = jacksonObjectMapper().apply { enable(SerializationFeature.INDENT_OUTPUT) }

    fun toJson(dataInfo: DataInfo): String? = runCatching { this.mapper.writeValueAsString(dataInfo) }
        .getOrElse {
            this.logger.error("Exception: Could not serialize file info.", it)
            null
        }

    fun fromJson(json: String): DataInfo? = runCatching { this.mapper.readValue<DataInfo>(json) }
        .getOrElse {
            this.logger.error("Exception: Could not deserialize file info.", it)
            null
        }
}