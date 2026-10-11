package com.smartmarket.domain.ocr

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Retângulo em pixels da página. Substitui `android.graphics.Rect` para que o parser
 * rode em testes JVM (onde `Rect` é um stub com campos zerados).
 */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    fun union(other: Box) = Box(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))

    /** Distância horizontal entre os intervalos (0 se sobrepõem). */
    fun horizontalGap(other: Box): Int = max(0, max(left, other.left) - min(right, other.right))

    /** Distância vertical entre os intervalos (0 se sobrepõem). */
    fun verticalGap(other: Box): Int = max(0, max(top, other.top) - min(bottom, other.bottom))

    fun verticalOverlap(other: Box): Int = max(0, min(bottom, other.bottom) - max(top, other.top))
}

/** Linha de texto crua devolvida pelo OCR. */
data class OcrLine(val text: String, val box: Box? = null)

/** Uma página lida (linhas na ordem do OCR). */
data class OcrPage(val index: Int, val lines: List<OcrLine>)

/** Oferta extraída do encarte (antes do casamento com o catálogo). */
data class ParsedOffer(
    val description: String,
    /** Preço da oferta. Em atacarejo com dois preços, é o MENOR (atacado). */
    val price: Double,
    /** Preço de varejo quando o bloco tinha dois preços. */
    val retailPrice: Double? = null,
    /** "atacado", "atacado a partir de 6 un", "leve 3 pague 2"... */
    val note: String = "",
    val page: Int = 0,
    val box: Box? = null
)

/**
 * Parser de encartes: separa lixo, preços e descrições e associa cada descrição ao preço
 * mais próximo (abaixo ou à direita). Puro Kotlin, testável na JVM.
 */
object EncarteParser {

    const val MIN_PRICE = 0.50
    const val MAX_PRICE = 2000.0

    // ------------------------------------------------------------------ Normalização

    fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    // ------------------------------------------------------------------ Preços

    /** Preço com centavos: 4,99 / 4.99 / 1.234,56 / R$ 4,99. Não aceita "1,5L", "32,9ml", "1,750L". */
    private val PRICE_REGEX = Regex(
        """(?<![\d.,])(?:r\s*[${'$'}s]\s*)?(?!0\d)(\d{1,3}(?:\.\d{3})+|\d{1,4})\s?[,.]\s?(\d{2})(?![\d])(?!\s*(?:ml|l|lt|lts|g|gr|m|cm|mm|mg|litros?)\b)""",
        RegexOption.IGNORE_CASE
    )

    /** Linha inteira com centavos separados por espaço ("4 99", "R$ 12 90 cada"). */
    private val SPLIT_PRICE_LINE = Regex(
        """^(?:r\s*[${'$'}s]\s*)?(\d{1,4})\s+[,.]?\s*(\d{2})\s*(?:cada|un|und|unid|kg|/kg|/un)?\.?$""",
        RegexOption.IGNORE_CASE
    )

    private val INTEGER_PART = Regex("""^(?:r\s*[${'$'}s]\s*)?(\d{1,4})\s*[,.]?$""", RegexOption.IGNORE_CASE)
    private val CENTS_PART = Regex("""^[,.]?\s*(\d{2})$""")

    /** OCR costuma ler o zero como "o"/"O" antes da vírgula ("o,96"). */
    private val LEADING_O = Regex("""(?<![a-zA-Z0-9])[oO](?=[,.]\d{2}(?!\d))""")

    private fun fixDigits(text: String): String = text.replace(LEADING_O, "0")

    private fun inRange(p: Double) = p >= MIN_PRICE && p <= MAX_PRICE

    private fun toPrice(intPart: String, cents: String): Double? =
        "${intPart.replace(".", "")}.$cents".toDoubleOrNull()

    /** "06.10 a 12.10", "06/10 até 12/10": período de validade, não preço. */
    private val DATE_RANGE = Regex("""\b\d{1,2}[./](?:0?[1-9]|1[0-2])\s*(?:a|ate|-)\s*\d{1,2}[./](?:0?[1-9]|1[0-2])\b""")

