# Paper Dialog Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Each step uses checkbox syntax and ends with a runnable verification or commit.

**Goal:** Add first-class Paper Dialog support to the Java `invui` module with an InvUI-style builder, Paper `DialogLike` escape hatch, protocol-aware opening, and deterministic fallback behavior.

**Architecture:** Keep dialogs in a new `xyz.xenondevs.invui.dialog` package. `DialogView` owns only a viewer and a Paper `DialogLike`; it never implements or registers as an inventory `Window`. `DialogSupport` centralizes protocol detection, while `DialogOpenResult` reports the outcome of each opening attempt. The builder delegates to Paper's dialog builders and is experimental; the wrapper and fallback operations remain small and explicit.

**Tech Stack:** Java 25, Paper API from the `26.2.build.+` dev bundle, Adventure Dialog API, JSpecify, JUnit Jupiter, and the existing MockBukkit test dependency.

**Spec:** `invui/DIALOG_SUPPORT_DESIGN.md`

## Global Constraints

- Change only the Java `invui` module and files under `invui/`; do not inspect or modify `invui-kotlin/`.
- Do not modify `Window`, `AbstractWindow`, `WindowManager`, inventory lifecycle code, or existing Window fallback semantics.
- Use Paper's public `DialogLike`, `Dialog`, and registry-data dialog APIs (`DialogBase`, `DialogBody`, `DialogInput`, `DialogType`, `ActionButton`, and `DialogAction`) from their target-version packages; do not add NMS, PacketEvents, or ViaVersion.
- Treat protocol `>= 771` as supported and protocol `-1` or `< 771` as unsupported.
- `ThreadCheck.checkOwnedBy(viewer)` validates the caller's current thread; it never schedules work.
- `openOrElse` invokes its callback only for `UNSUPPORTED_CLIENT`, never for `INVALID_VIEWER`.
- `DIALOG_OPENED` means the server-side `showDialog` request succeeded; it is not client acknowledgement.
- Mark `DialogView.builder()` and `DialogView.Builder` as `@ApiStatus.Experimental`.
- Add `@NullMarked` to the new package and use `@Nullable` for genuinely
  optional public values, including the fallback supplier result and optional
  external dialog title.
- Keep all examples and tests Java-only.

## Review Focus

- Unknown protocol `-1` must conservatively select `UNSUPPORTED_CLIENT`; test it alongside 770, 771, and 772.
- A wrong-thread call must throw from `ThreadCheck` before opening or running a usable-viewer fallback; test through the existing Folia ownership check where the test runtime permits.
- Invalid or disconnected viewers must return `INVALID_VIEWER` without evaluating a lazy Window supplier or custom callback.
- Supported viewers must never evaluate a lazy fallback supplier and must not close an inventory already underneath the dialog.
- A fallback Window must belong to the same viewer, and a supplier or callback must be invoked at most once per opening attempt.

---

### Task 1: Add protocol support and result primitives

**Files:**

- Create: `invui/src/main/java/xyz/xenondevs/invui/dialog/package-info.java`
- Create: `invui/src/main/java/xyz/xenondevs/invui/dialog/DialogOpenResult.java`
- Create: `invui/src/main/java/xyz/xenondevs/invui/dialog/DialogSupport.java`
- Test: `invui/src/test/java/xyz/xenondevs/invui/dialog/DialogSupportTest.java`

**Interfaces:**

- Produces `DialogOpenResult` values:
  `DIALOG_OPENED`, `WINDOW_FALLBACK_OPENED`, `CUSTOM_FALLBACK_HANDLED`,
  `UNSUPPORTED_CLIENT`, and `INVALID_VIEWER`.
- Produces `DialogSupport.isSupported(Player)` and package-private pure
  `DialogSupport.isSupportedProtocol(int)`.

- [ ] **Step 1: Write the failing protocol tests**

Create a JUnit parameterized test that exercises the pure protocol predicate and
the Player-facing method without requiring a server UI:

```java
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
```

Use a `Proxy` invocation handler for `Player` that returns the supplied value
only for `getProtocolVersion()` and returns primitive defaults for unrelated
methods. `playerReturningProtocol` must not call any Bukkit or MockBukkit API.

- [ ] **Step 2: Run the test and verify it fails for the missing feature**

Run:

```bash
rtk ./gradlew :invui:test --tests 'xyz.xenondevs.invui.dialog.DialogSupportTest'
```

Expected: test compilation fails because `DialogSupport` does not exist yet.
This is the expected feature-missing failure, not a test assertion or
dependency typo.

- [ ] **Step 3: Implement the minimal protocol/result types**

Use `@NullMarked` in `package-info.java`. Implement `DialogSupport` as a final
utility class:

