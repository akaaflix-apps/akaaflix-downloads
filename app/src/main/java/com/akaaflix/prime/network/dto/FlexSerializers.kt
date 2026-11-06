package com.akaaflix.prime.network.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/*
 * Xtream Codes panels are notoriously inconsistent: the same field may be a number, a
 * quoted number, an empty string, null, false or even an empty array depending on the
 * panel version. These serializers accept any of those shapes.
 */

internal fun JsonElement?.flexString(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    is JsonArray -> firstOrNull()?.flexString()
    is JsonObject -> null
}

internal fun JsonElement?.flexLong(): Long? = flexString()?.let { s ->
    s.toLongOrNull() ?: s.toDoubleOrNull()?.toLong() ?: if (s.equals("true", true)) 1L else null
}

internal fun JsonElement?.flexDouble(): Double? = flexString()?.replace(',', '.')?.toDoubleOrNull()

internal fun JsonElement?.flexStringList(): List<String> = when (this) {
    is JsonArray -> mapNotNull { it.flexString() }
    is JsonPrimitive -> listOfNotNull(flexString())
    else -> emptyList()
}

object FlexStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexString", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String? =
        (decoder as? JsonDecoder)?.decodeJsonElement()?.flexString() ?: runCatching { decoder.decodeString() }.getOrNull()

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeString("") else encoder.encodeString(value)
    }
}

object FlexLongSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexLong", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long? = (decoder as? JsonDecoder)?.decodeJsonElement()?.flexLong()
    override fun serialize(encoder: Encoder, value: Long?) = encoder.encodeLong(value ?: 0L)
}

object FlexDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexDouble", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): Double? = (decoder as? JsonDecoder)?.decodeJsonElement()?.flexDouble()
    override fun serialize(encoder: Encoder, value: Double?) = encoder.encodeDouble(value ?: 0.0)
}

/** Accepts `["a","b"]`, `"a"`, `[1,2]` or null. */
object FlexStringListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexStringList", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): List<String> =
        (decoder as? JsonDecoder)?.decodeJsonElement()?.flexStringList() ?: emptyList()

    override fun serialize(encoder: Encoder, value: List<String>) = encoder.encodeString(value.joinToString(","))
}
