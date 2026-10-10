package be.equily.app.domain

object Csv {
    fun cell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        val guarded = if (value.trimStart { it.isWhitespace() || it.isISOControl() || it == '\uFEFF' }.firstOrNull() in listOf('=', '+', '-', '@')) "'$escaped" else escaped
        return "\"$guarded\""
    }

    fun row(values: List<String>) = values.joinToString(";", transform = ::cell) + "\r\n"
}
