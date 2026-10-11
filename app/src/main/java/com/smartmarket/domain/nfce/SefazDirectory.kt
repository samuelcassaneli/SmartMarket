package com.smartmarket.domain.nfce

/**
 * Endereços públicos das SEFAZ para NFC-e, por código IBGE da UF (cUF).
 *
 * - [State.keyQueryUrl]: "urlChave" impressa no DANFE NFC-e (consulta pela chave de acesso).
 *   Todas as que testamos pedem captcha ou login, então a consulta por chave abre no WebView.
 * - [State.qrBaseUrl]: endereço do QR Code (produção), usado quando o QR traz só o parâmetro `p`.
 */
object SefazDirectory {

    data class State(
        val cUF: Int,
        val uf: String,
        val name: String,
        val keyQueryUrl: String,
        val qrBaseUrl: String,
        /** URL que devolve a nota direto pela chave, sem captcha (nenhuma confirmada até agora). */
        val directKeyUrl: ((String) -> String)? = null
    )

    val STATES: List<State> = listOf(
        State(11, "RO", "Rondônia", "http://www.nfce.sefin.ro.gov.br/home.jsp", "http://www.nfce.sefin.ro.gov.br/consultanfce/consulta.jsp"),
        State(12, "AC", "Acre", "http://www.sefaznet.ac.gov.br/nfce/consulta", "http://www.sefaznet.ac.gov.br/nfce/qrcode"),
        State(13, "AM", "Amazonas", "http://www.sefaz.am.gov.br/nfce/consulta", "http://sistemas.sefaz.am.gov.br/nfceweb/consultarNFCe.jsp"),
        State(14, "RR", "Roraima", "https://www.sefaz.rr.gov.br/nfce/consulta", "https://www.sefaz.rr.gov.br/nfce/servlet/qrcode"),
        State(15, "PA", "Pará", "https://app.sefa.pa.gov.br/consulta-nfce/#/consulta", "https://appnfc.sefa.pa.gov.br/portal/view/consultas/nfce/nfceForm.seam"),
        State(16, "AP", "Amapá", "http://www.sefaz.ap.gov.br/nfce/consulta", "https://www.sefaz.ap.gov.br/nfce/nfcep.php"),
        State(17, "TO", "Tocantins", "http://www.sefaz.to.gov.br/nfce/consulta", "http://www.sefaz.to.gov.br/nfce/qrcode"),
        State(21, "MA", "Maranhão", "https://www.sefaz.ma.gov.br/nfce/consulta", "http://www.nfce.sefaz.ma.gov.br/portal/consultarNFCe.jsp"),
        State(22, "PI", "Piauí", "http://www.sefaz.pi.gov.br/nfce/consulta", "http://www.sefaz.pi.gov.br/nfce/qrcode"),
        State(23, "CE", "Ceará", "http://www.sefaz.ce.gov.br/nfce/consulta", "http://nfce.sefaz.ce.gov.br/pages/ShowNFCe.html"),
        State(24, "RN", "Rio Grande do Norte", "http://www.set.rn.gov.br/nfce/consulta", "http://nfce.set.rn.gov.br/consultarNFCe.aspx"),
        State(25, "PB", "Paraíba", "http://www.sefaz.pb.gov.br/nfce/consulta", "http://www.sefaz.pb.gov.br/nfce"),
        State(26, "PE", "Pernambuco", "https://nfce.sefaz.pe.gov.br/nfce/consulta", "http://nfce.sefaz.pe.gov.br/nfce/consulta"),
        State(27, "AL", "Alagoas", "https://nfce.sefaz.al.gov.br/consultaNFCe.htm", "http://nfce.sefaz.al.gov.br/QRCode/consultarNFCe.jsp"),
        State(28, "SE", "Sergipe", "http://www.nfce.se.gov.br/nfce/consulta", "http://www.nfce.se.gov.br/nfce/qrcode"),
        State(29, "BA", "Bahia", "https://www.sefaz.ba.gov.br/nfce/consulta", "http://nfe.sefaz.ba.gov.br/servicos/nfce/qrcode.aspx"),
        State(31, "MG", "Minas Gerais", "https://portalsped.fazenda.mg.gov.br/portalnfce", "https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml"),
        State(32, "ES", "Espírito Santo", "http://www.sefaz.es.gov.br/nfce/consulta", "http://app.sefaz.es.gov.br/ConsultaNFCe/qrcode.aspx"),
        State(33, "RJ", "Rio de Janeiro", "https://www.fazenda.rj.gov.br/nfce/consulta", "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode"),
        State(35, "SP", "São Paulo", "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica", "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx"),
        State(41, "PR", "Paraná", "http://www.fazenda.pr.gov.br/nfce/consulta", "http://www.fazenda.pr.gov.br/nfce/qrcode"),
        State(42, "SC", "Santa Catarina", "https://sat.sef.sc.gov.br/nfce/consulta", "https://sat.sef.sc.gov.br/nfce/consulta"),
        State(43, "RS", "Rio Grande do Sul", "http://www.sefaz.rs.gov.br/nfce/consulta", "https://www.sefaz.rs.gov.br/NFCE/NFCE-COM.aspx"),
        State(50, "MS", "Mato Grosso do Sul", "http://www.dfe.ms.gov.br/nfce/consulta", "http://www.dfe.ms.gov.br/nfce/qrcode"),
        State(51, "MT", "Mato Grosso", "http://www.sefaz.mt.gov.br/nfce/consultanfce", "http://www.sefaz.mt.gov.br/nfce/consultanfce"),
        State(52, "GO", "Goiás", "http://www.sefaz.go.gov.br/nfce/consulta", "http://nfe.sefaz.go.gov.br/nfeweb/sites/nfce/danfeNFCe"),
        State(53, "DF", "Distrito Federal", "http://www.fazenda.df.gov.br/nfce/consulta", "http://www.fazenda.df.gov.br/nfce/qrcode")
    )

