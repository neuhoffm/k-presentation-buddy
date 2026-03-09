/*
 * Copyright (c) 2026 Advntrs LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.advntrs.kpresentationbuddy

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * A custom serializer that can read a JSON field that is either a single string
 * or an array of strings, and always deserializes it into a single, newline-separated string.
 */
object StringOrStringListSerializer : KSerializer<String> {
    private val listSerializer = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun deserialize(decoder: Decoder): String {
        val input = decoder as JsonDecoder
        val element = input.decodeJsonElement()

        // If the element is a single string primitive, just return its content.
        if (element is JsonPrimitive && element.isString) {
            return element.content
        }

        // If the element is an array, map each element to its string content
        // and then join them with newline characters. This correctly preserves empty strings.
        return element.jsonArray.map { it.jsonPrimitive.content }.joinToString("\n")
    }

    override fun serialize(encoder: Encoder, value: String) {
        throw UnsupportedOperationException("Serialization is not supported")
    }
}

/**
 * This is the base for all instructions.
 * The @JsonClassDiscriminator tells the serializer to look at the "type" field
 * in the JSON to decide which data class to deserialize into.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@kotlinx.serialization.json.JsonClassDiscriminator("type")
sealed interface Instruction {}
// The @SerialName annotation maps the "type" value from the JSON to the class.

@Serializable
data class Location(val line: Int, val column: Int)

@Serializable
@SerialName("createFile")
data class CreateFile(
    val path: String,
    @Serializable(with = StringOrStringListSerializer::class)
    val text: String? = null, // Text is optional for creating an empty file
    val openInEditor: Boolean = false
) : Instruction

@Serializable
@SerialName("typeText")
data class TypeText(
    @Serializable(with = StringOrStringListSerializer::class)
    val text: String,
    val delay: Long = 100
) : Instruction

@Serializable
@SerialName("goto")
data class GoTo(
    val line: Int,
    val column: Int
) : Instruction

@Serializable
@SerialName("command")
data class Command(
    val command: String
) : Instruction

@Serializable
@SerialName("openFile")
data class OpenFile(
    val path: String
) : Instruction

@Serializable
@SerialName("wait")
// A simple object for instructions that have no data.
data object Wait : Instruction

@Serializable
@SerialName("select")
data class Select(
    val start: Location,
    val end: Location,
    val name: String? = null // Optional name to save this selection
) : Instruction

@Serializable
@SerialName("delete")
data class Delete(
    val characters: Int? = null,
    val selection: String? = null // To delete a named selection
) : Instruction

@Serializable
@SerialName("replace")
data class Replace(
    @Serializable(with = StringOrStringListSerializer::class)
    val text: String
) : Instruction

@Serializable
@SerialName("typeTextFromFile")
data class TypeTextFromFile(
    val path: String,
    val delay: Long = 100
) : Instruction

@Serializable
@SerialName("typeChunksFromFile")
data class TypeChunksFromFile(
    val path: String,
    val waitAfterNewLine: Boolean = false,
    val waitAfterTyping: List<String> = emptyList(),
    val waitInsteadOfTyping: List<String> = emptyList(),
    val skipLinesContaining: List<String> = emptyList(),
    val delay: Long = 100
) : Instruction

@Serializable
@SerialName("runInTerminal")
data class RunInTerminal(
    @Serializable(with = StringOrStringListSerializer::class)
    val command: String,
    // This flag now controls whether we press "Enter" or just type.
    val execute: Boolean = true,
    val delay: Long = 100
) : Instruction

@Serializable
@SerialName("openFolder")
data class OpenFolder(
    val path: String
) : Instruction
