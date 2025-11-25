package com.backwaterrefresh;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Simple tile overlay for 3D bubble footprints.
 *
 * This is intentionally conservative: tiles are only drawn when their
 * WorldPoint can be mapped into the current scene. At extreme distances
 * markers will simply stop drawing rather than "floating" onto the wrong
 * chunk.
 */
public class BackwaterRefreshTileOverlay extends Overlay
{
    private final Client client;
    private final BackwaterRefreshPlugin plugin;
    private final BackwaterRefreshConfig config;

    // Reusable set to avoid per-frame allocations when collecting covered tiles
    private final Set<WorldPoint> coveredTiles = new HashSet<>();

    @Inject
    private BackwaterRefreshTileOverlay(
            Client client,
            BackwaterRefreshPlugin plugin,
            BackwaterRefreshConfig config
    )
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return null;
        }

        if (!config.showBarrierTileMarkers())
        {
            return null;
        }

        final Color fill = config.tileFillColor();
        final Color border = config.tileBorderColor();

        if (config.showOverlapTileMarkers())
        {
            // Original behaviour: draw one polygon per fetid pool footprint,
            // allowing overlaps to darken visually where fetid pools intersect.
            for (Map.Entry<WorldPoint, BackwaterRefreshPlugin.BarrierTileMarker> entry
                    : plugin.getBarrierTileMarkers().entrySet())
            {
                final WorldPoint wp = entry.getKey();
                final BackwaterRefreshPlugin.BarrierTileMarker marker = entry.getValue();

                if (marker.getPlane() != client.getPlane())
                {
                    continue;
                }

                // Convert this pool tile back into a LocalPoint relative to
                // the current scene. If this returns null (e.g. tile is in a
                // different chunk / view) we simply skip drawing it.
                LocalPoint centre = LocalPoint.fromWorld(client, wp);
                if (centre == null)
                {
                    continue;
                }

                final int size = marker.getSize();

                if (size == 2)
                {
                    // For 2x2 pools the GameObject world location points at the
                    // south‑west tile; adjust by half a tile so the polygon is
                    // centred over the full footprint.
                    final int halfTile = Perspective.LOCAL_TILE_SIZE / 2;
                    centre = new LocalPoint(
                            centre.getX() + halfTile,
                            centre.getY() + halfTile
                    );
                }

                final Polygon poly = Perspective.getCanvasTileAreaPoly(
                        client,
                        centre,
                        size
                );

                if (poly == null)
                {
                    continue;
                }

                if (fill != null)
                {
                    graphics.setColor(fill);
                    graphics.fill(poly);
                }

                if (border != null)
                {
                    graphics.setColor(border);
                    graphics.draw(poly);
                }
            }
        }
        else
        {
            // Draw a single 1x1 tile marker for each tile covered by any
            // pool footprint. Overlapping footprints share a tile marker
            // instead of layering multiple markers.
            coveredTiles.clear();

            for (Map.Entry<WorldPoint, BackwaterRefreshPlugin.BarrierTileMarker> entry
                    : plugin.getBarrierTileMarkers().entrySet())
            {
                final WorldPoint centreWp = entry.getKey();
                final BackwaterRefreshPlugin.BarrierTileMarker marker = entry.getValue();

                if (marker.getPlane() != client.getPlane())
                {
                    continue;
                }

                final int size = marker.getSize();
                if (size <= 0)
                {
                    continue;
                }

                final WorldPoint sw = computeSouthWestWorldPoint(centreWp, size);

                for (int dx = 0; dx < size; dx++)
                {
                    for (int dy = 0; dy < size; dy++)
                    {
                        coveredTiles.add(new WorldPoint(
                                sw.getX() + dx,
                                sw.getY() + dy,
                                marker.getPlane()
                        ));
                    }
                }
            }

            for (WorldPoint tileWp : coveredTiles)
            {
                final LocalPoint lp = LocalPoint.fromWorld(client, tileWp);
                if (lp == null)
                {
                    continue;
                }

                final Polygon poly = Perspective.getCanvasTileAreaPoly(
                        client,
                        lp,
                        1
                );

                if (poly == null)
                {
                    continue;
                }

                if (fill != null)
                {
                    graphics.setColor(fill);
                    graphics.fill(poly);
                }

                if (border != null)
                {
                    graphics.setColor(border);
                    graphics.draw(poly);
                }
            }
        }

        return null;
    }

    /**
     * Compute the south‑west tile of an object's footprint from the
     * GameObject world location and logical size.
     *
     * For 1x1 and other odd-sized objects, the world location points at the
     * centre tile, so the south‑west tile is that centre minus size/2 tiles.
     *
     * For even-sized objects such as 2x2, the world location is already the
     * south‑west tile.
     */
    private static WorldPoint computeSouthWestWorldPoint(WorldPoint centreWp, int size)
    {
        if (centreWp == null || size <= 0)
        {
            return centreWp;
        }

        if (size == 1)
        {
            return centreWp;
        }

        if ((size & 1) == 0)
        {
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
}
