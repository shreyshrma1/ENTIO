package com.entio.cli

internal class JsonFragment(
    internal val encoded: String,
)

internal fun jsonObject(vararg fields: Pair<String, Any?>): JsonFragment =
    JsonFragment(
        fields.joinToString(
            prefix = "{",
            postfix = "}",
            separator = ",",
        ) { (name, value) ->
            "${jsonString(name)}:${jsonValue(value)}"
        },
    )

internal fun jsonArray(values: Iterable<Any?>): JsonFragment =
    JsonFragment(
        values.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",",
            transform = ::jsonValue,
        ),
    )

private fun jsonValue(value: Any?): String =
    when (value) {
        null -> "null"
        is JsonFragment -> value.encoded
        is String -> jsonString(value)
        is Boolean,
        is Number,
        -> value.toString()
        is Iterable<*> -> jsonArray(value).encoded
        else -> error("Unsupported JSON value: ${value::class.qualifiedName}")
    }

private fun jsonString(value: String): String =
    buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                in '\u0000'..'\u001F' -> append("\\u%04x".format(character.code))
                else -> append(character)
            }
        }
        append('"')
    }