    private val byCode = STATES.associateBy { it.cUF }

    fun state(cUF: Int): State? = byCode[cUF]

    /** Só aceitamos páginas de órgãos públicos: host igual a `gov.br` ou terminado em `.gov.br`. */
    fun isOfficialHost(host: String?): Boolean {
        val h = host?.trim()?.lowercase()?.trimEnd('.') ?: return false
        if (h.isEmpty()) return false
        return h == "gov.br" || h.endsWith(".gov.br")
    }

    private val URL_HOST = Regex("""^https?://([^/?#\\]+)""", RegexOption.IGNORE_CASE)

    /** Extrai o host de uma URL http/https (null se o esquema não for http/https ou houver credenciais). */
    fun hostOf(url: String): String? {
        val authority = URL_HOST.find(url.trim())?.groupValues?.get(1) ?: return null
        if (authority.contains('@')) return null
        return authority.substringBefore(':').lowercase()
    }

    /** URL http/https apontando para host oficial. */
    fun isOfficialUrl(url: String?): Boolean = url != null && isOfficialHost(hostOf(url))

    fun ufOf(cUF: Int): String? = byCode[cUF]?.uf

    /** Página de consulta pela chave (abre no WebView; o usuário cola a chave e resolve o captcha). */
    fun keyQueryUrl(key: NfceKey): String = state(key.cUF)?.keyQueryUrl ?: "https://www.nfce.fazenda.gov.br/portal/consultarNFCe.aspx"

    /** URL de consulta direta pela chave, sem captcha, quando o estado oferece (null = usar WebView). */
    fun directKeyUrl(key: NfceKey): String? = state(key.cUF)?.directKeyUrl?.invoke(key.digits)

    /** Monta a URL do QR quando o texto lido traz só o parâmetro `p`. */
    fun qrUrl(key: NfceKey, pParam: String): String? =
        state(key.cUF)?.qrBaseUrl?.let { "$it?p=$pParam" }
}
