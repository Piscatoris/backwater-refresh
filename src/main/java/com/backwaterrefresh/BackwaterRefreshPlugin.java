package com.backwaterrefresh;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.Perspective;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WorldViewLoaded;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Backwater Refresh
 *
 * Visual and performance customization for the Sailing Backwater area.
 * - Features:
 * - Enables customizability in the Backwater Sailing area.
 * - Improves FPS and clarity by hiding & replacing bubbles.
 * - Default theme: Backwater tanglegrass area repaint.
 */
@PluginDescriptor(
        name = "Backwater Refresh",
        description = "Enables customizability in Backwater to improve FPS, with a default Tanglegrass theme.",
        tags = {"sailing", "backwater", "bubbles", "tanglegrass", "fetid", "pools", "fps", "optimization" }
)
public class BackwaterRefreshPlugin extends Plugin
{
    static final String CONFIG_GROUP = "backwaterrefresh";

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private BackwaterRefreshConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private BackwaterRefreshTileOverlay tileOverlay;

    @Provides
    BackwaterRefreshConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BackwaterRefreshConfig.class);
    }

    // ---------------------------------------------------------------------
    // Backwater bubble object IDs
    // ---------------------------------------------------------------------

    /**
     * Flat, purely cosmetic surface bubbles.
     * These are just visual noise on the water.
     */
    private static final Set<Integer> FLAT_BUBBLES = ImmutableSet.of(
            60356, // Backwater flat surface bubbles (4  tiles, anim 13537)
            60357, // Backwater flat surface bubbles (4  tiles, anim 13538)
            60358  // Backwater flat surface bubbles (1  tile,  anim 13539)
    );

    /**
     * 3D barrier bubbles in the Backwater (fetid pools).
     * These can be left as vanilla bubbles, hidden entirely, or replaced
     * with a static marker model while preserving collision.
     */
    private static final Set<Integer> THREED_BUBBLES = ImmutableSet.of(
            60359, // Backwater 3D bubble (1 tile, anim 13533)
            60360, // Backwater 3D bubble (4 tiles, anim 13536)
            60361, // Backwater 3D bubble (9 tiles, anim 13532)
            60362, // Backwater 3D bubble (9 tiles, anim 13534)
            60363  // Backwater 3D bubble (9 tiles, anim 13535)
    );

    /**
     * Footprint size (in tiles) for each 3D bubble object ID.
     * Used both for dense model placement and tile marker drawing.
     */
    private static final Map<Integer, Integer> BARRIER_BUBBLE_SIZES = ImmutableMap.of(
            60359, 1,
            60360, 2,
            60361, 3,
            60362, 3,
            60363, 3
    );

    /**
     * Original Backwater slowdown and protection messages and their colours.
     * Used to detect the base game messages and to keep colour consistent
     * for replacement text.
     */
    private static final String SLOWDOWN_ORIGINAL_BODY =
            "The fetid pools significantly reduce the speed of the boat!";

    private static final String PROTECTION_ORIGINAL_BODY =
            "The burst of speed temporarily protects the boat from the fetid pools effect.";

    private static final String SLOWDOWN_COLOR_TAG = "<col=ff3045>";
    private static final String PROTECTION_COLOR_TAG = "<col=229628>";

    private static final String SLOWDOWN_ORIGINAL_MESSAGE =
            SLOWDOWN_COLOR_TAG + SLOWDOWN_ORIGINAL_BODY;

    private static final String PROTECTION_ORIGINAL_MESSAGE =
            PROTECTION_COLOR_TAG + PROTECTION_ORIGINAL_BODY;

    // ---------------------------------------------------------------------
    // Default replacement model info
    // ---------------------------------------------------------------------

    /**
     * Default model ID for bubble replacement.
     * This is hard-coded data, not discovered via reflection.
     */
    private static final int DEFAULT_MODEL_ID = BackwaterRefreshConfig.DEFAULT_BARRIER_MODEL_ID;

    /**
     * Active client-side model markers keyed by world location. For dense
     * mode we store one entry per covered tile; for low density we only
     * store the centre tile.
     */
    private final Map<WorldPoint, RuneLiteObject> activeObjects = new ConcurrentHashMap<>();

    /**
     * Simple record used by the tile overlay to know where and how big to
     * draw tile markers for the 3D bubbles.
     */
    static final class BarrierTileMarker
    {
        private final int plane;
        private final int size;
        private final int worldViewId;

        BarrierTileMarker(int plane, int size, int worldViewId)
        {
            this.plane = plane;
            this.size = size;
            this.worldViewId = worldViewId;
        }

        int getPlane()
        {
            return plane;
        }

        int getSize()
        {
            return size;
        }

        int getWorldViewId()
        {
            return worldViewId;
        }
    }

    /**
     * Tile marker metadata keyed by the bubble's world location.
     */
    private final Map<WorldPoint, BarrierTileMarker> barrierTileMarkers = new ConcurrentHashMap<>();

    /**
     * Login delay so we only scan once the scene has fully loaded.
     * -1 = disabled, >= 0 = ticks since LOGGED_IN.
     */
    private int ticksSinceLogin = 0;

    /**
     * Cached replacement model so we do not repeatedly light ModelData.
     */
    private Model cachedReplacementModel = null;
    private int cachedReplacementModelId = -1;

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    @Override
    protected void startUp()
    {
        cachedReplacementModel = null;
        cachedReplacementModelId = -1;

        overlayManager.add(tileOverlay);
        clientThread.invoke(this::scanAllViews);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(tileOverlay);

        clientThread.invoke(() ->
        {
            clearAllObjects();
            cachedReplacementModel = null;
            cachedReplacementModelId = -1;
            reloadScene();
        });
    }

    private void clearAllObjects()
    {
        for (RuneLiteObject rlo : activeObjects.values())
        {
            if (rlo != null)
            {
                rlo.setActive(false);
            }
        }
        activeObjects.clear();
        barrierTileMarkers.clear();
    }

    /**
     * Nudge the client to rebuild the scene so the original game
     * objects come back when the plugin is disabled or major config
     * is changed.
     */
    private void reloadScene()

    {
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            client.setGameState(GameState.LOADING);
        }
    }

    // ---------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();

        if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
        {
            // Leaving the world: clean up and reset.
            clearAllObjects();
            cachedReplacementModel = null;
            cachedReplacementModelId = -1;
            ticksSinceLogin = 0;
        }
        else if (state == GameState.LOGGED_IN)
        {
            // Delay first full scan until the scene is constructed.
            ticksSinceLogin = 0;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (ticksSinceLogin >= 0)
        {
            ticksSinceLogin++;
            if (ticksSinceLogin == 3)
            {
                scanAllViews();
                ticksSinceLogin = -1;
            }
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!CONFIG_GROUP.equals(event.getGroup()))
        {
            return;
        }

        final String key = event.getKey();
        if (key == null)
        {
            return;
        }

        switch (key)
        {
            // These options change which game objects we hide/replace, so
            // we need to rebuild the scene from scratch.
            case "hideSurfaceBubbles":
            case "replaceBarrierBubbles":
            case "hideBarrierBubbles":
            case "denseBarrierMarkers":
                clientThread.invoke(() ->
                {
                    clearAllObjects();
                    cachedReplacementModel = null;
                    cachedReplacementModelId = -1;
                    reloadScene();
                });
                break;

            case "randomBarrierRotation":
            case "gridAlignedRotation":
                clientThread.invoke(this::updateAllMarkerOrientation);
                break;

            default:
                break;
        }
    }

    @Subscribe
    public void onWorldViewLoaded(WorldViewLoaded event)
    {
        final WorldView wv = event.getWorldView();
        if (wv == null)
        {
            return;
        }

        clientThread.invoke(() -> scanScene(wv));
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        final GameObject obj = event.getGameObject();
        if (obj == null)
        {
            return;
        }

        processObject(obj);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE || config == null)
        {
            return;
        }

        final String plain = event.getMessage();
        final String nodeValue = event.getMessageNode() != null ? event.getMessageNode().getValue() : null;

        // Slowdown message
        if (config.replaceSlowdownMessage()
                && (SLOWDOWN_ORIGINAL_BODY.equals(plain) || SLOWDOWN_ORIGINAL_MESSAGE.equals(nodeValue)))
        {
            String replacement = config.slowdownMessageText();
            if (replacement == null || replacement.isEmpty())
            {
                replacement = BackwaterRefreshConfig.DEFAULT_SLOWDOWN_MESSAGE;
            }

            event.getMessageNode().setValue(SLOWDOWN_COLOR_TAG + replacement);
            client.refreshChat();
            return;
        }

        // Protected speed message
        if (config.replaceProtectionMessage()
                && (PROTECTION_ORIGINAL_BODY.equals(plain) || PROTECTION_ORIGINAL_MESSAGE.equals(nodeValue)))
        {
            String replacement = config.protectionMessageText();
            if (replacement == null || replacement.isEmpty())
            {
                replacement = BackwaterRefreshConfig.DEFAULT_PROTECTION_MESSAGE;
            }

            event.getMessageNode().setValue(PROTECTION_COLOR_TAG + replacement);
            client.refreshChat();
        }
    }

    // ---------------------------------------------------------------------
    // Core Logic
    // ---------------------------------------------------------------------

    private void processObject(GameObject obj)
    {
        final int id = obj.getId();

        final boolean hideFlat = config == null || config.hideSurfaceBubbles();
        final boolean hideWalls = config != null && config.hideBarrierBubbles();
        // Hide 3D bubble graphics always wins; only consider replacement
        // if we are not hiding them entirely.
        final boolean replaceWalls = !hideWalls && (config == null || config.replaceBarrierBubbles());

        // Flat cosmetic bubbles.
        if (hideFlat && FLAT_BUBBLES.contains(id))
        {
            removeObjectFromScene(obj);
            return;
        }

        // 3D blocking bubbles.
        if (THREED_BUBBLES.contains(id))
        {
            // Always register for tile markers; overlay decides whether to show.
            registerBarrierTileMarker(obj);

            if (hideWalls)
            {
                // Do not spawn any replacement models when the user has asked
                // to hide 3D bubble graphics entirely.
                removeObjectFromScene(obj);
                return;
            }

            if (replaceWalls)
            {
                // Only remove the original bubble if we successfully created
                // at least one replacement model (i.e. valid model ID).
                if (createBarrierMarker(obj))
                {
                    removeObjectFromScene(obj);
                }
            }
        }
    }

    /**
     * Record metadata for the 3D bubble footprint so the overlay can
     * draw tile markers later.
     */
    private void registerBarrierTileMarker(GameObject bubbleObj)
    {
        final WorldPoint wp = bubbleObj.getWorldLocation();
        if (wp == null)
        {
            return;
        }

        final Integer size = BARRIER_BUBBLE_SIZES.get(bubbleObj.getId());
        if (size == null)
        {
            return;
        }

        if (bubbleObj.getLocalLocation() == null)
        {
            return;
        }

        final WorldView view = bubbleObj.getWorldView();
        final int worldViewId = view != null ? view.getId() : -1;

        barrierTileMarkers.put(wp, new BarrierTileMarker(bubbleObj.getPlane(), size, worldViewId));
    }


    /**
     * Spawn one or more RuneLiteObjects to stand in for the 3D bubble.
     * In high density mode there is one model per tile of the NxN footprint;
     * otherwise we drop a single marker at the object's centre.
     *
     * Returns {@code true} if we placed any replacement models at all
     * (i.e. the custom model ID was valid).
     */
    private boolean createBarrierMarker(GameObject bubbleObj)
    {
        final WorldPoint centreWp = bubbleObj.getWorldLocation();
        if (centreWp == null)
        {
            return false;
        }

        final Model model = getReplacementModel();
        if (model == null)
        {
            // Invalid / unavailable model ID – keep the original bubbles
            // so we do not create invisible collision.
            return false;
        }

        final Integer sizeObj = BARRIER_BUBBLE_SIZES.get(bubbleObj.getId());
        final int size = sizeObj != null ? sizeObj : 1;

        final boolean dense = config != null && config.denseBarrierMarkers() && size > 1;

        if (!dense)
        {
            createSingleMarker(centreWp, bubbleObj, model);
        }
        else
        {
            createDenseMarkers(centreWp, bubbleObj, model, size);
        }

        // At this point we have at least one valid RuneLiteObject.
        return true;
    }

    /**
     * Low-density behaviour: a single marker at the bubble's centre tile.
     * This is the same position used when High Density is off, so the
     * behaviour is unchanged.
     *
     * Orientation is derived deterministically from the bubble's world
     * location so it remains stable across scene scans and chunk loads.
     */
    private void createSingleMarker(WorldPoint keyWp, GameObject bubbleObj, Model model)
    {
        RuneLiteObject existing = activeObjects.get(keyWp);
        if (existing != null)
        {
            existing.setActive(false);
        }

        final RuneLiteObject rlo = client.createRuneLiteObject();
        rlo.setModel(model);
        rlo.setOrientation(computeMarkerOrientation(keyWp));

        final WorldView view = bubbleObj.getWorldView();
        if (view != null)
        {
            rlo.setWorldView(view.getId());
        }

        final LocalPoint centreLocal = bubbleObj.getLocalLocation();
        if (centreLocal != null)
        {
            rlo.setLocation(centreLocal, bubbleObj.getPlane());
            rlo.setActive(true);
            activeObjects.put(keyWp, rlo);
        }
    }

    /**
     * High-density behaviour: one marker per tile in the bubble's NxN footprint.
     *
     * TileObject.getLocalLocation() is the geometric centre of the object for
     * all sizes. For even-sized footprints this centre lies at the intersection
     * of tiles, not at a tile centre.
     *
     * To keep markers aligned with the real bubble tiles for every size, we
     * derive the south-west tile centre from the object centre and then place
     * one RuneLiteObject at the centre of each tile in the footprint.
     *
     * Orientation for each tile is derived deterministically from that tile's
     * world location, so it remains stable across scene scans and chunk loads.
     */
    private void createDenseMarkers(WorldPoint centreWp, GameObject bubbleObj, Model model, int size)
    {
        final LocalPoint centreLocal = bubbleObj.getLocalLocation();
        final WorldView view = bubbleObj.getWorldView();
        final int plane = bubbleObj.getPlane();

        if (centreLocal == null || view == null)
        {
            // Fallback: if for some reason we cannot resolve the centre,
            // place a single marker rather than doing nothing.
            createSingleMarker(centreWp, bubbleObj, model);
            return;
        }

        final int tileSize = Perspective.LOCAL_TILE_SIZE;
        final int halfTile = tileSize / 2;
        final int halfExtent = size * tileSize / 2;

        // Local centre of the south-west tile in the footprint.
        final int swLocalX = centreLocal.getX() - halfExtent + halfTile;
        final int swLocalY = centreLocal.getY() - halfExtent + halfTile;

        // World location of the south-west tile in the footprint.
        final WorldPoint swWorld = computeSouthWestWorldPoint(centreWp, size);

        for (int dx = 0; dx < size; dx++)
        {
            for (int dy = 0; dy < size; dy++)
            {
                final WorldPoint tileWp = new WorldPoint(
                        swWorld.getX() + dx,
                        swWorld.getY() + dy,
                        plane
                );

                RuneLiteObject existing = activeObjects.get(tileWp);
                if (existing != null)
                {
                    existing.setActive(false);
                }

                final RuneLiteObject rlo = client.createRuneLiteObject();
                rlo.setModel(model);
                rlo.setOrientation(computeMarkerOrientation(tileWp));
                rlo.setWorldView(view.getId());

                final int localX = swLocalX + dx * tileSize;
                final int localY = swLocalY + dy * tileSize;
                final LocalPoint tileLocal = new LocalPoint(localX, localY);

                rlo.setLocation(tileLocal, plane);
                rlo.setActive(true);
                activeObjects.put(tileWp, rlo);
            }
        }
    }

    /**
     * Compute the south‑west tile of an object's footprint from the
     * GameObject world location and logical size.
     *
     * For 1x1 and other odd-sized objects, getWorldLocation() points at the
     * centre tile, so the south‑west tile is that centre minus size/2 tiles.
     *
     * For even-sized objects such as 2x2, getWorldLocation() is already the
     * south‑west tile, per the TileObject.getWorldLocation() contract.
     */
    private static WorldPoint computeSouthWestWorldPoint(WorldPoint centreWp, int size) {
        if (centreWp == null || size <= 0) {
            return centreWp;
        }

        if (size == 1) {
            return centreWp;
        }

        if ((size & 1) == 0) {
            // Even footprint (e.g. 2x2): world location is already the south‑west tile.
            return centreWp;
        }

        final int half = size / 2;
        return new WorldPoint(
                centreWp.getX() - half,
                centreWp.getY() - half,
                centreWp.getPlane()
        );
    }

    /**
     * Compute an orientation for a replacement marker based on the current
     * configuration and the tile's world location.
     *
     * The hash makes rotations look random while remaining stable for a given
     * world point so they do not change across scene scans or chunk loads.
     */
    private int computeMarkerOrientation(WorldPoint wp)
    {
        if (config == null || !config.randomBarrierRotation() || config.hideBarrierBubbles())
        {
            return 0;
        }

        if (wp == null)
        {
            return 0;
        }

        // Simple integer hash of the world coordinates.
        int h = wp.getX() * 374761393 + wp.getY() * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;

        final boolean gridAligned = config.gridAlignedRotation();
        if (gridAligned)
        {
            int index = (h & 0x7FFFFFFF) % 4;
            return index * 512;
        }

        return (h & 0x7FFFFFFF) % 2048;
    }

    /**
     * Re-apply orientations to all active replacement markers so config
     * changes take effect immediately.
     *
     * Because orientation is derived from world coordinates, toggling
     * options will always produce the same pattern for a given layout
     * of bubbles, rather than re-rolling a new random field each time.
     */
    private void updateAllMarkerOrientation()
    {
        if (activeObjects.isEmpty())
        {
            return;
        }

        for (Map.Entry<WorldPoint, RuneLiteObject> entry : activeObjects.entrySet())
        {
            final WorldPoint wp = entry.getKey();
            final RuneLiteObject rlo = entry.getValue();

            if (wp == null || rlo == null)
            {
                continue;
            }

            rlo.setOrientation(computeMarkerOrientation(wp));
        }
    }

    /**
     * Plugin‑hub‑safe way to resolve the replacement model: we use a
     * known model ID and load it via the public API. No reflection or
     * ObjectComposition internals are touched.
     */
    private Model getReplacementModel()
    {

        // Use a fixed, hub-safe model id
        final int modelId = DEFAULT_MODEL_ID;

        if (cachedReplacementModel != null && cachedReplacementModelId == modelId)
        {
            return cachedReplacementModel;
        }

        final ModelData data = client.loadModelData(modelId);
        if (data == null)
        {
            return null;
        }

        cachedReplacementModel = data.light();
        cachedReplacementModelId = modelId;
        return cachedReplacementModel;
    }

    /**
     * Safely remove a bubble GameObject from its Scene, handling both
     * top‑level and per‑WorldView scenes.
     */
    private void removeObjectFromScene(GameObject obj)
    {
        if (obj == null)
        {
            return;
        }

        try
        {
            final WorldView wv = obj.getWorldView();
            if (wv != null)
            {
                final Scene scene = wv.getScene();
                if (scene != null)
                {
                    scene.removeGameObject(obj);
                }
            }
            else
            {
                final Scene scene = client.getScene();
                if (scene != null)
                {
                    scene.removeGameObject(obj);
                }
            }
        }
        catch (Exception ignored)
        {
            // Fail-safe: never crash the client if the underlying scene
            // changes while we are trying to remove an object.
        }
    }

    // ---------------------------------------------------------------------
    // Scanning
    // ---------------------------------------------------------------------

    /**
     * Scan all currently-known WorldViews (or the single Scene on older
     * clients) for Backwater bubbles and process them.
     */
    private void scanAllViews()
    {
        final WorldView top = client.getTopLevelWorldView();
        if (top != null)
        {
            scanRecursive(top);
        }
        else
        {
            final Scene s = client.getScene();
            if (s != null)
            {
                scanTiles(s.getTiles());
                scanTiles(s.getExtendedTiles());
            }
        }
    }

    private void scanRecursive(WorldView wv)
    {
        if (wv == null)
        {
            return;
        }

        scanScene(wv);
        for (WorldView child : wv.worldViews())
        {
            scanRecursive(child);
        }
    }

    private void scanScene(WorldView wv)
    {
        if (wv == null)
        {
            return;
        }

        final Scene scene = wv.getScene();
        if (scene == null)
        {
            return;
        }

        scanTiles(scene.getTiles());
        scanTiles(scene.getExtendedTiles());
    }

    /**
     * Safe scanning: collect candidate GameObjects first, then modify the
     * scene graph afterwards. This avoids issues when removing objects
     * while iterating the arrays provided by the client. Multi-tile
     * objects are deduplicated so we only process each instance once.
     */
    private void scanTiles(Tile[][][] tiles)
    {
        if (tiles == null)
        {
            return;
        }

        final List<GameObject> toProcess = new ArrayList<>();
        final Set<GameObject> seen = new HashSet<>();

        for (int z = 0; z < tiles.length; z++)
        {
            Tile[][] plane = tiles[z];
            if (plane == null)
            {
                continue;
            }

            for (int x = 0; x < plane.length; x++)
            {
                Tile[] column = plane[x];
                if (column == null)
                {
                    continue;
                }

                for (int y = 0; y < column.length; y++)
                {
                    Tile tile = column[y];
                    if (tile == null)
                    {
                        continue;
                    }

                    GameObject[] objects = tile.getGameObjects();
                    if (objects == null)
                    {
                        continue;
                    }

                    for (GameObject obj : objects)
                    {
                        if (obj != null)
                        {
                            int id = obj.getId();
                            if ((FLAT_BUBBLES.contains(id) || THREED_BUBBLES.contains(id)) && seen.add(obj))
                            {
                                toProcess.add(obj);
                            }
                        }
                    }
                }
            }
        }

        for (GameObject obj : toProcess)
        {
            processObject(obj);
        }
    }

    Map<WorldPoint, BarrierTileMarker> getBarrierTileMarkers()
    {
        return barrierTileMarkers;
    }
}
