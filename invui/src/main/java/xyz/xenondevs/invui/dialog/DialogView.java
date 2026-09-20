package xyz.xenondevs.invui.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.dialog.DialogLike;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import xyz.xenondevs.invui.internal.util.ThreadCheck;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Associates a Paper {@link DialogLike} with the player it should be shown to.
 * <p>
 * A view is not an inventory {@link Window}: it is not registered with
 * {@code WindowManager}, does not manage inventory state, and does not track a
 * Paper client acknowledgement. Every opening method must be called from the
 * viewer-owned thread. InvUI validates that requirement with
 * {@code ThreadCheck}; it does not schedule the call.
 * <p>
 * For common dialogs, the experimental builder keeps Paper's dialog model but
 * removes its base-building boilerplate:
 *
 * <pre>{@code
 * DialogView view = DialogView.builder()
 *     .setViewer(player)
 *     .setTitle(Component.text("Confirm"))
 *     .addBody(DialogBody.plainMessage(Component.text("Continue?")))
 *     .setType(DialogType.notice())
 *     .build();
 *
 * view.openOrFallback(() -> createFallbackWindow(player));
 * }</pre>
 *
 * For advanced Paper features, pass an already-built dialog through
 * {@link #of(Player, DialogLike)}. Paper action buttons and custom-click
 * callbacks remain Paper concerns and are not copied into an InvUI registry.
 */
public final class DialogView {
    
    private final Player viewer;
    private final DialogLike dialog;
    
    private DialogView(Player viewer, DialogLike dialog) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.dialog = Objects.requireNonNull(dialog, "dialog");
    }
    
    /**
     * Creates a view for a dialog built directly with Paper's API.
     * <p>
     * This is the stable escape hatch when the caller needs a Paper feature
     * that the experimental InvUI builder does not expose.
     *
     * @param viewer the player to show the dialog to
     * @param dialog the already-built Paper dialog
     * @return a new dialog view
     */
    public static DialogView of(Player viewer, DialogLike dialog) {
        return new DialogView(viewer, dialog);
    }
    
    /**
     * Creates an InvUI-style builder for a common Paper dialog.
     *
     * @return a new experimental builder
     */
    @ApiStatus.Experimental
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Gets the player associated with this view.
     *
     * @return the dialog viewer
     */
    public Player getViewer() {
        return viewer;
    }
    
    /**
     * Gets the Paper dialog associated with this view.
     *
     * @return the Paper dialog
     */
    public DialogLike getDialog() {
        return dialog;
    }

    /**
     * Tries to show the dialog without a fallback.
     * <p>
     * An invalid viewer returns {@link DialogOpenResult#INVALID_VIEWER}
     * without checking the thread. A usable viewer must already be on its
     * owned thread; otherwise {@code ThreadCheck} throws. An unsupported or
     * unknown protocol returns {@link DialogOpenResult#UNSUPPORTED_CLIENT}.
     * A supported viewer returns {@link DialogOpenResult#DIALOG_OPENED} after
     * InvUI calls Paper's {@code showDialog}; Paper provides no client-side
     * acknowledgement.
     * <p>
     * This method never evaluates a fallback because it has none.
     *
     * @return the result of the opening attempt
     * @throws IllegalStateException if the caller is not on the viewer-owned
     *     thread
     */
    public DialogOpenResult tryOpen() {
        if (!isUsableViewer(viewer))
            return DialogOpenResult.INVALID_VIEWER;
        
        ThreadCheck.checkOwnedBy(viewer);
        if (!DialogSupport.isSupported(viewer))
            return DialogOpenResult.UNSUPPORTED_CLIENT;
        
        viewer.showDialog(dialog);
        return DialogOpenResult.DIALOG_OPENED;
    }
    
    /**
     * Shows the dialog strictly.
     * <p>
     * Unlike {@link #tryOpen()}, this method does not return an unsupported or
     * invalid result. It throws instead of silently doing nothing.
     *
     * @throws IllegalStateException if the viewer is invalid or its client is
     *     not supported, or if the caller is not on the viewer-owned thread
     */
    public void open() {
        switch (tryOpen()) {
            case DIALOG_OPENED -> {}
            case UNSUPPORTED_CLIENT -> throw new IllegalStateException("The viewer's client does not support dialogs.");
            case INVALID_VIEWER -> throw new IllegalStateException("The dialog viewer is invalid.");
            default -> throw new AssertionError();
        }
    }
    
    /**
     * Shows the dialog or opens a Window fallback for the same viewer.
     * <p>
     * The fallback is used only for an unsupported client. It is never used
     * for an invalid viewer, and it is not an after-close fallback. The Window
     * must have the same viewer UUID as this view.
     *
     * @param fallback the fallback Window
     * @return the result of the opening attempt
     * @throws IllegalArgumentException if the fallback belongs to another
     *     viewer
     * @throws IllegalStateException if the caller is not on the viewer-owned
     *     thread
     */
    public DialogOpenResult openOrFallback(Window fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return openOrFallback(() -> fallback);
    }
    
    /**
     * Shows the dialog or lazily creates and opens a Window fallback for the
     * same viewer.
     * <p>
     * The supplier is not evaluated for a supported or invalid viewer. For an
     * unsupported viewer it is evaluated exactly once. A {@code null} result
     * means that no fallback was supplied and returns
     * {@link DialogOpenResult#UNSUPPORTED_CLIENT}.
     *
     * @param fallbackSupplier the lazy fallback Window supplier
     * @return the result of the opening attempt
     * @throws IllegalArgumentException if the fallback belongs to another
     *     viewer
     * @throws IllegalStateException if the caller is not on the viewer-owned
     *     thread
     */
    public DialogOpenResult openOrFallback(Supplier<? extends @Nullable Window> fallbackSupplier) {
        Objects.requireNonNull(fallbackSupplier, "fallbackSupplier");
        DialogOpenResult result = tryOpen();
        if (result != DialogOpenResult.UNSUPPORTED_CLIENT)
            return result;
        
        Window fallback = fallbackSupplier.get();
        if (fallback == null)
            return result;
        validateFallbackViewer(viewer, fallback.getViewer());
        if (!isUsableViewer(viewer) || !isUsableViewer(fallback.getViewer()))
            return DialogOpenResult.INVALID_VIEWER;
        
        fallback.open();
        return DialogOpenResult.WINDOW_FALLBACK_OPENED;
    }
    
    /**
     * Shows the dialog or invokes a custom fallback for an unsupported client.
     * <p>
     * The callback is invoked exactly once only for
     * {@link DialogOpenResult#UNSUPPORTED_CLIENT}. It is not invoked for an
     * invalid viewer or after a successful dialog request.
     *
     * @param fallback the custom compatibility fallback
     * @return the result of the opening attempt
     * @throws IllegalStateException if the caller is not on the viewer-owned
     *     thread
     */
    public DialogOpenResult openOrElse(Consumer<? super DialogOpenResult> fallback) {
        Objects.requireNonNull(fallback, "fallback");
        DialogOpenResult result = tryOpen();
        if (result != DialogOpenResult.UNSUPPORTED_CLIENT)
            return result;
        
        fallback.accept(result);
        return DialogOpenResult.CUSTOM_FALLBACK_HANDLED;
    }
    
    /**
     * Closes the dialog while preserving the screen underneath it.
     * <p>
     * This uses Paper's dialog-specific close operation and intentionally does
     * not reuse the opening usability check. In particular, a sleeping player
     * is not rejected solely for being asleep. The caller must still already
     * be on the viewer-owned thread.
     *
     * @throws IllegalStateException if the caller is not on the viewer-owned
     *     thread
     */
    public void close() {
        ThreadCheck.checkOwnedBy(viewer);
        viewer.closeDialog();
    }
    
    static boolean isUsableViewer(Player viewer) {
        return !viewer.isSleeping() && viewer.isValid() && viewer.isConnected();
    }

    static void validateFallbackViewer(Player viewer, Player fallbackViewer) {
        if (!fallbackViewer.getUniqueId().equals(viewer.getUniqueId()))
            throw new IllegalArgumentException("The fallback Window must belong to the dialog viewer.");
    }
    
    /**
     * An experimental builder for common Paper dialogs.
     * <p>
     * It delegates the final construction to Paper's registry-data dialog
     * APIs. The viewer, title, and dialog type are required; body and input
     * entries are optional.
     */
    @ApiStatus.Experimental
    public static final class Builder {
        
        private @Nullable Player viewer;
        private @Nullable Component title;
        private final List<DialogBody> body = new ArrayList<>();
        private final List<DialogInput> inputs = new ArrayList<>();
        private @Nullable DialogType type;
        private boolean canCloseWithEscape = true;
        private @Nullable Component externalTitle;
        
        private Builder() {}
        
        /**
         * Sets the viewer of the dialog.
         *
         * @param viewer the dialog viewer
         * @return this builder
         */
        public Builder setViewer(Player viewer) {
            this.viewer = Objects.requireNonNull(viewer, "viewer");
            return this;
        }
        
        /**
         * Sets the dialog title.
         *
         * @param title the dialog title
         * @return this builder
         */
        public Builder setTitle(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }
        
        /**
         * Sets the dialog title using MiniMessage syntax.
         *
         * @param title the MiniMessage title
         * @return this builder
         */
        public Builder setTitle(String title) {
            return setTitle(MiniMessage.miniMessage().deserialize(title));
        }
        
        /**
         * Adds a body element to the dialog.
         *
         * @param body the body element
         * @return this builder
         */
        public Builder addBody(DialogBody body) {
            this.body.add(Objects.requireNonNull(body, "body"));
            return this;
        }
        
        /**
         * Adds an input to the dialog.
         *
         * @param input the dialog input
         * @return this builder
         */
        public Builder addInput(DialogInput input) {
            this.inputs.add(Objects.requireNonNull(input, "input"));
            return this;
        }
        
        /**
         * Sets the dialog type.
         *
         * @param type the dialog type
         * @return this builder
         */
        public Builder setType(DialogType type) {
            this.type = Objects.requireNonNull(type, "type");
            return this;
        }
        
        /**
         * Sets whether the dialog can be closed with the escape key.
         *
         * @param canCloseWithEscape whether escape closes the dialog
         * @return this builder
         */
        public Builder setCanCloseWithEscape(boolean canCloseWithEscape) {
            this.canCloseWithEscape = canCloseWithEscape;
            return this;
        }
        
        /**
         * Sets the title shown on buttons that open this dialog.
         *
         * @param externalTitle the external title, or null to omit it
         * @return this builder
         */
        public Builder setExternalTitle(@Nullable Component externalTitle) {
            this.externalTitle = externalTitle;
            return this;
        }
        
        /**
         * Builds the dialog view.
         * <p>
         * The Paper dialog factories are resolved when this method is called,
         * so the method must run in a Paper runtime that provides their
         * ServiceLoader implementations.
         *
         * @return the built dialog view
         * @throws IllegalStateException if a viewer, title, or dialog type is missing
         */
        public DialogView build() {
            Player viewer = this.viewer;
            if (viewer == null)
                throw new IllegalStateException("Viewer is not defined.");
            
            Component title = this.title;
            if (title == null)
                throw new IllegalStateException("Title is not defined.");
            
            DialogType type = this.type;
            if (type == null)
                throw new IllegalStateException("Dialog type is not defined.");
            
            DialogBase.Builder base = DialogBase.builder(title)
                .body(List.copyOf(body))
                .inputs(List.copyOf(inputs))
                .canCloseWithEscape(canCloseWithEscape);
            if (externalTitle != null)
                base.externalTitle(externalTitle);
            
            Dialog dialog = Dialog.create(builder -> builder
                .empty()
                .base(base.build())
                .type(type));
            return new DialogView(viewer, dialog);
        }
        
    }
    
}
