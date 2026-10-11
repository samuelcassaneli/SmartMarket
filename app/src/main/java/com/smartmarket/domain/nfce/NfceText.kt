package com.smartmarket.domain.nfce

import com.smartmarket.domain.matching.FuzzyMatcher
import java.util.Locale

/** Utilidades de texto para notas fiscais (números pt-BR, nomes limpos, unidades). Puro, testável. */
object NfceText {

    private val PT_BR = Locale("pt", "BR")

    /** "1.234,56" -> 1234.56; "0,535" -> 0.535; "5.99" -> 5.99. */
    fun parseNumber(text: String?): Double? {
        if (text == null) return null
        val clean = text.replace(' ', ' ')
            .replace("R$", "")
            .filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
            .trim('.', ',')
        if (clean.isEmpty() || clean.none { it.isDigit() }) return null
        val normalized = when {
            clean.contains(',') -> clean.replace(".", "").replace(',', '.')
            clean.count { it == '.' } > 1 -> clean.replace(".", "")
            else -> clean
        }
        return normalized.toDoubleOrNull()
    }

    fun collapseSpaces(text: String): String = text.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

    fun digitsOnly(text: String): String = text.filter { it.isDigit() }

    /** 00.000.000/0000-00 */
    fun formatCnpj(digits: String): String {
        val d = digitsOnly(digits)
        if (d.length != 14) return digits
        return "${d.substring(0, 2)}.${d.substring(2, 5)}.${d.substring(5, 8)}/${d.substring(8, 12)}-${d.substring(12)}"
    }

    fun isWeightUnit(unit: String): Boolean {
        val u = unit.trim().lowercase(Locale.ROOT)
        return u == "kg" || u == "g" || u == "gr" || u == "kgs" || u == "quilo"
    }

    private val SMALL_WORDS = setOf("de", "da", "do", "das", "dos", "e", "com", "c/", "p/", "em", "sem", "a", "o")
    private val UNIT_TOKENS = setOf("kg", "un", "und", "unid", "pc", "pct")
    private val LITERS = Regex("""^(\d+(?:[.,]\d+)?)(?:l|lt)$""")
    private val SIZE_TOKEN = Regex("""^\d+(?:[.,]\d+)?(?:kg|g|gr|ml|l|lt|un|m|cm)$""", RegexOption.IGNORE_CASE)

    /**
     * Nome de produto a partir da descrição da nota: sem códigos, sem unidade solta no fim, em Title Case.
     * "BANANA PRATA ORG KG" -> "Banana Prata Org"; "LEITE COND ITALAC 395G" -> "Leite Cond Italac 395g".
     */
    fun cleanProductName(description: String): String {
        var tokens = collapseSpaces(description)
            .replace(Regex("""\(.*?\)"""), " ")
            .split(' ')
            .map { it.trim().trimEnd('.', ',', '-', ';') }
            .filter { it.isNotEmpty() }
        // remove códigos longos (EAN, códigos internos) e unidades soltas no fim
        tokens = tokens.filterNot { it.length >= 5 && it.all { c -> c.isDigit() } }
        while (tokens.size > 1 && tokens.last().lowercase(Locale.ROOT) in UNIT_TOKENS) tokens = tokens.dropLast(1)
        if (tokens.isEmpty()) return collapseSpaces(description)
        return tokens.mapIndexed { i, t -> titleToken(t, i == 0) }.joinToString(" ")
    }

    private fun titleToken(token: String, first: Boolean): String {
        val lower = token.lowercase(PT_BR)
        return when {
            SIZE_TOKEN.matches(token) -> lower.replace(LITERS, "$1L")
            !first && lower in SMALL_WORDS -> lower
            token.any { it.isDigit() } -> token.uppercase(PT_BR)
            else -> lower.replaceFirstChar { it.titlecase(PT_BR) }
        }
    }

    /** Nome do mercado a partir da razão social: "SPANI ATACADISTA LTDA" -> "Spani Atacadista". */
    fun cleanStoreName(name: String): String {
        val suffixes = setOf("ltda", "s/a", "sa", "s.a", "s.a.", "eireli", "me", "epp", "ltda.", "-", "cia", "&")
        val tokens = collapseSpaces(name).split(' ').filter { it.isNotEmpty() }.toMutableList()
        while (tokens.size > 1 && tokens.last().lowercase(Locale.ROOT) in suffixes) tokens.removeAt(tokens.lastIndex)
        return tokens.mapIndexed { i, t -> titleToken(t, i == 0) }.joinToString(" ")
    }

    private val STORE_NOISE = setOf(
        "ltda", "sa", "s/a", "eireli", "me", "epp", "comercio", "comercial", "de", "do", "da", "e",
        "alimentos", "supermercado", "supermercados", "mercado", "loja", "filial", "cia", "industria"
    )

    /**
     * Semelhança entre a razão social da nota e o nome de um mercado cadastrado (0..1).
     * "SPANI ATACADISTA LTDA" x "Spani Atacadista Resende" ≈ 1.
     */
    fun storeSimilarity(storeName: String, marketName: String): Double {
        val a = FuzzyMatcher.normalize(storeName).split(' ').filter { it.length > 1 && it !in STORE_NOISE }
        val b = FuzzyMatcher.normalize(marketName).split(' ').filter { it.length > 1 && it !in STORE_NOISE }
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val matches = a.count { ta -> b.any { tb -> ta == tb || (ta.length >= 4 && tb.startsWith(ta)) || (tb.length >= 4 && ta.startsWith(tb)) } }
        val coverage = matches.toDouble() / minOf(a.size, b.size)
        val firstBonus = if (a.first() == b.first()) 0.2 else 0.0
        return (coverage * 0.8 + firstBonus).coerceIn(0.0, 1.0)
    }

