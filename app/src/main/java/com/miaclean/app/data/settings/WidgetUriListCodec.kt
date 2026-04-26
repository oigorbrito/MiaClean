package com.miaclean.app.data.settings

/**
 * Codec for the widget's top-thumbnail `content://` URI list persisted in DataStore. The list is
 * bounded (at most `MAX_ENTRIES` in the updater) so a compact `|`-delimited string is cheaper
 * than pulling in a JSON serializer for a single field.
 *
 * `|` is used as the separator because MediaStore content URIs (the only URIs the widget ever
 * writes) don't contain pipes — they follow `content://media/external/images/media/<id>` with
 * numeric IDs. Blank / malformed entries are dropped on decode so a corrupted prefs file degrades
 * to "no thumbnails this cycle" and the next scan's [com.miaclean.app.widget.WidgetSummaryUpdater]
 * call rebuilds the list, rather than crashing the widget render.
 */
internal object WidgetUriListCodec {

    private const val DELIMITER = "|"

    fun encode(uris: List<String>): String =
        uris.filter { it.isNotBlank() }.joinToString(separator = DELIMITER)

    fun decode(encoded: String?): List<String> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.split(DELIMITER).filter { it.isNotBlank() }
    }
}
