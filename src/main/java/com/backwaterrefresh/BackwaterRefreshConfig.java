package com.backwaterrefresh;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * Configuration for the Backwater Refresh plugin.
 */
@ConfigGroup(BackwaterRefreshPlugin.CONFIG_GROUP)
public interface BackwaterRefreshConfig extends Config
{
    /**
     * Default model ID for barrier bubble replacement graphics.
     * Currently uses jungle grass.
     */
    int DEFAULT_BARRIER_MODEL_ID = 4741;

    /**
     * Default replacement text for the slowdown message when entering the fetid pools.
     */
    String DEFAULT_SLOWDOWN_MESSAGE = "The backwater tanglegrass significantly reduces the speed of the boat!";

    /**
     * Default replacement text for the protected-speed message when the burst of speed is active.
     */
    String DEFAULT_PROTECTION_MESSAGE = "The burst of speed briefly protects the boat from the tanglegrass effect.";

    // ---------------------------------------------------------------------
    // Sections
    // ---------------------------------------------------------------------

    @ConfigSection(
            name = "Bubble Models",
            description = "Configure how Backwater bubbles and pools are shown, hidden, or "
                    + "replaced with static models.",
            position = 0
    )
    String MODEL_SECTION = "bubbleModels";

    @ConfigSection(
            name = "Chat Messages",
            description = "Customise Backwater slowdown and protection chat messages.",
            position = 1
    )
    String CHAT_SECTION = "chatMessages";

    @ConfigSection(
            name = "Tile Markers",
            description = "Optional tile overlays for the 3D bubble footprints.",
            position = 2
    )
    String TILE_SECTION = "tileMarkers";

    // ---------------------------------------------------------------------
    // Model behaviour
    // ---------------------------------------------------------------------

    @ConfigItem(
            keyName = "hideSurfaceBubbles",
            name = "Hide surface bubbles",
            description = "Hide the flat, purely cosmetic Backwater surface bubbles (Obj IDs 60356-60358).",
            position = 0,
            section = MODEL_SECTION
    )
    default boolean hideSurfaceBubbles()
    {
        return true;
    }

    @ConfigItem(
            keyName = "replaceBarrierBubbles",
            name = "Replace fetid pools",
            description = "Replace Backwater 3D bubble fetid pools (Obj IDs 60359-60363) with a static model."
                    + " Has no effect if fetid pools are hidden.",
            position = 1,
            section = MODEL_SECTION
    )
    default boolean replaceBarrierBubbles()
    {
        return true;
    }

    @ConfigItem(
            keyName = "denseBarrierMarkers",
            name = "High Density",
            description = "If enabled, large fetid pools (2x2, 3x3) get one "
                    + "replacement model per tile in their footprint. If disabled, a single "
                    + "model is placed at the centre. Disable for large FPS boost.",
            position = 2,
            section = MODEL_SECTION
    )
    default boolean denseBarrierMarkers()
    {
        // High density is the default / reset behaviour.
        return true;
    }

    @ConfigItem(
            keyName = "randomBarrierRotation",
            name = "Random rotation",
            description = "Randomise the rotation of replacement models for fetid pools.",
            position = 3,
            section = MODEL_SECTION
    )
    default boolean randomBarrierRotation()
    {
        return true;
    }

    @ConfigItem(
            keyName = "gridAlignedRotation",
            name = "Grid-aligned rotation",
            description = "When random rotation is enabled, restrict rotations to quarter turns (0°, 90°, 180°, 270°).",
            position = 4,
            section = MODEL_SECTION
    )
    default boolean gridAlignedRotation()
    {
        return false;
    }

    @ConfigItem(
            keyName = "hideBarrierBubbles",
            name = "Hide fetid pools",
            description = "Hide all 3D bubble (fetid pool) graphics entirely while leaving collision" +
                    " and slowdown unchanged. This takes precedence over model replacement.",
            position = 6,
            section = MODEL_SECTION
    )
    default boolean hideBarrierBubbles()
    {
        return false;
    }

    // ---------------------------------------------------------------------
    // Chat messages
    // ---------------------------------------------------------------------

    @ConfigItem(
            keyName = "replaceSlowdownMessage",
            name = "Replace slowdown message",
            description = "Replace the default Backwater slowdown chat message when the boat enters the fetid pools.",
            position = 0,
            section = CHAT_SECTION
    )
    default boolean replaceSlowdownMessage()
    {
        return true;
    }

    @ConfigItem(
            keyName = "slowdownMessageText",
            name = "Slowdown message text",
            description = "Text to show when the boat is slowed by the Backwater fetid pools.",
            position = 1,
            section = CHAT_SECTION
    )
    default String slowdownMessageText()
    {
        return DEFAULT_SLOWDOWN_MESSAGE;
    }

    @ConfigItem(
            keyName = "replaceProtectionMessage",
            name = "Replace protection message",
            description = "Replace the default Backwater chat message when the burst of speed protects the boat from the fetid pools.",
            position = 2,
            section = CHAT_SECTION
    )
    default boolean replaceProtectionMessage()
    {
        return true;
    }

    @ConfigItem(
            keyName = "protectionMessageText",
            name = "Protection message text",
            description = "Text to show when the burst of speed protects the boat from the Backwater fetid pools.",
            position = 3,
            section = CHAT_SECTION
    )
    default String protectionMessageText()
    {
        return DEFAULT_PROTECTION_MESSAGE;
    }

    // ---------------------------------------------------------------------
    // Tile overlay
    // ---------------------------------------------------------------------

    @ConfigItem(
            keyName = "showBarrierTileMarkers",
            name = "Show tile markers",
            description = "Draw tile markers over the 3D bubble (fetid pool) footprints. "
                    + "Defaults to cosmetic color tint. "
                    + "Note: due to engine limitations these jitter while Sailing. Costs FPS to draw.",
            position = 0,
            section = TILE_SECTION
    )
    default boolean showBarrierTileMarkers()
    {
        return false;
    }

    @ConfigItem(
            keyName = "showOverlapTileMarkers",
            name = "Show overlap",
            description = "Only applies while tile markers are enabled. If enabled, draw full-sized bubble footprints"
                    + " so overlapping pools darken where they intersect. "
                    + "If disabled, draw a single 1x1 tile marker per affected tile (at higher FPS cost).",
            position = 1,
            section = TILE_SECTION
    )
    default boolean showOverlapTileMarkers()
    {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "tileFillColor",
            name = "Tile fill color",
            description = "Fill color (including alpha) for 3D bubble tile markers.",
            position = 2,
            section = TILE_SECTION
    )
    default Color tileFillColor()
    {
        // Dark swamp green, semi-transparent by default.
        return new Color(20, 50, 30, 25);
    }

    @Alpha
    @ConfigItem(
            keyName = "tileBorderColor",
            name = "Tile border color",
            description = "Outline color (including alpha) for 3D bubble tile markers.",
            position = 3,
            section = TILE_SECTION
    )
    default Color tileBorderColor()
    {
        // Dark swamp green, fully transparent by default.
        return new Color(20, 50, 30, 0);
    }
}
