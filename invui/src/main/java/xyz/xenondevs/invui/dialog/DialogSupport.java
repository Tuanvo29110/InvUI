package xyz.xenondevs.invui.dialog;

import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Provides the central client compatibility check for Paper dialogs.
 */
public final class DialogSupport {
    
    private static final int MINIMUM_DIALOG_PROTOCOL = 771;
    
    private DialogSupport() {}
    
    /**
     * Checks whether the connected client reported by the player can display
     * dialogs.
     * <p>
     * Paper reports the connected client's protocol through
     * {@link Player#getProtocolVersion()}. Unknown protocol values, including
     * Paper's documented {@code -1}, are treated as unsupported.
     *
     * @param player the player whose client should be checked
     * @return whether the reported protocol is at least Minecraft 1.21.6's
     *     protocol version, 771
     */
    public static boolean isSupported(Player player) {
        Objects.requireNonNull(player, "player");
        return isSupportedProtocol(player.getProtocolVersion());
    }
    
    static boolean isSupportedProtocol(int protocolVersion) {
        return protocolVersion >= MINIMUM_DIALOG_PROTOCOL;
    }
    
}
