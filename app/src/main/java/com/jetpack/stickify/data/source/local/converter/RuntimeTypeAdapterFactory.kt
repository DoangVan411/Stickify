package com.jetpack.stickify.data.source.local.converter

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.IOException

/**
 * AdapterFactory cho Polymorphic Serialization / Deserialization bằng Gson.
 * Cho phép phân biệt các lớp con của AssetRef, Layer, EditAction qua thuộc tính discriminator (`type`).
 */
class RuntimeTypeAdapterFactory<T> private constructor(
    private val baseType: Class<T>,
    private val typeFieldName: String,
    private val maintainType: Boolean
) : TypeAdapterFactory {

    private val labelToSubtype: MutableMap<String, Class<*>> = LinkedHashMap()
    private val subtypeToLabel: MutableMap<Class<*>, String> = LinkedHashMap()

    companion object {
        fun <T> of(baseType: Class<T>, typeFieldName: String = "type", maintainType: Boolean = false): RuntimeTypeAdapterFactory<T> {
            return RuntimeTypeAdapterFactory(baseType, typeFieldName, maintainType)
        }

        fun <T> of(baseType: Class<T>): RuntimeTypeAdapterFactory<T> {
            return RuntimeTypeAdapterFactory(baseType, "type", false)
        }
    }

    fun registerSubtype(subtype: Class<out T>, label: String): RuntimeTypeAdapterFactory<T> {
        if (labelToSubtype.containsKey(label) || subtypeToLabel.containsKey(subtype)) {
            throw IllegalArgumentException("Subtypes and labels must be unique.")
        }
        labelToSubtype[label] = subtype
        subtypeToLabel[subtype] = label
        return this
    }

    fun registerSubtype(subtype: Class<out T>): RuntimeTypeAdapterFactory<T> {
        return registerSubtype(subtype, subtype.simpleName)
    }

    override fun <R> create(gson: Gson, type: TypeToken<R>): TypeAdapter<R>? {
        if (!baseType.isAssignableFrom(type.rawType)) {
            return null
        }

        val labelToDelegate = LinkedHashMap<String, TypeAdapter<*>>()
        val subtypeToDelegate = LinkedHashMap<Class<*>, TypeAdapter<*>>()

        for ((label, subtype) in labelToSubtype) {
            val delegate = gson.getDelegateAdapter(this, TypeToken.get(subtype))
            labelToDelegate[label] = delegate
            subtypeToDelegate[subtype] = delegate
        }

        return object : TypeAdapter<R>() {
            @Throws(IOException::class)
            override fun write(out: JsonWriter, value: R?) {
                if (value == null) {
                    out.nullValue()
                    return
                }

                val srcType: Class<*> = value.javaClass
                val label = subtypeToLabel[srcType]
                    ?: throw JsonParseException("Cannot serialize ${srcType.name}; did you forget to register it?")

                @Suppress("UNCHECKED_CAST")
                val delegate = subtypeToDelegate[srcType] as TypeAdapter<R>?
                    ?: throw JsonParseException("Cannot serialize ${srcType.name}; delegate not found.")

                val jsonObject = delegate.toJsonTree(value).asJsonObject

                if (maintainType) {
                    gson.getAdapter(JsonElement::class.java).write(out, jsonObject)
                    return
                }

                val clone = JsonObject()
                if (jsonObject.has(typeFieldName)) {
                    throw JsonParseException("Cannot serialize ${srcType.name} because it already defines a field named $typeFieldName")
                }
                clone.add(typeFieldName, JsonPrimitive(label))
                for ((key, element) in jsonObject.entrySet()) {
                    clone.add(key, element)
                }
                gson.getAdapter(JsonElement::class.java).write(out, clone)
            }

            @Throws(IOException::class)
            override fun read(reader: JsonReader): R? {
                val jsonElement = gson.getAdapter(JsonElement::class.java).read(reader)
                if (jsonElement.isJsonNull) {
                    return null
                }
                val jsonObject = jsonElement.asJsonObject
                val labelJsonElement = if (maintainType) {
                    jsonObject.get(typeFieldName)
                } else {
                    jsonObject.remove(typeFieldName)
                }

                if (labelJsonElement == null) {
                    throw JsonParseException("Cannot deserialize $baseType because it does not contain a field named $typeFieldName")
                }

                val label = labelJsonElement.asString
                @Suppress("UNCHECKED_CAST")
                val delegate = labelToDelegate[label] as TypeAdapter<R>?
                    ?: throw JsonParseException("Cannot deserialize $baseType subtype named $label; did you forget to register it?")

                return delegate.fromJsonTree(jsonObject)
            }
        }.nullSafe()
    }
}
