package xyz.xenondevs.invui.dialog;

import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogSupportTest {
    
    @ParameterizedTest
    @CsvSource({
        "-1, false",
        "0, false",
        "770, false",
        "771, true",
        "772, true"
    })
    void protocolBoundaryUsesTheDialogMinimum(int protocol, boolean expected) {
        assertEquals(expected, DialogSupport.isSupportedProtocol(protocol));
    }
    
    @Test
    void playerSupportUsesPaperReportedProtocol() {
        Player player = playerReturningProtocol(771);
        
        assertTrue(DialogSupport.isSupported(player));
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
    
}
