package cc.ioctl.tdlib.tlrpc.api.msg

import com.google.gson.JsonObject

class FormattedTextBuilder : CharSequence {

    companion object {
        const val Bold = "textEntityTypeBold"
        const val Italic = "textEntityTypeItalic"
        const val Underline = "textEntityTypeUnderline"
        const val Strikethrough = "textEntityTypeStrikethrough"

        const val Pre = "textEntityTypePre"
        const val Code = "textEntityTypeCode"
        const val Spoiler = "textEntityTypeSpoiler"

        const val PreCode = "textEntityTypePreCode"
        const val TextUrl = "textEntityTypeTextUrl"
        const val MentionName = "textEntityTypeMentionName"
    }

    data class Span(
        val type: String, val offset: Int, val length: Int, val attrName: String? = null, val attrValue: Any? = null
    )

    data class PendingSpannedText(
        val type: String, val text: String, val attrValue: Any? = null
    )

    private val mText = StringBuilder()
    private val mSpans = ArrayList<Span>(1)

    private var mLastStart: Int = 0
    private val mCurrentInclusiveSpans: MutableSet<String> = HashSet(1)

    override val length: Int get() = mText.length

    override fun get(index: Int): Char {
        return mText[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        return mText.subSequence(startIndex, endIndex)
    }

    override fun toString(): String {
        return mText.toString()
    }

    fun clear() {
        mText.clear()
        mSpans.clear()
        mCurrentInclusiveSpans.clear()
        mLastStart = 0
    }

    fun append(text: String) {
        mText.append(text)
    }

    private fun resolveDelta() {
        if (mLastStart == length) {
            return
        }
        val start = mLastStart
        val end = length
        for (type in mCurrentInclusiveSpans) {
            mSpans.add(Span(type, start, end - start))
        }
        mLastStart = length
    }

    private fun beginSpan(type: String) {
        if (type != Bold && type != Italic && type != Underline && type != Strikethrough) {
            throw IllegalArgumentException("Only bold, italic, underline and strikethrough are supported, but got $type")
        }
        if (mCurrentInclusiveSpans.contains(type)) {
            throw IllegalStateException("Span of type $type is already started")
        }
        resolveDelta()
        mCurrentInclusiveSpans.add(type)
    }

    private fun endSpan(type: String) {
        if (!mCurrentInclusiveSpans.contains(type)) {
            throw IllegalStateException("Span of type $type is not started")
        }
        resolveDelta()
        mCurrentInclusiveSpans.remove(type)
    }

    fun beginBold() {
        beginSpan(Bold)
    }

    fun endBold() {
        endSpan(Bold)
    }

    fun beginItalic() {
        beginSpan(Italic)
    }

    fun endItalic() {
        endSpan(Italic)
    }

    fun beginUnderline() {
        beginSpan(Underline)
    }

    fun endUnderline() {
        endSpan(Underline)
    }

    fun beginStrikethrough() {
        beginSpan(Strikethrough)
    }

    fun endStrikethrough() {
        endSpan(Strikethrough)
    }

    fun appendBold(text: String) {
        if (mCurrentInclusiveSpans.contains(Bold)) {
            append(text)
        } else {
            beginSpan(Bold)
            append(text)
            endSpan(Bold)
        }
    }

    fun appendItalic(text: String) {
        if (mCurrentInclusiveSpans.contains(Italic)) {
            append(text)
        } else {
            beginSpan(Italic)
            append(text)
            endSpan(Italic)
        }
    }

    fun appendUnderline(text: String) {
        if (mCurrentInclusiveSpans.contains(Underline)) {
            append(text)
        } else {
            beginSpan(Underline)
            append(text)
            endSpan(Underline)
        }
    }

    fun appendStrikethrough(text: String) {
        if (mCurrentInclusiveSpans.contains(Strikethrough)) {
            append(text)
        } else {
            beginSpan(Strikethrough)
            append(text)
            endSpan(Strikethrough)
        }
    }

    fun appendPre(text: String) {
        resolveDelta()
        val start = length
        append(text)
        val end = length
        mLastStart = end
        mSpans.add(Span(Pre, start, end - start))
    }

    fun appendCode(text: String) {
        resolveDelta()
        val start = length
        append(text)
        val end = length
        mLastStart = end
        mSpans.add(Span(Code, start, end - start))
    }

    fun appendPreCode(text: String, language: String) {
        resolveDelta()
        val start = length
        append(text)
        val end = length
        mLastStart = end
        mSpans.add(Span(PreCode, start, end - start, attrName = "language", attrValue = language))
    }

    fun appendSpoiler(text: String) {
        resolveDelta()
        val start = length
        append(text)
        val end = length
        mLastStart = end
        mSpans.add(Span(Spoiler, start, end - start))
    }

    fun appendTextUrl(text: String, url: String) {
        resolveDelta()
        val start = length
        append(text)
        val end = length
        mLastStart = end
        mSpans.add(Span(TextUrl, start, end - start, attrName = "url", attrValue = url))
    }

    fun appendMentionName(text: String, userId: Long) {
        resolveDelta()
        val start = length
        append(text)
        val end = length
        mLastStart = end
        mSpans.add(Span(MentionName, start, end - start, attrName = "user_id", attrValue = userId))
    }

    private fun translateSpan(span: Span): FormattedText.TextEntity {
        return FormattedText.TextEntity(span.offset, span.length, JsonObject().apply {
            addProperty("@type", span.type)
            if (span.attrName != null) {
                when (span.attrValue) {
                    is Number -> {
                        addProperty(span.attrName, span.attrValue.toLong())
                    }
                    is String -> {
                        addProperty(span.attrName, span.attrValue)
                    }
                    else -> {
                        throw IllegalArgumentException("Unsupported attribute ${span.attrName} : ${span.attrValue?.javaClass?.name}")
                    }
                }
            }
        })
    }

    fun build(): FormattedText {
        resolveDelta()
        return FormattedText(mText.toString(), mSpans.map { translateSpan(it) }.toTypedArray())
    }

    operator fun plus(other: String): FormattedTextBuilder {
        append(other)
        return this
    }

    operator fun plus(other: PendingSpannedText): FormattedTextBuilder {
        when (other.type) {
            Bold -> appendBold(other.text)
            Italic -> appendItalic(other.text)
            Underline -> appendUnderline(other.text)
            Strikethrough -> appendStrikethrough(other.text)
            Pre -> appendPre(other.text)
            Code -> appendCode(other.text)
            PreCode -> appendPreCode(other.text, other.attrValue as String)
            Spoiler -> appendSpoiler(other.text)
            TextUrl -> appendTextUrl(other.text, other.attrValue as String)
            MentionName -> appendMentionName(other.text, other.attrValue as Long)
            else -> throw IllegalArgumentException("Unsupported type ${other.type}")
        }
        return this
    }

    fun Bold(text: String): PendingSpannedText {
        return PendingSpannedText(Bold, text)
    }

    fun Italic(text: String): PendingSpannedText {
        return PendingSpannedText(Italic, text)
    }

    fun Underline(text: String): PendingSpannedText {
        return PendingSpannedText(Underline, text)
    }

    fun Strikethrough(text: String): PendingSpannedText {
        return PendingSpannedText(Strikethrough, text)
    }

    fun Pre(text: String): PendingSpannedText {
        return PendingSpannedText(Pre, text)
    }

    fun Code(text: String): PendingSpannedText {
        return PendingSpannedText(Code, text)
    }

    fun PreCode(text: String, language: String): PendingSpannedText {
        return PendingSpannedText(PreCode, text, language)
    }

    fun Spoiler(text: String): PendingSpannedText {
        return PendingSpannedText(Spoiler, text)
    }

    fun TextUrl(text: String, url: String): PendingSpannedText {
        return PendingSpannedText(TextUrl, text, url)
    }

    fun MentionName(text: String, userId: Long): PendingSpannedText {
        return PendingSpannedText(MentionName, text, userId)
    }
}
