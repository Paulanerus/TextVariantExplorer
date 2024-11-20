package dev.paulee.api.data

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
annotation class RequiresData(val name: String, val sources: Array<KClass<*>> = [])

@Target(AnnotationTarget.CLASS)
annotation class DataSource(val file: String)
