package cc.ioctl.tdlib.obj

interface IKnowability {

    /**
     * Whether the user's information is available.
     * If this is false, nothing except id is valid.
     */
    val isKnown: Boolean

}
