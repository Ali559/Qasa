package alibarznji.qasa.screens.nav

object RootRoutes {
    const val WELCOME = "welcome"
    const val CREATE = "create"
    const val PAIR = "pair"
    const val UNLOCK = "unlock"
    const val SHELL = "shell"
}

object VaultRoutes {
    const val LIST = "vault/list"
    const val DETAIL = "vault/detail/{itemId}"
    const val ADD = "vault/add"
    const val EDIT = "vault/edit"

    fun detail(itemId: String) = "vault/detail/$itemId"
}

object SettingsRoutes {
    const val HOME = "settings/home"
    const val CHANGE_PASSWORD = "settings/change_password"
    const val DEVICES = "settings/devices"
    const val LAN = "settings/lan"
    const val SUBSCRIPTION = "settings/subscription"
}
