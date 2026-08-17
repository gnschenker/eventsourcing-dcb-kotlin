package dcb

interface FactCodec {
    fun encode(fact: Fact): String
    fun decode(type: String, json: String): Fact
}
