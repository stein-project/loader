/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import java.util.List;

/**
 * A Stein Loader mod: receives the game events.
 *
 * <p>Registration is through ServiceLoader: the mod jar carries
 * META-INF/services/dev.xavier.stein.loader.api.SteinMod with the class name, which needs a public
 * no-argument constructor. Game types arrive as Object (the game's class names are obfuscated; the
 * mod casts to the types it knows).
 *
 * <p>All methods run on the game thread. An error in one mod is logged and the game (and the other
 * mods) carry on.
 */
public interface SteinMod {

    /**
     * Before Minecraft.startGame: the game exists, nothing has been loaded yet (e.g. register a
     * resource pack).
     */
    default void beforeStartGame() {}

    /**
     * After startGame: window, resources and options are ready (equivalent to
     * FMLInitializationEvent).
     */
    default void afterStartGame() {}

    /** End of each client tick (TickEvent.ClientTickEvent, END phase). */
    default void onTickEnd() {}

    /** After each keyboard event handled by the game (InputEvent.KeyInputEvent). */
    default void onKeyInput() {}

    /** After each mouse event handled by the game (InputEvent.MouseInputEvent). */
    default void onMouseInput() {}

    /** After the HUD (RenderGameOverlayEvent.Post, type ALL). */
    default void onOverlay(float partialTicks) {}

    /**
     * After everything, the screen grid included: the place for whatever must go on top of every
     * element (the Tab list). {@link #onOverlay} draws before the grid, so what it draws ends up
     * beneath the elements. Depth starts clean here: not even the grid items, drawn in front, end
     * up on top of what is drawn from here.
     */
    default void onOverlayTop(float partialTicks) {}

    /** Before the open screen draws itself (GuiScreenEvent.DrawScreenEvent.Pre). */
    default void beforeDrawScreen(Object screen, int mouseX, int mouseY, float partialTicks) {}

    /** After the open screen draws itself (GuiScreenEvent.DrawScreenEvent.Post). */
    default void afterDrawScreen(Object screen, int mouseX, int mouseY, float partialTicks) {}

    /**
     * Mouse event on an open screen, before the screen gets it
     * (GuiScreenEvent.MouseInputEvent.Pre).
     *
     * @return true to keep the screen from receiving the event
     */
    default boolean onGuiMouseInput(Object screen) {
        return false;
    }

    /**
     * Chat message received from the server, before it goes to the chat (ClientChatReceivedEvent,
     * types 0 and 1).
     *
     * @return the message (the same one or another IChatComponent), or null to discard it
     */
    default Object onChat(Object message) {
        return message;
    }

    /** Joining a server or world has completed (S01PacketJoinGame handled). */
    default void onJoinGame() {}

    /**
     * The mod's settings screen, opened by the Configure button of the Stein Loader menu.
     *
     * @param parent the screen to return to on close (GuiScreen)
     * @return the screen (GuiScreen), or null if the mod has none; if not overridden, the button is
     *     disabled
     */
    default Object configScreen(Object parent) {
        return null;
    }

    /**
     * What the mod offers the player during a match: its options and screens on the Panel page
     * (right Shift). Called every time the Panel opens — describe, do not store: the values are
     * read on the spot. If not overridden, the mod does not appear in the Panel list.
     */
    default void onPage(Page page) {}

    /**
     * Game screen built (after initGui, and again when the window is resized), like Forge's
     * InitGuiEvent.Post.
     *
     * @param screen the screen (GuiScreen)
     * @param buttons its button list (GuiButton): the mod may add its own, with ids far from the
     *     game's
     */
    default void onGuiInit(Object screen, List<Object> buttons) {}

    /**
     * Button clicked on a screen, before the screen's actionPerformed, like Forge's
     * ActionPerformedEvent.Pre.
     *
     * @return true to keep the click (the screen does not receive it)
     */
    default boolean onGuiAction(Object screen, Object button) {
        return false;
    }

    /**
     * The frame's field of view, after the game's own calculations (EntityRenderer.getFOVModifier),
     * like Forge's EntityViewRenderEvent.FOVModifier. Called more than once per frame: with
     * useFovSetting, the world one (derived from the player's FOV option); without, the hand one
     * (70). Mods receive, in order, the value returned by the previous one.
     *
     * @return the field of view to use, in degrees
     */
    default float onFov(float fov, float partialTicks, boolean useFovSetting) {
        return fov;
    }