    /** Todos os preços válidos encontrados no texto, na ordem. */
    fun parsePrices(text: String): List<Double> {
        if (DATE_RANGE.containsMatchIn(normalize(text))) return emptyList()
        val fixed = fixDigits(text.trim())
        SPLIT_PRICE_LINE.matchEntire(fixed)?.let { m ->
            if (!fixed.contains(',') && !fixed.contains('.')) {
                val p = toPrice(m.groupValues[1], m.groupValues[2])
                return if (p != null && inRange(p)) listOf(p) else emptyList()
            }
        }
        return PRICE_REGEX.findAll(fixed)
            .mapNotNull { toPrice(it.groupValues[1], it.groupValues[2]) }
            .filter { inRange(it) }
            .toList()
    }

    /** Primeiro preço válido do texto, se houver. */
    fun parsePrice(text: String): Double? = parsePrices(text).firstOrNull()

    /** Remove os preços (e "R$", "cada") do texto, devolvendo o que sobra. */
    fun stripPrices(text: String): String {
        val fixed = fixDigits(text)
        if (SPLIT_PRICE_LINE.matches(fixed.trim())) return ""
        return fixed.replace(PRICE_REGEX, " ")
            .replace(Regex("""\br\s*\$""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\bcada\b""", RegexOption.IGNORE_CASE), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    // ------------------------------------------------------------------ Lixo

    private val LEGAL_PHRASES = listOf(
        "meramente ilustrativ", "imagens ilustrativ", "imagem ilustrativ", "foto ilustrativ", "fotos ilustrativ",
        "ofertas validas", "oferta valida", "ofertas valida", "validas para", "valido para", "validade das ofertas",
        "ofertas condicionadas", "condicionadas ao estoque", "enquanto durarem", "durarem os estoques",
        "ministerio da saude", "saude adverte", "beba com moderacao", "se beber", "nao dirija",
        "proibida a venda", "venda proibida", "menores de 18", "cartao", "credito", "debito", "parcelamos", "parcele",
        "sem juros", "reservamo", "erros graficos", "erros de impressao", "sujeito a alteracao", "sujeitos a alteracao",
        "limite de", "por cpf", "por cliente", "quantidade limitada", "somente para produtos", "do mesmo tipo",
        "mesmo tipo, marca", "consulte", "aceitamos", "televendas", "delivery", "whatsapp", "instagram", "facebook",
        "www.", ".com", "@", "siga-nos", "baixe o app", "nosso app", "preco de atacado valido", "valido somente",
        "ofertas validas para a loja", "horario de funcionamento", "funcionamento:"
    )

    /** Linhas que são só chamadas de capa ou títulos, sem produto. */
    private val HEADINGS = setOf(
        "oferta", "ofertas", "super oferta", "super ofertas", "aproveite", "imperdivel", "imperdiveis",
        "so hoje", "economia", "mais economia", "promocao", "promocoes", "preco baixo", "precos baixos",
        "jornal de ofertas", "encarte", "confira", "novidade", "lancamento", "destaque", "baixou",
        "atacado", "varejo", "preco", "precos", "por apenas", "apenas", "a partir de", "unidade", "unidades"
    )

    private val MARKET_WORDS = listOf(
        "spani", "atacadao", "dom atacadista", "atacadista", "assai", "guanabara", "carrefour", "supermercados",
        "supermercado", "atacarejo", "hiper", "mercado",
        // cidades das lojas (cabeçalhos "Resende - RJ")
        "resende", "volta redonda", "barra mansa", "itatiaia", "porto real", "rio de janeiro"
    )

    private val WEEKDAYS = Regex("""\b(segunda|terca|quarta|quinta|sexta|sabado|domingo)(-feira)?\b""")
    private val MONTHS = "jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez|janeiro|fevereiro|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro"
    private val DATE_NUMERIC = Regex("""\b(\d{1,2})\s*[/.-]\s*(\d{1,2})(?:\s*[/.-]\s*(\d{2,4}))?\b""")
    private val DATE_TEXT = Regex("""\b\d{1,2}\s*(?:a|ate|e|de)?\s*(?:\d{1,2}\s*)?(?:de\s*)?($MONTHS)\b|\b($MONTHS)\s*(?:de\s*)?(?:/\s*)?20\d{2}\b""")

    /** Tokens que sozinhos não descrevem produto: unidades, quantidades e "sabores/tipos". */
    private val FILLER_TOKENS = setOf(
        "un", "und", "unid", "unids", "unidade", "unidades", "aunidade", "aunid", "cada", "kg", "g", "gr", "grs",
        "ml", "l", "lt", "lts", "litro", "litros", "pct", "pc", "pacote", "cx", "caixa", "fardo", "fd", "dz", "duzia",
        "sabores", "sabor", "tipos", "tipo", "fragrancias", "fragrancia", "variados", "variadas", "diversos",
        "diversas", "cores", "tamanhos", "modelos", "tradicional", "tradicionais", "c", "com", "de", "da", "do",
        "e", "ou", "a", "o", "as", "os", "por", "x", "r", "rs", "s", "sai", "no", "na", "em", "kit", "leve", "pague",
        "partir", "nas", "compras", "por"
    )

    /** Palavras de unidade que, sozinhas, são descartadas (não completam nome). */
    private val UNIT_ONLY = setOf(
        "un", "und", "unid", "unidade", "unidades", "aunidade", "aunid", "cada", "kg", "g", "gr", "ml", "l", "lt",
        "pct", "cx", "r", "rs", "a", "o", "de", "por"
    )

    private val SIZE_TOKEN = Regex("""^\d+(?:[.,]\d+)?(?:kg|g|gr|grs|mg|ml|l|lt|lts|un|und|m|cm|mm)?$""")
    private val PACK_TOKEN = Regex("""^(?:c|x|cx|l|p)?\d+$""")

    /** Quantidade de letras "úteis": letras de tokens que não são unidade, quantidade ou enchimento. */
    fun usefulLetters(text: String): Int {
        val norm = normalize(text).replace(Regex("""[^a-z0-9 ]"""), " ")
        return norm.split(" ").filter { it.isNotBlank() }
            .filterNot { it in FILLER_TOKENS || SIZE_TOKEN.matches(it) || PACK_TOKEN.matches(it) }
            .filterNot { it.startsWith("unid") || it.startsWith("aunid") }
            .sumOf { tok -> tok.count { it.isLetter() } }
    }

    private fun isDate(norm: String): Boolean {
        if (WEEKDAYS.containsMatchIn(norm)) return true
        if (DATE_TEXT.containsMatchIn(norm)) return true
        return DATE_NUMERIC.findAll(norm).any { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            // "1,5" ou "4.99" não são datas: exige separador / ou - ou ano
            val sep = m.value.contains('/') || m.value.contains('-') || m.groupValues[3].isNotEmpty()
            sep && d in 1..31 && mo in 1..12
        }
    }

    private fun stripMarketNames(norm: String, extraNames: Collection<String>): String {
        var out = norm
        for (w in MARKET_WORDS + extraNames.map { normalize(it) }.filter { it.length >= 3 }) {
            out = out.replace(w, " ")
        }
        return out.replace("\\s+".toRegex(), " ").trim()
    }

    /** Aviso legal, data, título, nome do mercado ou outro texto que nunca é produto. */
    fun isNoise(text: String, marketNames: Collection<String> = emptyList()): Boolean {
        val norm = normalize(text)
        if (norm.isBlank()) return true
        if (LEGAL_PHRASES.any { norm.contains(it) }) return true
        val bare = norm.replace(Regex("""[^a-z0-9 ]"""), " ").replace("\\s+".toRegex(), " ").trim()
        if (bare in HEADINGS) return true
        if (isDate(norm) && usefulLetters(DATE_TEXT.replace(WEEKDAYS.replace(norm, " "), " ")) < 4) return true
        val withoutMarket = stripMarketNames(norm, marketNames)
        if (withoutMarket != norm && usefulLetters(withoutMarket) < 4) return true
        return false
    }

    /**
     * Descrição que não serve como nome de produto: menos de 3 letras úteis, só unidade/quantidade
     * ("aunidade", "90g Sabores Un"), só número/preço ("o,96") ou ruído ([isNoise]).
     */
    fun isJunkDescription(text: String, marketNames: Collection<String> = emptyList()): Boolean {
        val withoutPrice = stripPrices(text)
        if (usefulLetters(withoutPrice) < 3) return true
        if (isNoise(text, marketNames)) return true
        return false
    }

    // ------------------------------------------------------------------ Modificadores (atacado, leve/pague)

    private val WHOLESALE_MIN = Regex("""a\s*partir\s*de\s*(\d{1,3})""")
    private val WHOLESALE_HINT = Regex("""a\s*partir\s*de|\bsai\s*a\b|unidade\s*sai|atacado|no\s*atacado|preco\s*de\s*atacado""")
    private val LEVE_PAGUE = Regex("""leve\s*(\d{1,3})\s*,?\s*pague\s*(\d{1,3})""")
    private val PACK_UNIT_INFO = Regex("""nesta\s*embalagem|o\s*kg\s*sai|o\s*litro\s*sai|o\s*l\s*sai|a\s*unidade\s*sai\s*a\s*:?\s*$""")
    private val MODIFIER_STRIP = Regex(
        """(?:nas\s*compras\s*)?a\s*partir\s*de\s*\d{1,3}\s*(?:un\w*)?\s*,?\s*(?:a\s*unidade\s*sai\s*a\s*:?)?|leve\s*\d{1,3}\s*,?\s*pague\s*\d{1,3}|a\s*unidade\s*sai\s*a\s*:?|unidade\s*:|\bvarejo\b|\batacado\b""",
        RegexOption.IGNORE_CASE
    )

    private enum class Kind { PRICE, MODIFIER, TEXT, FILLER, DISCARD }

    private class Item(
        val kind: Kind,
        val text: String,
        val box: Box?,
        val order: Int,
        val prices: List<Double> = emptyList(),
        val wholesale: Boolean = false,
        val minQty: Int? = null,
        val levePague: String? = null,
        /** Preço de "nesta embalagem a unidade sai a": informativo, não é a oferta. */
        val packInfo: Boolean = false
    )

    /** Classifica uma linha crua, separando preço embutido do texto. Pode gerar até 2 itens. */
    private fun classify(line: OcrLine, order: Int, marketNames: Collection<String>): List<Item> {
        val raw = line.text.trim()
        if (raw.isEmpty()) return emptyList()
        val norm = normalize(raw)
        val prices = parsePrices(raw)
        val levePague = LEVE_PAGUE.find(norm)?.let { "leve ${it.groupValues[1]} pague ${it.groupValues[2]}" }
        val minQty = WHOLESALE_MIN.find(norm)?.groupValues?.get(1)?.toIntOrNull()
        val packInfo = PACK_UNIT_INFO.containsMatchIn(norm) && (norm.contains("embalagem") || norm.contains("kg sai") || norm.contains("litro"))
        val wholesale = WHOLESALE_HINT.containsMatchIn(norm) && !packInfo

        val rest = stripPrices(MODIFIER_STRIP.replace(raw, " ")).replace("\\s+".toRegex(), " ").trim()
        val out = mutableListOf<Item>()

        if (packInfo) {
            // "NESTA EMBALAGEM A UNIDADE SAI A: 6,99": não é preço de oferta nem descrição
            return listOf(Item(Kind.DISCARD, raw, line.box, order))
        }
        if (LEGAL_PHRASES.any { norm.contains(it) }) return listOf(Item(Kind.DISCARD, raw, line.box, order))

        val restUseful = usefulLetters(rest)
        val textIsDescription = rest.isNotBlank() && restUseful >= 3 && !isNoise(rest, marketNames)
        val hasModifier = wholesale || levePague != null

        if (prices.isNotEmpty()) {
            out += Item(Kind.PRICE, raw, line.box, order, prices, wholesale, minQty, levePague)
            if (textIsDescription) out += Item(Kind.TEXT, rest, line.box, order)
            return out
        }
        if (textIsDescription) {
            out += Item(Kind.TEXT, rest, line.box, order, wholesale = wholesale, minQty = minQty, levePague = levePague)
            return out
        }
        if (hasModifier) return listOf(Item(Kind.MODIFIER, raw, line.box, order, wholesale = wholesale, minQty = minQty, levePague = levePague))
        val restTokens = normalize(rest).replace(Regex("""[^a-z0-9 ]"""), " ").split(" ").filter { it.isNotBlank() }
        val onlyUnits = restTokens.all { t -> t in UNIT_ONLY || t.startsWith("unid") || t.startsWith("aunid") || t.all { ch -> ch.isDigit() } }
        if (restTokens.isNotEmpty() && !onlyUnits && !isNoise(raw, marketNames)) {
            // "90g Sabores Un": pode completar um nome, mas nunca vira produto sozinho
            return listOf(Item(Kind.FILLER, rest, line.box, order))
        }
        return listOf(Item(Kind.DISCARD, raw, line.box, order))
    }

    /**
     * Junta preços com centavos sobrescritos que o OCR lê em linhas separadas:
     * "4" (grande) + "99" (pequeno, à direita e no alto).
     */
    fun mergeSplitPrices(lines: List<OcrLine>): List<OcrLine> {
        val used = BooleanArray(lines.size)
        val out = mutableListOf<OcrLine>()
        for (i in lines.indices) {
            if (used[i]) continue
            val a = lines[i]
            val ia = INTEGER_PART.matchEntire(a.text.trim())
            val boxA = a.box
            if (ia != null && boxA != null) {
                var best = -1
                var bestGap = Int.MAX_VALUE
                for (j in lines.indices) {
                    if (j == i || used[j]) continue
                    val b = lines[j]
                    val boxB = b.box ?: continue
                    if (CENTS_PART.matchEntire(b.text.trim()) == null) continue
                    val gap = boxB.left - boxA.right
                    val h = boxA.height.coerceAtLeast(1)
                    val nearRight = gap >= -h / 3 && gap <= h
                    val upperHalf = boxB.top >= boxA.top - h / 3 && boxB.top <= boxA.centerY
                    if (nearRight && upperHalf && boxB.height <= boxA.height && gap < bestGap) {
                        best = j; bestGap = gap
                    }
                }
                if (best >= 0) {
                    val cents = CENTS_PART.matchEntire(lines[best].text.trim())!!.groupValues[1]
                    used[best] = true
                    used[i] = true
                    out += OcrLine("${ia.groupValues[1]},$cents", boxA.union(lines[best].box!!))
                    continue
                }
            }
            out += a
        }
        return out
    }

    // ------------------------------------------------------------------ Agrupamento e associação

    private class Group(val items: MutableList<Item>) {
        var box: Box? = items.first().box
        fun add(item: Item) {
            items += item
            val b = item.box
            box = if (box == null) b else if (b == null) box else box!!.union(b)
        }
        val hasText get() = items.any { it.kind == Kind.TEXT }
    }

    private fun median(values: List<Int>): Int = if (values.isEmpty()) 0 else values.sorted()[values.size / 2]

    /** Processa uma página. */
    fun parsePage(lines: List<OcrLine>, page: Int = 0, marketNames: Collection<String> = emptyList()): List<ParsedOffer> {
        if (lines.isEmpty()) return emptyList()
        val merged = mergeSplitPrices(lines)
        val items = merged.flatMapIndexed { idx, l -> classify(l, idx, marketNames) }
        val hasBoxes = items.isNotEmpty() && items.all { it.box != null }
        return if (hasBoxes) parseSpatial(items, page) else parseSequential(items, page)
    }

    /** Processa todas as páginas e remove duplicatas (mesma descrição e preço). */
    fun parse(pages: List<OcrPage>, marketNames: Collection<String> = emptyList()): List<ParsedOffer> {
        val all = pages.flatMap { parsePage(it.lines, it.index, marketNames) }
        val seen = HashSet<String>()
        return all.filter { seen.add(normalize(it.description) + "|" + it.price) }
    }

    private fun parseSpatial(items: List<Item>, page: Int): List<ParsedOffer> {
        val descItems = items.filter { it.kind == Kind.TEXT || it.kind == Kind.FILLER }
        val lineH = median(descItems.mapNotNull { it.box?.height }).coerceAtLeast(1)

        // 1) agrupar linhas de descrição vizinhas (union-find simples)
        val parent = IntArray(descItems.size) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) r = parent[r]; return r }
        for (i in descItems.indices) for (j in i + 1 until descItems.size) {
            val a = descItems[i].box!!
            val b = descItems[j].box!!
            val h = max(a.height, b.height).coerceAtLeast(1)
            val stacked = a.verticalGap(b) <= (h * 0.9).toInt() && a.horizontalGap(b) == 0 &&
                abs(a.height - b.height) <= h * 0.6
            val sameRow = a.verticalOverlap(b) >= min(a.height, b.height) / 2 && a.horizontalGap(b) <= (h * 0.8).toInt()
            if (stacked || sameRow) parent[find(j)] = find(i)
        }
        val groupsByRoot = LinkedHashMap<Int, Group>()
        descItems.indices.sortedBy { descItems[it].order }.forEach { i ->
            val r = find(i)
            val g = groupsByRoot[r]
            if (g == null) groupsByRoot[r] = Group(mutableListOf(descItems[i])) else g.add(descItems[i])
        }
        val groups = groupsByRoot.values.filter { it.hasText }
        if (groups.isEmpty()) return emptyList()

        val maxDist = lineH * 9.0

        fun distance(g: Box, p: Box): Double {
            val dx = g.horizontalGap(p).toDouble()
            return when {
                p.top >= g.bottom - g.height / 4 -> dx + max(0, p.top - g.bottom) // abaixo
                p.verticalOverlap(g) > 0 -> dx * 1.1 + (if (p.left < g.left) dx else 0.0) // ao lado
                else -> dx + (g.top - p.bottom).coerceAtLeast(0) * 2.5 // acima (improvável)
            }
        }

        // 2) cada preço/modificador vai para o grupo mais próximo
        data class Assigned(val item: Item, val dist: Double)
        val assigned = HashMap<Group, MutableList<Assigned>>()
        val floating = items.filter { x -> x.kind == Kind.PRICE || x.kind == Kind.MODIFIER }
        for (pi in floating) {
            var best: Group? = null
            var bestD = Double.MAX_VALUE
            for (g in groups) {
                val d = distance(g.box!!, pi.box!!)
                if (d < bestD) { bestD = d; best = g }
            }
            if (best != null && bestD <= maxDist) assigned.getOrPut(best) { mutableListOf() }.add(Assigned(pi, bestD))
        }

        return groups.mapNotNull { g ->
            val list = assigned[g].orEmpty()
            val priceItems = list.filter { it.item.kind == Kind.PRICE }.sortedBy { it.dist }
            val modifiers = list.map { it.item } + g.items
            buildOffer(g, priceItems.map { it.item }, modifiers, page)
        }
    }

