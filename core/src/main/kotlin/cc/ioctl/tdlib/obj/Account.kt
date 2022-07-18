package cc.ioctl.tdlib.obj

/**
 * An account denoting a user on Telegram.
 */
abstract class Account : Sender {

    /**
     * The user identifier of the account
     */
    abstract override val userId: Long

    /**
     * The username of the account, without the @ prefix, may be null if the user does not have one.
     */
    abstract override val username: String?

    /**
     * First name of the account.
     */
    abstract val firstName: String

    /**
     * Optional last name of the account, may be null.
     */
    abstract val lastName: String?

    /**
     * Convenient way to get the full name of the account.
     */
    override val name: String get() = lastName?.let { "$firstName $it" } ?: firstName

    /**
     * The phone number of the account, may be null if invisible.
     */
    abstract val phoneNumber: String?

    abstract val bio: String?

    abstract val isDeletedAccount: Boolean

    /**
     * Whether the account is a standard bot.
     */
    abstract val isBot: Boolean

    override val isAnonymousChannel: Boolean
        get() {
            // uid starts with -100
            return userId.toString().startsWith("-100")
        }

    override val isAnonymousAdmin: Boolean
        get() {
            // TODO: implement
            return false
        }

    override val isAnonymous: Boolean get() = isAnonymousChannel || isAnonymousAdmin

}
