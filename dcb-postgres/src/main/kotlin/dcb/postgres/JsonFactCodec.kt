package dcb.postgres

import dcb.Fact
import dcb.FactCodec
import dcb.type
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class JsonFactCodec internal constructor(
    private val json: Json,
    private val serializers: Map<String, KSerializer<out Fact>>,
) : FactCodec {
    override fun encode(fact: Fact): String {
        val serializer = serializerFor(fact.type)
        @Suppress("UNCHECKED_CAST")
        return json.encodeToString(serializer as KSerializer<Fact>, fact)
    }

    override fun decode(type: String, jsonText: String): Fact {
        val serializer = serializerFor(type)
        return json.decodeFromString(serializer, jsonText)
    }

    private fun serializerFor(type: String): KSerializer<out Fact> =
        serializers[type] ?: error("Unknown fact type '$type'")

    class Builder {
        private val serializers = linkedMapOf<String, KSerializer<out Fact>>()

        val json: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        inline fun <reified T : Fact> register(serializer: KSerializer<T>): Builder {
            val name = T::class.simpleName ?: error("Fact must be a named class")
            put(name, serializer)
            return this
        }

        fun put(type: String, serializer: KSerializer<out Fact>) {
            serializers[type] = serializer
        }

        fun build(): JsonFactCodec = JsonFactCodec(json, serializers.toMap())
    }
}

fun jsonFactCodec(build: JsonFactCodec.Builder.() -> Unit): JsonFactCodec =
    JsonFactCodec.Builder().apply(build).build()