    private fun parseSequential(items: List<Item>, page: Int): List<ParsedOffer> {
        val out = mutableListOf<ParsedOffer>()
        var current: Group? = null
        val prices = mutableListOf<Item>()
        val mods = mutableListOf<Item>()
        fun flush() {
            val g = current
            if (g != null && g.hasText) buildOffer(g, prices.toList(), mods + g.items, page)?.let { out += it }
            current = null; prices.clear(); mods.clear()
        }
        for (item in items.sortedBy { x -> x.order }) {
            when (item.kind) {
                Kind.TEXT, Kind.FILLER -> {
                    if (prices.isNotEmpty()) flush()
                    val c = current
                    if (c == null) current = Group(mutableListOf(item)) else c.add(item)
                }
                Kind.PRICE -> if (current != null) prices.add(item)
                Kind.MODIFIER -> mods.add(item)
                Kind.DISCARD -> Unit
            }
        }
        flush()
        return out
    }

    private fun buildOffer(g: Group, priceItems: List<Item>, modifiers: List<Item>, page: Int): ParsedOffer? {
        if (priceItems.isEmpty()) return null
        val description = g.items.sortedBy { it.order }.joinToString(" ") { it.text }
            .replace(Regex("""\s+(?:un|und|unid)\.?$""", RegexOption.IGNORE_CASE), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
        if (usefulLetters(description) < 3) return null

        // até dois preços, os mais próximos
        val candidates = priceItems.flatMap { it.prices }.distinct().take(2)
        val closest = candidates.first()
        val wholesaleFlag = modifiers.any { it.wholesale } || priceItems.any { it.wholesale }
        val minQty = modifiers.firstNotNullOfOrNull { it.minQty } ?: priceItems.firstNotNullOfOrNull { it.minQty }
        val levePague = modifiers.firstNotNullOfOrNull { it.levePague } ?: priceItems.firstNotNullOfOrNull { it.levePague }

        var price = closest
        var retail: Double? = null
        var note = ""
        if (candidates.size == 2) {
            val lo = min(candidates[0], candidates[1])
            val hi = max(candidates[0], candidates[1])
            // varejo x atacado costuma diferir pouco; diferença grande = preço de outro produto
            if (lo / hi >= 0.6) {
                price = lo; retail = hi
                note = "atacado"
            }
        }
        if (note.isEmpty() && wholesaleFlag) note = "atacado"
        if (note == "atacado" && minQty != null) note = "atacado a partir de $minQty un"
        if (levePague != null) note = if (note.isEmpty()) levePague else "$note, $levePague"

        return ParsedOffer(
            description = description,
            price = price,
            retailPrice = retail,
            note = note,
            page = page,
            box = g.box
        )
    }
}