    /**
     * The player's FOV factor — speed, sprinting, drawn bow (AbstractClientPlayer.getFovModifier)
     * —, which the game smooths every tick; like Forge's FOVUpdateEvent.
     *
     * @return the factor to use (1 = the option's field of view, unchanged)
     */
    default float onFovModifier(float modifier) {
        return modifier;
    }

    /**
     * Before the block and item atlas (TextureMap) is built, on every resource reload — Forge's
     * TextureStitchEvent.Pre: the mod registers textures (TextureMap.registerSprite). A texture
     * with a path under mcpatcher/ or optifine/ comes from assets/&lt;domain&gt;/&lt;path&gt;.png
     * (without the leading textures/). May come before afterStartGame (the first load happens
     * inside startGame).
     */
    default void onTextureStitch(Object textureMap) {}

    /** Models and atlas ready, on every resource reload (end of Forge's ModelBakeEvent). */
    default void onModelsReloaded() {}

    /**
     * Model of each drawn item (ItemModelMesher.getItemModel): screen, hand, ground, frame. Called
     * many times per frame — answer quickly for an item that does not belong to the mod. Mods
     * receive, in order, the model returned by the previous one.
     *
     * @return the model (IBakedModel) to use
     */
    default Object onItemModel(Object stack, Object model) {
        return model;
    }

    /**
     * Texture of a worn armor piece (LayerArmorBase; Forge's ForgeHooksClient.getArmorTexture):
     * layer 1, or 2 on the leggings; overlay null, or the layer name ("overlay", dyed leather).
     *
     * @return the ResourceLocation to use
     */
    default Object onArmorTexture(Object stack, int layer, String overlay, Object location) {
        return location;
    }

    /**
     * Model of a block assembled into the chunk geometry (BlockModelRenderer.renderModel), only for
     * blocks marked with {@link Game#watchBlockModel}: the mod swaps the model at that position
     * (connected textures, for example). Runs on the chunk-building threads, several at the same
     * time: no shared mutable state without care, no OpenGL. world is the access to the blocks of
     * the build (IBlockAccess). Mods receive, in order, the model returned by the previous one.
     *
     * @return the model (IBakedModel) to use
     */
    default Object onBlockModel(Object world, Object state, Object pos, Object model) {
        return model;
    }

    /**
     * Player's hit on an entity by click (PlayerControllerMP.attackEntity in Minecraft.clickMouse),
     * before it goes to the server — Forge's AttackEntityEvent, on the client. The arm has already
     * swung.
     *
     * @param target the targeted entity (Entity)
     * @return true to cancel the hit (nothing goes to the server)
     */
    default boolean onAttackEntity(Object target) {
        return false;
    }

    /**
     * Right click on an entity (the two interactions of Minecraft.rightClickMouse), before it goes
     * to the server — Forge's EntityInteractEvent, on the client. Hits sent through {@link
     * Targeting#interact} do not pass through here.
     *
     * <p>If cancelled, nothing goes to the server and the click carries on as if there were no
     * entity under the crosshair: the held item is used (blocking with the sword, eating,
     * shooting), as in the game when clicking the air.
     *
     * @param target the targeted entity (Entity)
     * @return true to cancel the interaction
     */
    @Experimental(since = "0.1.15")
    default boolean onInteractEntity(Object target) {
        return false;
    }

    /**
     * Click on a slot — PlayerControllerMP.windowClick, the path of every inventory click of the
     * game and of {@link Inventory} —, before it goes to the server. Applies with the inventory
     * open or closed (window 0 is the player's own).
     *
     * <p>A click requested from inside this event (answering a click with another) goes straight to
     * the game, without coming back to the mods.
     *
     * @param windowId the window ({@link Inventory#windowId})
     * @param slot the window slot; {@link Inventory#OUTSIDE} is outside it (drops the cursor item)
     * @param button the button (0 left, 1 right; for {@link Inventory#SWAP}, the hotbar number 0-8)
     * @param mode the mode ({@link Inventory#PICKUP}, {@link Inventory#QUICK_MOVE}...)
     * @return true to cancel: nothing changes and nothing goes to the server
     */
    @Experimental(since = "0.1.15")
    default boolean onWindowClick(int windowId, int slot, int button, int mode) {
        return false;
    }