```java
public static boolean isSupported(Player player) {
    Objects.requireNonNull(player, "player");
    return isSupportedProtocol(player.getProtocolVersion());
}

static boolean isSupportedProtocol(int protocolVersion) {
    return protocolVersion >= 771;
}
```

Document that Paper reports `-1` when the protocol is unknown, and that all
values below 771, including `-1`, are conservatively unsupported. Keep the
threshold in `DialogSupport`; no other class may compare protocol numbers.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run the same Gradle command. Expected: all boundary and Player-protocol tests
pass.

- [ ] **Step 5: Commit the completed primitive**

```bash
rtk git add invui/src/main/java/xyz/xenondevs/invui/dialog invui/src/test/java/xyz/xenondevs/invui/dialog/DialogSupportTest.java
rtk git commit -m "Add Dialog protocol support primitives"
```

### Task 2: Implement the experimental InvUI-style builder

**Files:**

- Modify: `invui/src/main/java/xyz/xenondevs/invui/dialog/package-info.java`
- Create: `invui/src/main/java/xyz/xenondevs/invui/dialog/DialogView.java`
- Test: `invui/src/test/java/xyz/xenondevs/invui/dialog/DialogViewBuilderTest.java`

**Interfaces:**

- `DialogView.of(Player viewer, DialogLike dialog)` creates the Paper escape-hatch wrapper.
- `@ApiStatus.Experimental DialogView.builder()` creates the builder.
- `DialogView.Builder` exposes `setViewer(Player)`, `setTitle(Component)`,
  `setTitle(String)`, `addBody(DialogBody)`, `addInput(DialogInput)`,
  `setType(DialogType)`, `setCanCloseWithEscape(boolean)`,
  `setExternalTitle(@Nullable Component)`, and `build()`.
- Use these Paper 26.2 API packages: `io.papermc.paper.dialog.Dialog`,
  `io.papermc.paper.registry.data.dialog.DialogBase`,
  `io.papermc.paper.registry.data.dialog.body.DialogBody`,
  `io.papermc.paper.registry.data.dialog.input.DialogInput`,
  `io.papermc.paper.registry.data.dialog.type.DialogType`, and
  `io.papermc.paper.registry.data.dialog.ActionButton`.
- `DialogView.getViewer()` and `DialogView.getDialog()` expose the immutable
  construction inputs without exposing internal implementation classes.

- [ ] **Step 1: Write failing builder and escape-hatch tests**

Use a proxy Player and a proxy `DialogLike`. MockBukkit's unit-test runtime
does not provide Paper's `DialogInstancesProvider` or
`InlinedRegistryBuilderProvider` ServiceLoader implementations, so invoking
Paper's concrete dialog builders in this unit test would fail before InvUI is
exercised. Test InvUI's lazy builder configuration and stable escape hatch:

```java
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
```

Add one test for each required builder field. For each missing field, assert
`IllegalStateException` and the exact message: `Viewer is not defined.`,
`Title is not defined.`, or `Dialog type is not defined.`.

- [ ] **Step 2: Run the tests and verify the missing builder fails**

Run:

```bash
rtk ./gradlew :invui:test --tests 'xyz.xenondevs.invui.dialog.DialogViewBuilderTest'
```

Expected: compilation fails because `DialogView` and its builder do not exist.

- [ ] **Step 3: Implement the builder and wrapper construction**

Make `DialogView` final and immutable. Store only `Player viewer` and
`DialogLike dialog`, validate both with `Objects.requireNonNull`, and implement
`of` as the stable escape hatch.

Mark `builder()` and the nested `Builder` with
`@ApiStatus.Experimental`. Store body/input entries in mutable builder lists;
copy them when building. Use Paper's native construction shape:

```java
DialogBase.Builder base = DialogBase.builder(title)
    .body(body)
    .inputs(inputs)
    .canCloseWithEscape(canCloseWithEscape);
if (externalTitle != null)
    base.externalTitle(externalTitle);

Dialog dialog = Dialog.create(builder -> builder
    .empty()
    .base(base.build())
    .type(type));
```

Only call `externalTitle` when the builder value is non-null. Parse the String
title overload with the existing `MiniMessage.miniMessage().deserialize(...)`
convention used by `Window`. In `build()`, validate viewer, title, and type in
that order before calling any Paper builder.

- [ ] **Step 4: Run the builder tests and verify they pass**

Run the focused Gradle command again. Expected: builder creation, escape hatch,
and all required-field validation tests pass.

- [ ] **Step 5: Commit the builder**

```bash
rtk git add invui/src/main/java/xyz/xenondevs/invui/dialog invui/src/test/java/xyz/xenondevs/invui/dialog/DialogViewBuilderTest.java
rtk git commit -m "Add experimental InvUI Dialog builder"
```