    /** Unidade sugerida para um produto novo criado a partir da nota. */
    fun suggestUnit(item: NfceItem): String = when {
        item.isWeighed -> "kg"
        item.unit.trim().equals("L", true) || item.unit.trim().equals("LT", true) -> "L"
        item.unit.trim().equals("PCT", true) || item.unit.trim().equals("PC", true) -> "pct"
        item.unit.trim().equals("CX", true) -> "cx"
        else -> "un"
    }

    private val CATEGORY_RULES: List<Pair<String, List<String>>> = listOf(
        "Hortifruti" to listOf(
            "banana", "maca", "manga", "melao", "melancia", "pera", "uva", "laranja", "limao", "mamao", "abacaxi",
            "morango", "batata", "cebola", "alho", "tomate", "cenoura", "alface", "rucula", "salsa", "cebolinha",
            "couve", "brocolis", "abobora", "abobrinha", "pepino", "pimentao", "chuchu", "beterraba", "mandioca", "aipim",
            "inhame", "kiwi", "goiaba", "maracuja", "hortifruti", "legume", "verdura", "fruta"
        ),
        "Carnes" to listOf(
            "carne", "bovin", "frango", "file", "peito", "coxa", "sobrecoxa", "asa", "linguica", "salsicha", "bacon",
            "acem", "patinho", "alcatra", "picanha", "costela", "musculo", "moida", "suino", "lombo", "pernil", "peixe",
            "tilapia", "sardinha", "atum", "hamburguer", "almondega"
        ),
        "Laticínios" to listOf(
            "leite", "queijo", "iogurte", "manteiga", "requeijao", "mussarela", "muss", "presunto", "peru", "creme de leite",
            "nata", "coalhada", "ricota", "cottage", "margarina", "ovo", "ovos"
        ),
        "Padaria" to listOf("pao", "bolo", "bolinho", "brownie", "torrada", "bisnaguinha", "croissant", "rosca"),
        "Bebidas" to listOf("refrigerante", "refri", "suco", "agua", "cerveja", "cerv", "vinho", "energetico", "cha ", "isotonico", "coca", "guarana"),
        "Matinais" to listOf("cafe", "achocolatado", "achoc", "cereal", "aveia", "granola", "nescau", "toddy"),
        "Limpeza" to listOf(
            "detergente", "det ", "sabao", "amaciante", "amac", "desinfetante", "sanitaria", "multiuso", "esponja",
            "limpador", "alvejante", "lustra", "saco lixo", "saco de lixo", "inseticida"
        ),
        "Higiene" to listOf(
            "sabonete", "shampoo", "condicionador", "creme dental", "dental", "escova", "desodorante", "papel hig",
            "pap hig", "absorvente", "fralda", "algodao", "cotonete", "barbear"
        ),
        "Alimentos" to listOf(
            "arroz", "feijao", "macarrao", "massa", "acucar", "oleo", "farinha", "sal ", "molho", "extrato", "biscoito",
            "bisc", "bolacha", "fuba", "azeite", "vinagre", "tempero", "maionese", "ketchup", "milho", "ervilha",
            "atum", "goiabada", "chocolate", "doce", "geleia", "pipoca", "lasanha", "pizza"
        )
    )

    /** Categoria sugerida pela descrição (palavras-chave simples); "Geral" se nada casar. */
    fun suggestCategory(description: String): String {
        val n = " " + FuzzyMatcher.normalize(description) + " "
        for ((category, words) in CATEGORY_RULES) {
            if (words.any { w -> keywordMatches(n, w) }) {
                return category
            }
        }
        return "Geral"
    }

    /** Palavras curtas casam inteiras ("maca" não casa "macarrao"); longas casam por prefixo. */
    private fun keywordMatches(normalized: String, word: String): Boolean {
        val w = word.trim()
        val pattern = if (w.length >= 5) """\b${Regex.escape(w)}""" else """\b${Regex.escape(w)}\b"""
        return Regex(pattern).containsMatchIn(normalized)
    }

    /**
     * Quanto somar na despensa (na unidade do produto) para um item da nota.
     * Conversão simples: kg <-> g e L <-> ml; item pesado em produto contado por unidade vale 1 unidade.
     */
    fun pantryQuantity(quantity: Double, itemUnit: String, productUnit: String): Double {
        val iu = itemUnit.trim().lowercase(Locale.ROOT)
        val pu = productUnit.trim().lowercase(Locale.ROOT)
        val itemKg = when (iu) {
            "kg", "kgs", "quilo" -> quantity
            "g", "gr" -> quantity / 1000.0
            else -> null
        }
        val itemLiters = when (iu) {
            "l", "lt", "lts" -> quantity
            "ml" -> quantity / 1000.0
            else -> null
        }
        return when {
            itemKg != null && pu == "kg" -> itemKg
            itemKg != null && (pu == "g" || pu == "gr") -> itemKg * 1000.0
            itemKg != null -> 1.0 // pesado, mas o produto é contado por unidade/pacote
            itemLiters != null && pu == "l" -> itemLiters
            itemLiters != null && pu == "ml" -> itemLiters * 1000.0
            itemLiters != null -> 1.0
            else -> quantity
        }
    }
}