    /**
     * The server changed a slot (S2FPacketSetSlot), already applied — the result of a click, of
     * picking up an item from the ground, of the server touching the inventory. Window -1 and slot
     * -1: the item on the cursor.
     *
     * @param stack the item now in the slot (ItemStack), or null if it was emptied
     */
    @Experimental(since = "0.1.15")
    default void onSlotChanged(int windowId, int slot, Object stack) {}

    /**
     * The server sent all the items of a window at once (S30PacketWindowItems), already applied: on
     * opening a chest, on joining the world.
     */
    @Experimental(since = "0.1.15")
    default void onWindowItems(int windowId) {}

    /**
     * An entity took damage — any one the player sees, the player included: the server announces it
     * with status 2 (S19PacketEntityStatus), and that announcement drives the red animation, the
     * knockback tilt and the hurt sound. How much it was, {@link #onHealthChanged} tells (when the
     * server sends the health).
     *
     * <p>Returning true removes the <b>effect</b> on the client (no red, no tilt, no sound) — the
     * damage itself belongs to the server and still happens. On the player itself, the camera tilt
     * comes from the lowered health (S06), not from here.
     *
     * @param entity who took the damage (Entity)
     * @return true to not show the effect
     */
    @Experimental(since = "0.1.15")
    default boolean onEntityHurt(Object entity) {
        return false;
    }

    /**
     * The game is closing: last chance to save what is still pending (a {@link ModConfig#saveSoon}
     * from a second ago). Runs on a shutdown thread, with the game already tearing down: write
     * files and nothing else — no drawing, network or screens. It has to be fast.
     */
    @Experimental(since = "0.1.16")
    default void onShutdown() {}

    /**
     * The connection to the server dropped (or the join was refused): the disconnect screen has
     * just opened. {@code address} is the server ({@link Server#connectingAddress}), {@code reason}
     * the reason as the game shows it, without colors. Comes before the {@link #onGuiInit} of that
     * screen, once per disconnect.
     */
    @Experimental(since = "0.1.16")
    default void onDisconnected(String address, String reason) {}

    /**
     * The relations changed ({@link Relations}: an entry, a supplier, a color), at the end of the
     * tick in which they changed — several changes in the same tick arrive as a single notice.
     * Whoever keeps something derived from the relations (colors of a list, ally count) redoes it
     * here.
     */
    @Experimental(since = "0.1.16")
    default void onRelationsChanged() {}

    /** An entity died (status 3 of S19PacketEntityStatus), before the death animation. */
    @Experimental(since = "0.1.15")
    default void onEntityDeath(Object entity) {}

    /**
     * The health or absorption (the yellow hearts) of a living entity changed, already applied: the
     * player's through S06PacketUpdateHealth, others' through the data the server synchronizes
     * (S1CPacketEntityMetadata). Damage is {@code (oldHealth + oldAbsorption) - (newHealth +
     * newAbsorption)}; negative is healing.
     *
     * <p>Servers may hide or fake other entities' health (many always send it full): then only the
     * player has real numbers, and {@link #onEntityHurt} still holds for everyone. Others'
     * absorption only arrives for players; for mobs it is not synchronized.
     *
     * @param entity the entity (EntityLivingBase)
     */
    @Experimental(since = "0.1.15")
    default void onHealthChanged(
            Object entity,
            float oldHealth,
            float newHealth,
            float oldAbsorption,
            float newAbsorption) {}

    /**
     * Should this entity vanish from the screen? Called for every entity every frame, in place of
     * its drawing — must answer quickly, without allocating.
     *
     * <p>This is not the game's invisibility: nothing is drawn, neither body, armor, name nor
     * shadow. To whoever is looking, nobody is there. The entity still exists — only the screen is
     * affected.
     *
     * @param entity the entity about to be drawn (Entity)
     * @return true to not draw it
     */
    default boolean onHideEntity(Object entity) {
        return false;
    }

