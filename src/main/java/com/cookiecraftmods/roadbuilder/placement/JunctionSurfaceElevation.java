package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

final class JunctionSurfaceElevation {
    private record Chord(RoadPoint a, RoadPoint b, double from, double to) {}
    private record Arm(RoadSegment road, List<Chord> chords, double reach,
                       double hold, double endY, double endSlope) {}
    private final List<Arm> arms = new ArrayList<>();
    private final double fallback;
    private final boolean level;

    JunctionSurfaceElevation(RoadNode node, Collection<RoadSegment> incident) {
        fallback = node.position().y();
        level = incident.size() >= 3 && AuxiliaryLanePorts.attachments(node.id(), incident).isEmpty()
                && !RoadForkJunction.isFork(node, List.copyOf(incident));
        double width = incident.stream().mapToDouble(r -> r.presetSnapshot().surfaceBlockWidth()).max().orElse(1);
        for (var road : incident) {
            var geometry = road.referenceGeometry();
            boolean atStart = road.startNodeId().equals(node.id());
            double reach = Math.min(48, level ? geometry.length()/2 : geometry.length());
            double hold = Math.min(width/2 + 2, reach*0.75);
            double sampledReach = Math.min(48, geometry.length());
            int count = Math.max(1, (int)Math.ceil(sampledReach/0.5));
            List<Chord> chords = new ArrayList<>();
            for (int i=0; i<count; i++) {
                double from=sampledReach*i/count, to=sampledReach*(i+1)/count;
                chords.add(new Chord(geometry.sample(atStart?from:geometry.length()-from).position(),
                        geometry.sample(atStart?to:geometry.length()-to).position(), from, to));
            }
            var end = geometry.sample(atStart?reach:geometry.length()-reach);
            arms.add(new Arm(road, chords, reach, hold, end.position().y(),
                    end.tangent().y()*(atStart?1:-1)));
        }
    }

    double height(double x, double z) {
        double weighted=0, total=0, closest=Double.POSITIVE_INFINITY, selected=fallback;
        for (var arm : arms) {
            double nearest=Double.POSITIVE_INFINITY, y=fallback;
            for (var c : arm.chords()) {
                double dx=c.b.x()-c.a.x(), dz=c.b.z()-c.a.z(), divisor=dx*dx+dz*dz;
                if (divisor<1e-10) continue;
                double t=Math.max(0,Math.min(1,((x-c.a.x())*dx+(z-c.a.z())*dz)/divisor));
                double distance=Math.pow(x-c.a.x()-t*dx,2)+Math.pow(z-c.a.z()-t*dz,2);
                if (distance<nearest) {
                    nearest=distance;
                    double along=c.from()+(c.to()-c.from())*t;
                    y=level && along<arm.reach()?profile(arm,along):c.a.y()+(c.b.y()-c.a.y())*t;
                }
            }
            if (nearest<closest) { closest=nearest; selected=y; }
            double weight=1/Math.pow(0.25+nearest,2);
            weighted+=weight*y; total+=weight;
        }
        return level?selected:total==0?fallback:weighted/total;
    }

    private double profile(Arm arm, double distance) {
        if (distance<=arm.hold()) return fallback;
        double length=arm.reach()-arm.hold(), t=(distance-arm.hold())/length;
        double middleSlope=2*(arm.endY()-fallback)/length-arm.endSlope()/2;
        if (t<=0.5) return fallback+middleSlope*length*t*t;
        double remaining=1-t;
        return arm.endY()-arm.endSlope()*length*remaining
                -(middleSlope-arm.endSlope())*length*remaining*remaining;
    }