### Task 3: Add opening, fallback, and close semantics

**Files:**

- Modify: `invui/src/main/java/xyz/xenondevs/invui/dialog/DialogView.java`
- Test: `invui/src/test/java/xyz/xenondevs/invui/dialog/DialogViewOpenTest.java`

**Interfaces:**

- `void open()` performs a strict attempt and throws `IllegalStateException`
  for `UNSUPPORTED_CLIENT` or `INVALID_VIEWER`.
- `DialogOpenResult tryOpen()` returns only `DIALOG_OPENED`,
  `UNSUPPORTED_CLIENT`, or `INVALID_VIEWER`.
- `DialogOpenResult openOrFallback(Window fallback)` and
  `DialogOpenResult openOrFallback(Supplier<? extends @Nullable Window>)`.
- `DialogOpenResult openOrElse(Consumer<? super DialogOpenResult>)`.
- `void close()` validates viewer ownership and delegates to
  `Player.closeDialog()` without checking `isSleeping()`.

- [ ] **Step 1: Write failing opening/fallback tests**

Use a `PlayerMock` subclass in the test source with controllable protocol,
connection, and dialog-call counters:

```java
private static final class TestPlayer extends PlayerMock {
    private int protocolVersion;
    private boolean connected = true;
    private int shownDialogs;
    private int closedDialogs;

    private TestPlayer(ServerMock server, int protocolVersion) {
        super(server, "dialog-test");
        this.protocolVersion = protocolVersion;
    }

    @Override
    public int getProtocolVersion() {
        return protocolVersion;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void showDialog(DialogLike dialog) {
        shownDialogs++;
    }

    @Override
    public void closeDialog() {
        closedDialogs++;
    }
}
```

Set up `MockBukkit.mock()` and add the test player to its server. Add tests
covering these exact behaviors:

```java
@Test
void supportedClientRequestsDialogAndDoesNotEvaluateLazyFallback() {
    AtomicInteger supplierCalls = new AtomicInteger();
    DialogView view = viewFor(new TestPlayer(server, 771));

    assertEquals(DialogOpenResult.DIALOG_OPENED, view.openOrFallback(() -> {
        supplierCalls.incrementAndGet();
        return null;
    }));
    assertEquals(0, supplierCalls.get());
}

@Test
void unsupportedClientEvaluatesLazyFallbackOnce() {
    AtomicInteger supplierCalls = new AtomicInteger();
    DialogView view = viewFor(new TestPlayer(server, 770));

    assertEquals(DialogOpenResult.UNSUPPORTED_CLIENT, view.openOrFallback(() -> {
        supplierCalls.incrementAndGet();
        return null;
    }));
    assertEquals(1, supplierCalls.get());
}

@Test
void unsupportedClientInvokesCustomFallbackOnce() {
    AtomicInteger callbackCalls = new AtomicInteger();
    DialogView view = viewFor(new TestPlayer(server, 770));

    assertEquals(DialogOpenResult.CUSTOM_FALLBACK_HANDLED,
        view.openOrElse(result -> callbackCalls.incrementAndGet()));
    assertEquals(1, callbackCalls.get());
}

@Test
void invalidViewerSkipsSupplierAndCallback() {
    TestPlayer player = new TestPlayer(server, 770);
    player.connected = false;
    DialogView view = viewFor(player);
    AtomicInteger calls = new AtomicInteger();

    assertEquals(DialogOpenResult.INVALID_VIEWER, view.openOrFallback(() -> {
        calls.incrementAndGet();
        return null;
    }));
    assertEquals(DialogOpenResult.INVALID_VIEWER, view.openOrElse(result -> calls.incrementAndGet()));
    assertEquals(0, calls.get());
}
```

Also test strict `open()` exceptions, same-viewer validation through the
package-private pure UUID validator, and `close()` with a sleeping test player. The `close()` test must
assert that `closeDialog()` was called even though `isSleeping()` is true.
The MockBukkit runtime does not include the NMS classes needed to construct a
real Window, so the public fallback method is covered through supplier-count
tests and the pure validator is tested directly before any Window operation.

- [ ] **Step 2: Run the tests and verify they fail for missing behavior**

Run:

```bash
rtk ./gradlew :invui:test --tests 'xyz.xenondevs.invui.dialog.DialogViewOpenTest'
```

Expected: the tests compile against the builder from Task 2 and fail because
the opening/fallback/close methods are not implemented.

- [ ] **Step 3: Implement the minimal opening path**

Implement a single internal attempt in this order:

```java
if (!isUsableViewer())
    return INVALID_VIEWER;

ThreadCheck.checkOwnedBy(viewer);

if (!DialogSupport.isSupported(viewer))
    return UNSUPPORTED_CLIENT;

viewer.showDialog(dialog);
return DIALOG_OPENED;
```

`isUsableViewer()` must match the existing Window open guard:
`!viewer.isSleeping() && viewer.isValid() && viewer.isConnected()`.
`tryOpen()` must not catch Paper exceptions. `open()` converts only the two
non-open results into clear `IllegalStateException` messages.

- [ ] **Step 4: Implement the Window and lazy fallback path**

Call the internal attempt once. If it returns `DIALOG_OPENED` or
`INVALID_VIEWER`, return immediately. For `UNSUPPORTED_CLIENT`, evaluate the
supplier exactly once, accept `null` as no fallback, require the fallback's
viewer UUID to equal the Dialog viewer UUID, call `fallback.open()`, and return
`WINDOW_FALLBACK_OPENED`. Re-check viewer validity after supplier construction
before attempting the Window open; if the player disconnected in that gap,
return `INVALID_VIEWER` and do not open the Window.

- [ ] **Step 5: Implement the custom fallback and close path**

For `openOrElse`, call the internal attempt once, invoke the callback only when
the result is `UNSUPPORTED_CLIENT`, and return `CUSTOM_FALLBACK_HANDLED` after
the callback returns. Do not invoke it for invalid viewers. For `close()`, call
`ThreadCheck.checkOwnedBy(viewer)` and then `viewer.closeDialog()` without the
open usability guard.

- [ ] **Step 6: Run all opening tests and verify they pass**

Run the focused Gradle command again. Expected: all strict/result/fallback,
supplier-count, callback-count, invalid-viewer, same-viewer, and close tests
pass.

- [ ] **Step 7: Commit opening and lifecycle behavior**

```bash
rtk git add invui/src/main/java/xyz/xenondevs/invui/dialog/DialogView.java invui/src/test/java/xyz/xenondevs/invui/dialog/DialogViewOpenTest.java
rtk git commit -m "Add Dialog opening and fallback behavior"
```

### Task 4: Complete JavaDoc, formatting, and Java-only verification

**Files:**

- Modify: `invui/src/main/java/xyz/xenondevs/invui/dialog/DialogView.java`
- Modify: `invui/src/main/java/xyz/xenondevs/invui/dialog/DialogSupport.java`
- Modify: `invui/src/main/java/xyz/xenondevs/invui/dialog/DialogOpenResult.java`
- Modify: `invui/src/main/java/xyz/xenondevs/invui/dialog/package-info.java`

- [ ] **Step 1: Add public JavaDoc examples and semantics**

Document the required protocol boundary, unknown protocol behavior, builder
experimental status, required builder fields, strict-open exceptions,
`tryOpen()` result meaning, lazy supplier evaluation, same-viewer requirement,
thread ownership requirement, callback behavior, and `closeDialog()` preserving
the underlying inventory. Keep every example Java-only and mention that Paper
does not acknowledge client rendering.

- [ ] **Step 2: Run Java formatting and diagnostics**

No Gradle formatting task is configured in this repository, and no JetBrains
formatting MCP is available in this session. Run `rtk git diff --check`, then
manually reformat only the four affected Java files to match adjacent InvUI
style, including its keep-indents-on-empty-lines rule. Inspect the affected
files for compiler/IDE diagnostics. Do not use a whole-project IDE build or
inspect any Kotlin source.

- [ ] **Step 3: Run the complete Java `invui` test suite**

Run:

```bash
rtk ./gradlew :invui:test
```

Expected: all existing Java tests and the new dialog tests pass. Do not run a
Kotlin-specific task.

- [ ] **Step 4: Run Java compilation and Javadocs**

Run:

```bash
rtk ./gradlew :invui:compileJava :invui:javadoc
```

Expected: Java compilation and Javadocs complete without new errors or
warnings caused by the Dialog API.

- [ ] **Step 5: Review the complete branch diff**

Run:

```bash
rtk git diff --stat main...HEAD
rtk git diff main...HEAD -- invui/src/main/java invui/src/test/java invui/DIALOG_SUPPORT_DESIGN.md invui/DIALOG_SUPPORT_PLAN.md
rtk git status --short --branch
```

Confirm that only Java `invui` source/tests and the two process documents are
present, no existing Window code changed, no dependency changed, and no path
under `invui-kotlin/` was modified.

- [ ] **Step 6: Commit final documentation and verification changes**

```bash
rtk git add invui/src/main/java/xyz/xenondevs/invui/dialog
rtk git commit -m "Document Paper Dialog API semantics"
```