    /**
     * Should this entity be drawn as a <b>ghost</b>? Called for every entity every frame, in place
     * of its drawing — must answer quickly, without allocating.
     *
     * <p>It is the same ghost the game already makes of someone invisible and on your team: the
     * silhouette at 15% opacity, without writing depth (so it does not hide what is behind it). The
     * difference is that the <b>armor, the held item and the cape are not drawn either</b> — in the
     * game they stay solid, which is why an invisible player in armor becomes "walking armor".
     *
     * <p>Whoever is hidden ({@link #onHideEntity}) does not get here: vanishing beats becoming a
     * ghost. Everything else stays the same — the name is still above the head, and hits still
     * land. Only the screen is affected.
     *
     * @param entity the entity about to be drawn (Entity)
     * @return true to draw it as a ghost
     */
    default boolean onGhostEntity(Object entity) {
        return false;
    }

    /**
     * The whole Tab list, in place of the game's: the mod draws its own (ping, filters, how many
     * players to show). Called every frame with Tab open — answer quickly. The first mod that draws
     * ends it.
     *
     * @param overlay the game's GuiPlayerTabOverlay, for what only it knows (the formatted name,
     *     the ping bars)
     * @param width screen width, in GUI pixels
     * @param scoreboard the Scoreboard; objective the list's ScoreObjective, or null
     * @return true when the mod drew: the game does not draw its own
     */
    default boolean onPlayerList(Object overlay, int width, Object scoreboard, Object objective) {
        return false;
    }

    /**
     * A player's name in the Tab list (GuiPlayerTabOverlay.getPlayerName: with the server team's
     * prefix and suffix). Called every frame with Tab open — answer quickly. Mods receive, in
     * order, the name returned by the previous one.
     *
     * @param playerInfo the player in the list (NetworkPlayerInfo)
     * @return the name to show
     */
    default String onPlayerListName(Object playerInfo, String name) {
        return name;
    }

    /**
     * Name above the head of a living entity (RendererLivingEntity.renderName: the getDisplayName,
     * with the server team). Called every frame per entity with a visible name. Mods receive, in
     * order, the name returned by the previous one.
     *
     * @param entity the entity (EntityLivingBase)
     * @param name the name (IChatComponent)
     * @return the name to show (IChatComponent)
     */
    default Object onNametag(Object entity, Object name) {
        return name;
    }

    /**
     * The world has just been drawn — blocks, entities, particles and the translucent layer —,
     * before the upper clouds and the hand: the place to draw in the world (boxes, lines, markers),
     * like Forge's RenderWorldLastEvent. The matrix is the camera's, with the origin at the
     * player's eye: add the camera entity's interpolated position to whatever you want to draw at a
     * world location. Once per frame. Leave the GL state as you found it.
     */
    @Experimental(since = "0.1.14")
    default void onRenderWorld(float partialTicks) {}

    /**
     * Server plugin packet (S3FPacketCustomPayload), already on the game thread: the channel
     * ("MC|Brand", "BungeeCord", your plugin's) and a copy of the bytes, which the mod may keep.
     * The game handles the packet the same way afterwards (and ignores channels it does not know).
     * To send one to the server: {@link Net#sendPayload}.
     */
    @Experimental(since = "0.1.14")
    default void onCustomPayload(String channel, byte[] data) {}

    /**
     * What the player is about to send — chat message or command ("/..."), before it leaves for the
     * server: the place for a client-only command, like Forge's ClientChatEvent. Mods receive, in
     * order, the message returned by the previous one.
     *
     * @return the message to send (the same or another), or null to send nothing
     */
    @Experimental(since = "0.1.14")
    default String onSendChat(String message) {
        return message;
    }

    /**
     * A sound about to play (ISound), like Forge's PlaySoundEvent: swap it for another, or silence
     * it. Called for every sound — answer quickly. Mods receive, in order, the sound returned by
     * the previous one.
     *
     * @return the sound to play (ISound, the same or another), or null to not play
     */
    @Experimental(since = "0.1.14")
    default Object onPlaySound(Object sound) {
        return sound;
    }
}
