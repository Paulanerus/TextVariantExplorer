package dev.paulee.api.data

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
annotation class RequiresData(val name: String, val sources: Array<KClass<*>> = [])

@Target(AnnotationTarget.CLASS)
annotation class DataSource(val file: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Unique

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Index

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class NullValue(val values: Array<String>)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class MergeWith(val field: String)
