package com.replyhub.app.notifications

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

object AttachmentKinds {
    const val IMAGE = "IMAGE"
    const val FILE = "FILE"
    const val IMAGE_UNAVAILABLE = "IMAGE_UNAVAILABLE"
    const val FILE_UNAVAILABLE = "FILE_UNAVAILABLE"
}

data class ExtractedAttachment(
    val kind: String,
    val path: String?,
    val name: String,
    val mimeType: String?,
)

data class AttachmentHint(
    val kind: String,
    val name: String,
    val mimeType: String?,
)

class NotificationAttachmentExtractor(private val context: Context) {
    fun extract(notification: Notification, body: String): ExtractedAttachment? =
        extractMessagingStyleData(notification)
            ?: extractBigPicture(notification)
            ?: inferAttachmentHint(body)?.let {
                ExtractedAttachment(it.kind, path = null, it.name, it.mimeType)
            }

    private fun extractMessagingStyleData(notification: Notification): ExtractedAttachment? {
        @Suppress("DEPRECATION")
        val bundles = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?: return null
        val attachment = bundles.asSequence()
            .mapNotNull { it as? Bundle }
            .mapNotNull { bundle ->
                @Suppress("DEPRECATION")
                val uri = bundle.getParcelable<Uri>(MESSAGE_DATA_URI) ?: return@mapNotNull null
                uri to bundle.getString(MESSAGE_DATA_MIME_TYPE)
            }
            .lastOrNull()
            ?: return null
        val (uri, bundledMimeType) = attachment
        val mimeType = bundledMimeType ?: context.contentResolver.getType(uri)
        val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: defaultName(mimeType)
        return copyUri(uri, mimeType, name)
    }

    private fun extractBigPicture(notification: Notification): ExtractedAttachment? {
        @Suppress("DEPRECATION")
        val value = notification.extras.get(Notification.EXTRA_PICTURE) ?: return null
        val bitmap = when (value) {
            is Bitmap -> value
            is Icon -> value.loadDrawable(context)?.toBitmap()
            else -> null
        } ?: return null
        val file = attachmentFile("photo", "png")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        return ExtractedAttachment(
            kind = AttachmentKinds.IMAGE,
            path = file.absolutePath,
            name = "사진.png",
            mimeType = "image/png",
        )
    }

    private fun copyUri(uri: Uri, mimeType: String?, name: String): ExtractedAttachment {
        val isImage = mimeType?.startsWith("image/") == true
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.length in 1..8 }
            ?: mimeType?.substringAfter('/', missingDelimiterValue = "")
                ?.substringBefore('+')
                ?.takeIf { it.length in 1..8 }
            ?: "bin"
        val file = attachmentFile(if (isImage) "image" else "file", extension)
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyToWithLimit(output, MAX_ATTACHMENT_BYTES)
                }
            } ?: error("첨부파일 스트림을 열 수 없습니다.")
        }.isSuccess
        if (!copied) file.delete()

        return ExtractedAttachment(
            kind = when {
                copied && isImage -> AttachmentKinds.IMAGE
                copied -> AttachmentKinds.FILE
                isImage -> AttachmentKinds.IMAGE_UNAVAILABLE
                else -> AttachmentKinds.FILE_UNAVAILABLE
            },
            path = file.takeIf { copied }?.absolutePath,
            name = name,
            mimeType = mimeType,
        )
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun attachmentFile(prefix: String, extension: String): File {
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        return File(directory, "$prefix-${UUID.randomUUID()}.$extension")
    }

    private fun defaultName(mimeType: String?): String =
        if (mimeType?.startsWith("image/") == true) "사진" else "첨부파일"

    private companion object {
        const val MESSAGE_DATA_URI = "uri"
        const val MESSAGE_DATA_MIME_TYPE = "type"
        const val MAX_ATTACHMENT_BYTES = 20L * 1024L * 1024L
    }
}

internal fun InputStream.copyToWithLimit(output: OutputStream, maxBytes: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) return copied
        copied += read
        require(copied <= maxBytes) { "첨부파일이 허용 크기를 초과했습니다." }
        output.write(buffer, 0, read)
    }
}

fun inferAttachmentHint(body: String): AttachmentHint? {
    val normalized = body.trim()
    val lower = normalized.lowercase()
    val isImage = normalized in setOf("사진", "이미지") ||
        lower in setOf("photo", "image") ||
        normalized.contains("사진을 보냈") ||
        normalized.contains("이미지를 보냈")
    if (isImage) {
        return AttachmentHint(AttachmentKinds.IMAGE_UNAVAILABLE, "사진", "image/*")
    }

    val isVideo = normalized == "동영상" || normalized.contains("동영상을 보냈")
    if (isVideo) {
        return AttachmentHint(AttachmentKinds.FILE_UNAVAILABLE, "동영상", "video/*")
    }

    val looksLikeFile = normalized == "파일" ||
        normalized.contains("파일을 보냈") ||
        FILE_NAME_PATTERN.matches(normalized)
    return if (looksLikeFile) {
        AttachmentHint(AttachmentKinds.FILE_UNAVAILABLE, normalized.ifBlank { "첨부파일" }, null)
    } else {
        null
    }
}

private val FILE_NAME_PATTERN = Regex(
    ".+\\.(pdf|docx?|xlsx?|pptx?|zip|hwp|txt|csv)$",
    RegexOption.IGNORE_CASE,
)
