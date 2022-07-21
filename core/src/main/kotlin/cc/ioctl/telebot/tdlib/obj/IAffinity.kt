package cc.ioctl.telebot.tdlib.obj

interface IAffinity {

    /**
     * The local user used for object resolution.
     * 0 for detached objects.
     * Detached objects are not bound to a local user and no updates.
     */
    val affinityUserId: Long

}
