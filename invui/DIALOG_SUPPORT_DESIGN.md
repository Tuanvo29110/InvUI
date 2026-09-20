# Paper Dialog Support Design

## Scope

Add first-class Paper Dialog support to the Java `invui` module only. The
existing inventory `Window` lifecycle remains unchanged, and no file under
`invui-kotlin/` is inspected or modified.

Paper's public API is the integration boundary: `Player.showDialog(DialogLike)`
opens the dialog and `Player.closeDialog()` closes it without closing an
underlying inventory. InvUI adds a small convenience builder around Paper's
builder, but does not reimplement Paper's complete dialog model or manage
dialog action callbacks.

## Public API

Add package `xyz.xenondevs.invui.dialog` with:

* `DialogView` — an immutable wrapper around a `Player` and an Adventure
  `DialogLike`, with an InvUI-style builder for common dialogs.
* `DialogSupport` — the single source of truth for client compatibility.
* `DialogOpenResult` — explicit results for dialog, Window fallback, custom
  fallback, unsupported client, and invalid viewer paths.

`DialogView` supports both a convenient InvUI-style builder and an escape hatch
for a dialog built directly with Paper:

```java
DialogView view = DialogView.builder()
    .setViewer(player)
    .setTitle(Component.text("Confirm"))
    .addBody(DialogBody.plainMessage(Component.text("Continue?")))
    .setType(DialogType.confirmation(
        ActionButton.builder(Component.text("Yes")).build(),
        ActionButton.builder(Component.text("No")).build()
    ))
    .build();

DialogView existing = DialogView.of(player, paperDialog);

view.open();
DialogOpenResult result = view.tryOpen();
view.openOrFallback(window);
view.openOrFallback(() -> createFallbackWindow(player));
view.openOrElse(reason -> player.sendMessage("Please update Minecraft."));
view.close();
```

The builder delegates to Paper's `Dialog.create`, `DialogBase`, `DialogBody`,
`DialogInput`, and `DialogType` APIs. It removes the repetitive base-building
boilerplate without recreating Paper's complete dialog model. Its common
configuration includes the viewer, title, body entries, input entries, dialog
type, escape-key closing, and external title. Advanced Paper features remain
available through `DialogView.of(Player, DialogLike)`.

The wrapper is stateless with respect to server dialog lifecycle. Each call is
one deterministic open attempt; a supplier or callback is invoked at most once
for that attempt. Paper owns dialog callbacks and lifecycle state.

## Compatibility

`DialogSupport.isSupported(Player)` reads the connected client's
`Player#getProtocolVersion()`:

```text
protocol < 771  -> unsupported
protocol == 771 -> supported
protocol > 771  -> supported
protocol == -1  -> unsupported (unknown is handled conservatively)
```

No ViaVersion or PacketEvents dependency is added. A protocol check is kept in
`DialogSupport`; no other class compares protocol numbers.

## Opening and fallback behavior

`tryOpen()` returns `DIALOG_OPENED` for a usable supported viewer,
`UNSUPPORTED_CLIENT` for an unsupported or unknown protocol, and
`INVALID_VIEWER` for a sleeping, invalid, or disconnected viewer.

`open()` is strict. It opens the dialog or throws `IllegalStateException` for
an unsupported or invalid viewer instead of silently doing nothing.

`openOrFallback(Window)` and its lazy supplier overload use this deterministic
chain:

```text
usable + supported       -> show Dialog, return DIALOG_OPENED
usable + unsupported     -> resolve Window once and open it, return WINDOW_FALLBACK_OPENED
usable + unsupported + null Window -> return UNSUPPORTED_CLIENT
invalid viewer           -> return INVALID_VIEWER without resolving fallback
```

The fallback Window must belong to the same viewer. A null result from the lazy
supplier means that no fallback was provided. Supplier exceptions and Paper or
Window open failures propagate; InvUI does not silently swallow them or invoke
another fallback afterward.

`openOrElse(Consumer)` invokes the consumer exactly once for an unsupported or
invalid result and returns `CUSTOM_FALLBACK_HANDLED`. It is not invoked when
the dialog opens. If the consumer throws, that exception propagates.

All actual UI operations and fallback callbacks for a usable viewer run on the
viewer-owned thread through the existing `ThreadCheck` convention. Dialog
opening does not close or register an InvUI `Window`. `close()` delegates to
`Player.closeDialog()` so an underlying inventory is preserved.

## Tests

Add Java tests in `invui/src/test/java` for:

* protocol 770, 771, values above 771, and unknown `-1`;
* InvUI builder creation and Paper-dialog escape hatch;
* supported dialog selection;
* strict and result-based opening;
* Window fallback selection;
* lazy supplier evaluation zero times on the dialog path and exactly once on
  the fallback path;
* custom callback evaluation exactly once;
* invalid viewers and null fallback results;
* no changes to existing Window or Kotlin-module behavior.

## Validation

Run Java-only Gradle tests and compilation for `:invui`, inspect diagnostics and
formatting for affected Java files, then review the complete diff and confirm
that no path under `invui-kotlin/` was read or changed.
