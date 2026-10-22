package com.omer.belestiviclone.shared.catalog

private fun String.asciiLowercase(): String = map {
    if (it in 'A'..'Z') it + ('a' - 'A') else it
}.joinToString("")

/** Stored address retains everything inside the form's trimmed boundaries. */
@ConsistentCopyVisibility
data class Address private constructor(val raw: String) {
    override fun toString(): String = "Address([redacted])"

    companion object {
        fun fromForm(text: String): Address = Address(text.trim())
    }
}

@ConsistentCopyVisibility
data class AddressKey internal constructor(private val value: String) {
    override fun toString(): String = "AddressKey([redacted])"
}

/** Only URL-backed list registration is in this slice; no account/file duplicate policy. */
enum class SourceAddressKind { M3U_URL }
data class SourceAddressKey(val kind: SourceAddressKind, val address: AddressKey)

data class Header(val name: String, val value: String) {
    override fun toString(): String = "Header([redacted])"
}

data class NamedPlaybackOption(val name: String, val value: String) {
    override fun toString(): String = "NamedPlaybackOption([redacted])"
}

data class DrmDefinition(
    val scheme: String,
    val licenseAddress: Address? = null,
    val licenseHeaders: List<Header> = emptyList(),
    val keyReference: String? = null,
    val options: List<NamedPlaybackOption> = emptyList(),
) {
    override fun toString(): String = "DrmDefinition([redacted])"
}

/**
 * All necessary options must be supplied losslessly. Unknown necessary directives, unresolved
 * secrets, or information the adapter cannot represent MUST set a block flag. No provider hint
 * participates in equality. This is an internal value, not a log/backup/transport DTO.
 */
data class PlaybackDefinition(
    val address: Address,
    val headers: List<Header> = emptyList(),
    val mimeType: String? = null,
    val protocol: String? = null,
    val drm: DrmDefinition? = null,
    val authorityScope: String? = null,
    val options: List<NamedPlaybackOption> = emptyList(),
    val unknownRequiredDirectives: List<String> = emptyList(),
    val isLossless: Boolean = true,
) {
    override fun toString(): String = "PlaybackDefinition([redacted])"
}

@ConsistentCopyVisibility
data class HeaderValues internal constructor(val name: String, private val values: List<String>) {
    override fun toString(): String = "HeaderValues([redacted])"
}

@ConsistentCopyVisibility
data class DrmKey internal constructor(
    private val scheme: String,
    private val licenseAddress: AddressKey?,
    private val licenseHeaders: List<HeaderValues>,
    private val keyReference: String?,
    private val options: List<NamedPlaybackOption>,
) {
    override fun toString(): String = "DrmKey([redacted])"
}

@ConsistentCopyVisibility
data class PlaybackKey internal constructor(
    val version: Int = 1,
    private val address: AddressKey,
    private val headers: List<HeaderValues>,
    private val mimeType: String?,
    private val protocol: String?,
    private val drm: DrmKey?,
    private val authorityScope: String?,
    private val options: List<NamedPlaybackOption>,
) {
    override fun toString(): String = "PlaybackKey(v$version, [redacted])"
}

object ComparisonV1 {
    private val scheme = Regex("^[Hh][Tt][Tt][Pp][Ss]?://")
    private val dnsLabel = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")
    private const val headerPunctuation = "!#$%&'*+-.^_`|~"

    /**
     * A small comparison scanner, not a URL/network parser. Only an unambiguous HTTP(S) URL
     * with an ASCII DNS authority gets scheme/host case folding. Exotic authorities retain
     * their complete spelling, including scheme case. Obvious malformed inputs are blocked.
     */
    fun addressKey(address: Address): AddressKey? {
        val raw = address.raw
        val prefix = scheme.find(raw)?.value ?: return null
        if (raw.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f || it in "\\<>\"{}|^`" }) {
            return null
        }
        raw.indices.filter { raw[it] == '%' }.forEach { index ->
            if (index + 2 >= raw.length || raw[index + 1] !in "0123456789abcdefABCDEF" ||
                raw[index + 2] !in "0123456789abcdefABCDEF") return null
        }
        val authorityEnd = raw.indexOfAny(charArrayOf('/', '?', '#'), prefix.length)
            .let { if (it < 0) raw.length else it }
        val authority = raw.substring(prefix.length, authorityEnd)
        if (authority.isEmpty()) return null
        val hostStart = authority.lastIndexOf('@') + 1
        val hostPort = authority.substring(hostStart)
        if (hostPort.isEmpty()) return null
        // No unverified IDN/IPv6/user-info/multiple-colon canonicalization.
        if (authority.count { it == '@' } > 1 || hostPort.startsWith('[') ||
            hostPort.count { it == ':' } > 1 || hostPort.any { it.code > 0x7f }) return AddressKey(raw)
        val colon = hostPort.indexOf(':')
        val host = if (colon < 0) hostPort else hostPort.substring(0, colon)
        if (colon >= 0) {
            val port = hostPort.substring(colon + 1)
            if (port.isEmpty() || port.any { it !in '0'..'9' } ||
                port.toIntOrNull()?.let { it in 0..65535 } != true) return AddressKey(raw)
        }
        val labels = host.removeSuffix(".").split('.')
        if (host.isEmpty() || host.length > 254 || labels.any { !dnsLabel.matches(it) }) {
            return AddressKey(raw)
        }
        // Literal IPv4 and other numeric-only hosts stay exact as unusual authorities.
        if (host.all { it in '0'..'9' || it == '.' }) return AddressKey(raw)
        val normalized = prefix.asciiLowercase() + authority.substring(0, hostStart) +
            host.asciiLowercase() + (if (colon < 0) "" else hostPort.substring(colon)) +
            raw.substring(authorityEnd)
        return AddressKey(normalized)
    }

    fun sourceKey(kind: SourceAddressKind, address: Address): SourceAddressKey? =
        addressKey(address)?.let { SourceAddressKey(kind, it) }

    fun playbackKey(definition: PlaybackDefinition): PlaybackKey? {
        if (!definition.isLossless || definition.unknownRequiredDirectives.isNotEmpty()) return null
        val address = addressKey(definition.address) ?: return null
        val headers = headerKey(definition.headers) ?: return null
        val drm = definition.drm?.let {
            val licenseAddress = it.licenseAddress?.let { license -> addressKey(license) ?: return null }
            DrmKey(it.scheme, licenseAddress, headerKey(it.licenseHeaders) ?: return null,
                it.keyReference, it.options.toList())
        }
        return PlaybackKey(address = address, headers = headers, mimeType = definition.mimeType,
            protocol = definition.protocol, drm = drm, authorityScope = definition.authorityScope,
            options = definition.options.toList())
    }

    private fun headerKey(headers: List<Header>): List<HeaderValues>? {
        if (headers.any { header -> header.name.isEmpty() || header.name.any {
                it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it !in headerPunctuation
            } }) return null
        return headers.groupBy { it.name.asciiLowercase() }.entries.sortedBy { it.key }
            .map { (name, entries) -> HeaderValues(name, entries.map { it.value }) }
    }
}
