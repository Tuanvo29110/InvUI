package xyz.xenondevs.invui.dialog;

import net.kyori.adventure.dialog.DialogLike;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DialogViewBuilderTest {
    
    @Test
    void builderAcceptsCommonConfigurationWithoutCreatingPaperObjects() {
        Player player = playerReturningProtocol(771);
        
        DialogView.Builder builder = DialogView.builder()
            .setViewer(player)
            .setTitle(Component.text("Title"))
            .setCanCloseWithEscape(false)
            .setExternalTitle(Component.text("External"));
        
        assertNotNull(builder);
    }
    
    @Test
    void escapeHatchRetainsAnExistingPaperDialog() {
        Player player = playerReturningProtocol(771);
        DialogLike dialog = dialogLikeProxy();
        
        DialogView view = DialogView.of(player, dialog);
        
        assertSame(dialog, view.getDialog());
    }
    
    @Test
    void buildRequiresViewer() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            DialogView.builder()
                .setTitle(Component.text("Title"))
                .build()
        );
        
        assertEquals("Viewer is not defined.", exception.getMessage());
    }
    
    @Test
    void buildRequiresTitle() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            DialogView.builder()
                .setViewer(playerReturningProtocol(771))
                .build()
        );
        
        assertEquals("Title is not defined.", exception.getMessage());
    }
    
    @Test
    void buildRequiresDialogType() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            DialogView.builder()
                .setViewer(playerReturningProtocol(771))
                .setTitle(Component.text("Title"))
                .build()
        );
        
        assertEquals("Dialog type is not defined.", exception.getMessage());
    }
    
    private static Player playerReturningProtocol(int protocol) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{Player.class},
            (_, method, _) -> {
                if (method.getName().equals("getProtocolVersion"))
                    return protocol;
                if (method.getReturnType() == boolean.class)
                    return false;
                if (method.getReturnType().isPrimitive())
                    return 0;
                return null;
            }
        );
    }

    private static DialogLike dialogLikeProxy() {
        return (DialogLike) Proxy.newProxyInstance(
            DialogLike.class.getClassLoader(),
            new Class<?>[]{DialogLike.class},
            (_, method, _) -> {
                if (method.getName().equals("toString"))
                    return "dialog";
                if (method.getReturnType() == boolean.class)
                    return false;
                if (method.getReturnType().isPrimitive())
                    return 0;
                return null;
            }
        );
    }
    
}
