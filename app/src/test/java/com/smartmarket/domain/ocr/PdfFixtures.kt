package com.smartmarket.domain.ocr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

/** Carrega as linhas (texto + caixa) extraídas da camada de texto de PDFs reais de encarte. */
object PdfFixtures {
    fun load(name: String): List<OcrPage> {
        val text = javaClass.classLoader!!.getResource("flyers/$name")!!.readText()
        return Json.parseToJsonElement(text).jsonArray.map { p ->
            val obj = p.jsonObject
            OcrPage(
                index = obj["index"]!!.jsonPrimitive.int,
                lines = obj["lines"]!!.jsonArray.map { l ->
                    val lo = l.jsonObject
                    val b = lo["b"]!!.jsonArray.map { it.jsonPrimitive.int }
                    OcrLine(lo["t"]!!.jsonPrimitive.content, Box(b[0], b[1], b[2], b[3]))
                }
            )
        }
    }
}
