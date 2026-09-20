package xyz.xenondevs.invui.dialog;

/**
 * Describes the result of an attempt to open a {@link DialogView}.
 */
public enum DialogOpenResult {
    /**
     * The viewer was valid, the client was supported, and Paper accepted the
     * server-side dialog open request.
     * <p>
     * This result does not acknowledge that the client rendered the dialog.
     */
    DIALOG_OPENED,
    
    /**
     * An InvUI {@link xyz.xenondevs.invui.window.Window} fallback was opened.
     */
    WINDOW_FALLBACK_OPENED,
    
    /**
     * A caller-provided custom fallback was invoked after the client was found
     * to be unsupported.
     */
    CUSTOM_FALLBACK_HANDLED,
    
    /**
     * The viewer's client cannot display dialogs, or its protocol is unknown.
     */
    UNSUPPORTED_CLIENT,
    
    /**
     * The viewer is sleeping, invalid, or disconnected.
     */
    INVALID_VIEWER
}