    List<PlannedBlock> approaches(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            RoadNode node, int maximumBlocks, int maximumWork) {
        if (!level) return List.of();
        List<PlannedBlock> blocks=new ArrayList<>();
        for (var arm : arms) {
            if (arm.chords().stream().allMatch(c -> Math.abs(c.a().y()-fallback)<1e-8
                    && Math.abs(c.b().y()-fallback)<1e-8)) continue;
            var road=arm.road();
            var geometry=road.referenceGeometry();
            boolean atStart=road.startNodeId().equals(node.id());
            var resolved=RoadSurfaceResolver.resolve(road.presetSnapshot().surfaceColumns());
            if (!(resolved instanceof RoadSurfaceResolver.Result.Success success)) continue;
            // Keep the original direction for asymmetric cross-sections.
            double from=atStart?0:geometry.length()-arm.reach();
            ReferenceGeometry local=new ReferenceGeometry() {
                public double length() { return arm.reach(); }
                public RoadPoint start() { return sample(0).position(); }
                public RoadPoint end() { return sample(length()).position(); }
                public PathSample sample(double distance) {
                    var original=geometry.sample(Math.min(geometry.length(),from+distance));
                    var point=original.position();
                    return new PathSample(distance,new RoadPoint(point.x(),
                            profile(arm,atStart?distance:arm.reach()-distance),point.z()),original.tangent());
                }
            };
            var result=new RoadPathPlanner().plan(new RoadPathPlanRequest(dimension,local,success.surface()),
                    maximumBlocks,maximumWork,ConstructionLimits.UNRESTRICTED_MAXIMUM_GRADE);
            if (!(result instanceof BlockPlanResult.Success planned))
                throw new IllegalArgumentException("junction approach planning limit exceeded");
            blocks.addAll(planned.plan().blocks());
            if (blocks.size()>maximumBlocks) throw new IllegalArgumentException("junction block limit exceeded");
        }
        return blocks;
    }

    /** Remove the old approach surface where the junction replaces its elevation. */
    static List<PlannedBlock> clearSupersededSurfaces(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            List<PlannedBlock> surface,RoadNode node,
            Collection<RoadSegment> incident,int maximumBlocks,int maximumWork) {
        Map<BlockPos,PlannedBlock> blocks=new LinkedHashMap<>();
        Map<Long,Integer> columns=new HashMap<>();
        for(var b:surface) {
            blocks.put(b.position(),b);
            columns.merge(column(b.position()),b.position().getY(),Math::max);
        }
        if (blocks.size()>maximumBlocks) throw new IllegalArgumentException("junction block limit exceeded");
        for(var road:incident) {
            var resolved=RoadSurfaceResolver.resolve(road.presetSnapshot().surfaceColumns());
            if(!(resolved instanceof RoadSurfaceResolver.Result.Success success)) continue;
            var geometry=road.referenceGeometry();
            double fraction=Math.min(1,48/geometry.length());
            boolean atStart=road.startNodeId().equals(node.id());
            if(fraction<1) {
                double cut=atStart?fraction:1-fraction;
                var parts=ReferenceGeometryEditor.split(geometry,List.of(cut),List.of(geometry.sample(geometry.length()*cut).position()));
                geometry=parts.get(atStart?0:1);
            }
            var result=new RoadPathPlanner().plan(new RoadPathPlanRequest(
                    dimension,geometry,success.surface()),maximumBlocks,maximumWork,
                    ConstructionLimits.UNRESTRICTED_MAXIMUM_GRADE);
            if(!(result instanceof BlockPlanResult.Success original)) continue;
            for(var old:original.plan().blocks()) {
                Integer newY=columns.get(column(old.position()));
                if(newY==null || newY==old.position().getY()) continue;
                // Clear only the displaced road/deck and its rail, not an entire terrain column.
                for(int dy=-RoadStructurePlanner.DECK_THICKNESS;dy<=2;dy++) {
                    var pos=old.position().above(dy);
                    if(pos.getY()<=newY || blocks.containsKey(pos)) continue;
                    if(blocks.size()>=maximumBlocks) throw new IllegalArgumentException("junction block limit exceeded");
                    blocks.put(pos,new PlannedBlock(pos,Blocks.AIR.defaultBlockState()));
                }
            }
        }
        return List.copyOf(blocks.values());
    }

    private static long column(BlockPos p) { return ((long)p.getX()<<32)^(p.getZ()&0xffffffffL); }
}
