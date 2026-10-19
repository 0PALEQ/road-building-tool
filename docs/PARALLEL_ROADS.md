# Parallel placement

The **||** switch beside the snapping controls adds an offset copy to the road
being drawn. It works with straight, simple curve, complex curve, freehand and
roundabout placement and stays enabled when switching tools.

- Left-click **||** to enable or disable the copy.
- Scroll over **||** to set the distance from 1 to 256 blocks (default 16).
- Right-click **||** to switch left/right. Hover to read the distance and side.
- Draw and place the road normally; both roads appear in the preview.

The copy uses the same preset and elevation profile. Spacing is measured along
the local perpendicular at every point of the primary road, including both
endpoints. Straight roads remain straight; bends follow a parallel offset curve,
and roundabouts become concentric rings. Curved offsets use an adaptive spline
with at most 64 anchors to preserve spacing within a small fraction of a block.

An inside offset larger than the bend radius would fold back on itself. Such
placements, and curves exceeding the detail limit, are blocked with a message
asking you to reduce spacing or change sides.

The server preflights both paths and their intersections and commits them as a
single construction operation. Validation failures leave both unbuilt. Large
pairs use the existing incremental construction and recovery workflow.
