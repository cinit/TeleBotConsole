package cc.ioctl.telebot.tdlib.obj

import com.google.gson.JsonObject
import java.io.IOException

class RemoteFile internal constructor(
    val tdlibFileIndex: Int,
    val fileId: String,
    val uniqueId: String,
) {

    companion object {
        @JvmStatic
        @kotlin.jvm.Throws(IOException::class)
        fun fromJsonObject(obj: JsonObject): RemoteFile {
            if (obj["@type"].asString != "file") {
                throw IOException("Invalid type: ${obj["@type"].asString}")
            }
            val fileIndex = obj["id"].asInt
            val remote = obj["remote"].asJsonObject
            val fileId = remote["id"].asString
            val uniqueId = remote["unique_id"].asString
            return RemoteFile(fileIndex, fileId, uniqueId)
        }
    }

}
