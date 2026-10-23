package com.example.data.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

sealed class AttachmentProcessResult {
    data class TextAttachment(
        val fileName: String,
        val formattedPromptPrefix: String,
        val extractedRawText: String,
        val charCount: Int
    ) : AttachmentProcessResult()

    data class BinaryAttachment(
        val fileName: String,
        val mimeType: String,
        val bytes: ByteArray,
        val sizeBytes: Long
    ) : AttachmentProcessResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as BinaryAttachment
            return fileName == other.fileName && mimeType == other.mimeType && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = fileName.hashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    data class Error(val message: String) : AttachmentProcessResult()
}

object FileAttachmentHelper {

    private const val MAX_BINARY_SIZE = 20 * 1024 * 1024L // 20 MB
    private const val MAX_TEXT_CHARS = 30000

    // In-memory LRU cache for persistent binary attachment bytes (20 MB maximum)
    private val binaryCache = object : LruCache<String, ByteArray>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int {
            return value.size
        }
    }

    fun getFileName(context: Context, uri: Uri): String {
        var name = "attachment"
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = it.getString(index) ?: "attachment"
                    }
                }
            }
        } catch (_: Exception) {
            name = uri.lastPathSegment ?: "attachment"
        }
        return name
    }

    fun isVideo(mimeType: String?, fileName: String): Boolean {
        if (mimeType?.startsWith("video/", ignoreCase = true) == true) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in listOf("mp4", "mkv", "mov", "avi", "webm", "flv", "3gp", "wmv", "m4v")
    }

    fun detectCategory(context: Context, uri: Uri): String {
        val fileName = getFileName(context, uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val detectedMime = context.contentResolver.getType(uri)?.lowercase() ?: ""
        return detectCategoryFromMimeAndExt(detectedMime, ext)
    }

    fun detectCategoryFromMimeAndExt(mime: String?, ext: String): String {
        val cleanMime = mime?.lowercase() ?: ""
        val cleanExt = ext.lowercase()

        if (cleanMime == "application/pdf" || cleanExt == "pdf") return "pdf"
        if (cleanMime.startsWith("image/") || cleanExt in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")) return "image"
        if (cleanMime.startsWith("audio/") || cleanExt in listOf("mp3", "m4a", "wav", "ogg", "flac", "aac")) return "audio"
        if (cleanMime.startsWith("video/") || cleanExt in listOf("mp4", "mkv", "mov", "avi", "webm", "flv", "3gp", "wmv", "m4v")) return "video"
        if (cleanMime.startsWith("text/") ||
            cleanMime in listOf("application/json", "application/rtf", "application/xml", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
            cleanExt in listOf("txt", "md", "csv", "html", "htm", "xml", "json", "srt", "vtt", "rtf", "doc", "docx")
        ) {
            return "doc"
        }
        return "unknown"
    }

    fun processUri(context: Context, uri: Uri): AttachmentProcessResult {
        val fileName = getFileName(context, uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val detectedMime = context.contentResolver.getType(uri)?.lowercase() ?: ""

        val category = detectCategoryFromMimeAndExt(detectedMime, ext)
        if (category == "doc" || category == "text") {
            return processTextDocument(context, uri, fileName, ext)
        }

        return processBinaryDocument(context, uri, fileName, detectedMime, ext)
    }

    private fun processTextDocument(
        context: Context,
        uri: Uri,
        fileName: String,
        ext: String
    ): AttachmentProcessResult {
        try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return AttachmentProcessResult.Error("Could not read $fileName. Try another format.")

            var extractedText = when (ext) {
                "docx" -> extractTextFromDocx(rawBytes)
                "doc" -> extractTextFromDoc(rawBytes)
                "srt", "vtt" -> stripSrtVttTimings(String(rawBytes, StandardCharsets.UTF_8))
                "html", "htm", "xml" -> stripXmlHtmlTags(String(rawBytes, StandardCharsets.UTF_8))
                "rtf" -> stripRtf(String(rawBytes, StandardCharsets.UTF_8))
                else -> String(rawBytes, StandardCharsets.UTF_8)
            }

            if (extractedText == null) {
                return AttachmentProcessResult.Error(
                    if (ext == "doc") "This .doc file could not be read. Save as .docx or .txt."
                    else "Could not read $fileName. Try another format."
                )
            }

            if (extractedText.length > MAX_TEXT_CHARS) {
                extractedText = extractedText.take(MAX_TEXT_CHARS) + "\n[...truncated at 30k characters]"
            }

            val formatted = buildString {
                appendLine("[Attached file: $fileName]")
                appendLine(extractedText)
                append("[End of file]")
            }

            return AttachmentProcessResult.TextAttachment(
                fileName = fileName,
                formattedPromptPrefix = formatted,
                extractedRawText = extractedText,
                charCount = extractedText.length
            )
        } catch (_: Exception) {
            return AttachmentProcessResult.Error("Could not read $fileName. Try another format.")
        }
    }

    private fun processBinaryDocument(
        context: Context,
        uri: Uri,
        fileName: String,
        detectedMime: String,
        ext: String
    ): AttachmentProcessResult {
        try {
            val stream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return AttachmentProcessResult.Error("Could not read $fileName.")

            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            var totalRead = 0L

            stream.use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    totalRead += read
                    if (totalRead > MAX_BINARY_SIZE) {
                        return AttachmentProcessResult.Error("File too large. Max 20MB.")
                    }
                    output.write(buffer, 0, read)
                }
            }

            val bytes = output.toByteArray()

            val resolvedMime = when {
                detectedMime.isNotBlank() && detectedMime != "application/octet-stream" -> detectedMime
                ext in listOf("png") -> "image/png"
                ext in listOf("jpg", "jpeg") -> "image/jpeg"
                ext in listOf("webp") -> "image/webp"
                ext == "pdf" -> "application/pdf"
                ext == "mp3" -> "audio/mp3"
                ext == "m4a" -> "audio/m4a"
                ext == "wav" -> "audio/wav"
                ext == "ogg" -> "audio/ogg"
                ext == "flac" -> "audio/flac"
                ext == "aac" -> "audio/aac"
                ext == "mp4" -> "video/mp4"
                ext == "mkv" -> "video/x-matroska"
                ext == "mov" -> "video/quicktime"
                ext == "webm" -> "video/webm"
                ext == "3gp" -> "video/3gpp"
                ext in listOf("avi", "wmv", "flv", "m4v") -> "video/mp4"
                else -> "application/octet-stream"
            }

            // Put into memory cache
            binaryCache.put(uri.toString(), bytes)

            return AttachmentProcessResult.BinaryAttachment(
                fileName = fileName,
                mimeType = resolvedMime,
                bytes = bytes,
                sizeBytes = totalRead
            )
        } catch (e: Exception) {
            return AttachmentProcessResult.Error("Could not read $fileName: ${e.message}")
        }
    }

    fun loadBinaryFromCacheOrUri(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String
    ): AttachmentProcessResult.BinaryAttachment? {
        val cached = binaryCache.get(uri.toString())
        if (cached != null) {
            return AttachmentProcessResult.BinaryAttachment(
                fileName = fileName,
                mimeType = mimeType,
                bytes = cached,
                sizeBytes = cached.size.toLong()
            )
        }

        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            var total = 0L
            stream.use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    total += read
                    if (total > MAX_BINARY_SIZE) return null
                    output.write(buffer, 0, read)
                }
            }
            val bytes = output.toByteArray()
            binaryCache.put(uri.toString(), bytes)
            AttachmentProcessResult.BinaryAttachment(
                fileName = fileName,
                mimeType = mimeType,
                bytes = bytes,
                sizeBytes = total
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTextFromDocx(bytes: ByteArray): String? {
        return try {
            val zip = ZipInputStream(bytes.inputStream())
            var entry = zip.nextEntry
            var docXml = ""
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    docXml = zip.readBytes().toString(StandardCharsets.UTF_8)
                    break
                }
                entry = zip.nextEntry
            }
            if (docXml.isNotEmpty()) {
                stripXmlHtmlTags(docXml)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTextFromDoc(bytes: ByteArray): String? {
        return try {
            val utf8 = String(bytes, StandardCharsets.UTF_8)
            val printable = utf8.filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,;:!?'\"-()" }
            if (printable.length > 50) printable else null
        } catch (_: Exception) {
            null
        }
    }

    private fun stripSrtVttTimings(text: String): String {
        val lines = text.lines()
        val result = mutableListOf<String>()
        val timeRegex = Regex("""\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}""")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.all { it.isDigit() } || timeRegex.containsMatchIn(trimmed) || trimmed == "WEBVTT") {
                continue
            }
            result.add(trimmed)
        }
        return result.joinToString("\n")
    }

    private fun stripXmlHtmlTags(text: String): String {
        return text.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun stripRtf(text: String): String {
        return text.replace(Regex("""\\[a-zA-Z]+(-?\d+)?\s?"""), " ")
            .replace("{", "")
            .replace("}", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
