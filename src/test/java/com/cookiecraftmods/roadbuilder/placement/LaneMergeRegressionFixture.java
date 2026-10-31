package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import com.cookiecraftmods.roadbuilder.preset.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Geometry reproducing a merge at a sloping road continuation. */
final class LaneMergeRegressionFixture {
    static List<RoadSegment> roads() {
        Map<RoadPoint, RoadNodeId> nodes = new HashMap<>();
        List<ReferenceGeometry> geometries = List.of(
            new MultiCurveGeometry(List.of(
                new RoadPoint(-518.8757289399642, 77.0, 265.98974014969116),
                new RoadPoint(-518.8747987907278, 77.00000634677254, 265.9893566011427),
                new RoadPoint(-500.5821122389756, 77.12498554211135, 258.38151482149203),
                new RoadPoint(-482.335783448781, 77.24997274464316, 250.66315213956773),
                new RoadPoint(-464.1364801738647, 77.37496680857636, 242.83455100348448),
                new RoadPoint(-445.98486843623164, 77.4999665890025, 234.89599790472747),
                new RoadPoint(-427.88161250546386, 77.6249709417443, 226.847783363699),
                new RoadPoint(-409.82737487677895, 77.74997872321018, 218.69020191558343),
                new RoadPoint(-391.82281624888566, 77.87498879024825, 210.42355209695947),
                new RoadPoint(-373.86859550160636, 78.0, 202.04813643315052),
                new RoadPoint(-355.9653696734606, 78.12501120975175, 193.56426142640873),
                new RoadPoint(-338.11379393844413, 78.25002127678984, 184.97223754457684),
                new RoadPoint(-320.31452158248544, 78.3750290582557, 176.27237921043894),
                new RoadPoint(-302.56820398004623, 78.50003341099749, 167.4650047919947),
                new RoadPoint(-284.8754905701052, 78.62503319142364, 158.55043659329579),
                new RoadPoint(-267.23702883172064, 78.75002725535684, 149.52900084593233),
                new RoadPoint(-249.6534642591494, 78.87501445788865, 140.40102770115269),
                new RoadPoint(-232.1254403375059, 78.99999365322749, 131.1668512231366),
                new RoadPoint(-232.124551608312, 79.0, 131.16637957972134))),
            new CubicBezierGeometry(
                new RoadPoint(40.728087446357286, 73.12054714222165, 126.39346474448578),
                new RoadPoint(47.93183362353438, 73.38405858990673, 146.8258176970018),
                new RoadPoint(50.87291622002625, 73.6469665063225, 168.7504604197826),
                new RoadPoint(49.303216164895304, 73.90346219750883, 190.35866941872024)),
            new CubicBezierGeometry(
                new RoadPoint(49.303216164895304, 73.90346219750883, 190.35866941872024),
                new RoadPoint(49.10561931271848, 73.93575036683752, 193.07875219212167),
                new RoadPoint(48.8376874146605, 73.96793432057338, 195.79373387520613),
                new RoadPoint(48.5, 74.0, 198.5)),
            new CubicBezierGeometry(
                new RoadPoint(48.5, 74.0, 198.5),
                new RoadPoint(44.24022916233693, 69.73282821395885, 232.6382977773759),
                new RoadPoint(56.153997961527615, 70.11202059739729, 266.9565911455217),
                new RoadPoint(67.45227570240269, 71.26233197418395, 299.7102700473529)));
        List<RoadSegment> roads = new ArrayList<>();
        for (var geometry : geometries) {
            roads.add(RoadSegment.path(new RoadSegmentId(UUID.randomUUID()),
                    new RoadId(UUID.randomUUID()),
                    nodes.computeIfAbsent(geometry.start(), p -> new RoadNodeId(UUID.randomUUID())),
                    nodes.computeIfAbsent(geometry.end(), p -> new RoadNodeId(UUID.randomUUID())),
                    geometry, new ResourceLocation("roadbuilder", "merge_regression"), 1,
                    new RoadPresetSnapshot(17, new ResourceLocation("minecraft", "gray_concrete"), List.of(
                            new LaneComponent(LaneType.VEHICLE, 4, -2.5, LaneTravelDirection.FORWARD),
                            new LaneComponent(LaneType.VEHICLE, 4, 2.5, LaneTravelDirection.FORWARD))),
                    TrafficHandedness.RHT, List.of(RoadLaneId.create(), RoadLaneId.create())));
        }
        return roads;
    }
}
