package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import com.cookiecraftmods.roadbuilder.preset.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.*;

/** Preserve the continuous outside edges after lane-connector pavement is laid. */
final class LaneMergeMarkingPlanner {
    private record Core(RoadSegmentId id,List<RoadPoint> points,double halfWidth) {}

    static List<PlannedBlock> restore(ResourceKey<Level> dimension,RoadNode node,
            Collection<RoadSegment> roads,List<PlannedBlock> surface,int maximumBlocks,int maximumWork) {
        Map<BlockPos,PlannedBlock> result=new LinkedHashMap<>();
        Set<Long> columns=new HashSet<>();
        surface.forEach(b->{result.put(b.position(),b);if(!b.targetState().isAir()) columns.add(column(b.position()));});
        var elevation=new JunctionSurfaceElevation(node,roads);
        List<Core> cores=new ArrayList<>();
        for(var road:roads) {
            var geometry=road.referenceGeometry();
            double reach=Math.min(48,geometry.length());
            boolean atStart=road.startNodeId().equals(node.id());
            List<RoadPoint> points=new ArrayList<>();
            int count=Math.max(1,(int)Math.ceil(reach/0.5));
            for(int i=0;i<=count;i++) points.add(geometry.sample(atStart?reach*i/count:geometry.length()-reach*i/count).position());
            double half=road.lanes().stream().filter(l->l.component().type()==LaneType.VEHICLE)
                    .mapToDouble(l->Math.abs(l.component().lateralOffset())+l.component().width()/2).max().orElse(0);
            cores.add(new Core(road.id(),points,half));
        }
        for(var road:roads) {
            var resolved=RoadSurfaceResolver.resolve(road.presetSnapshot().surfaceColumns());
            if(!(resolved instanceof RoadSurfaceResolver.Result.Success success)) continue;
            var roles=road.presetSnapshot().surfaceColumnRoles();
            var surfaceColumns=road.presetSnapshot().surfaceColumns();
            double core=cores.stream().filter(c->c.id.equals(road.id())).findFirst().orElseThrow().halfWidth;
            for(int i=0;i<roles.size();i++) {
                double lateral=(roles.size()-1)/2.0-i;
                if(roles.get(i)!=RoadSurfaceRole.MARKING || surfaceColumns.get(i).pattern()!=RoadMarkingPattern.CONTINUOUS
                        || Math.abs(lateral)<Math.max(0.5,core-0.75)) continue;
                // Rasterize the original curve; an approximate local slice can
                // shift the stripe by a cell and open another gap at the seam.
                var line=new OffsetGeometry(road.referenceGeometry(),lateral,false);
                var resolvedSurface=success.surface();
                var oneColumn=new ResolvedRoadSurface(List.of(resolvedSurface.columnStates().get(i)),
                        List.of(resolvedSurface.underlayStates().get(i)),List.of(resolvedSurface.columnPatterns().get(i)),
                        List.of(resolvedSurface.columnStateSets().get(i)),List.of(resolvedSurface.underlayStateSets().get(i)));
                var planned=new RoadPathPlanner().plan(new RoadPathPlanRequest(dimension,line,oneColumn),
                        maximumBlocks,maximumWork,ConstructionLimits.UNRESTRICTED_MAXIMUM_GRADE);
                if(!(planned instanceof BlockPlanResult.Success marking)) continue;
                for(var block:marking.plan().blocks()) {
                    var pos=block.position();
                    if(!columns.contains(column(pos))) continue;
                    double y=elevation.height(pos.getX()+0.5,pos.getZ()+0.5);
                    if(buried(pos,y,road.id(),cores)) continue;
                    var placement=oneColumn.targetPlacement(0,0,pos.getX(),pos.getZ(),y);
                    result.put(placement.position(),new PlannedBlock(placement.position(),placement.state()));
                }
            }
        }
        if(result.size()>maximumBlocks) throw new IllegalArgumentException("lane marking block limit exceeded");
        return List.copyOf(result.values());
    }

    private static boolean buried(BlockPos position,double height,RoadSegmentId owner,List<Core> cores) {
        double x=position.getX()+0.5,z=position.getZ()+0.5;
        for(var core:cores) {
            if(core.id.equals(owner) || core.halfWidth<0.5) continue;
            double nearest=Double.POSITIVE_INFINITY,y=0;
            for(int i=1;i<core.points.size();i++) {
                var a=core.points.get(i-1);var b=core.points.get(i);
                double dx=b.x()-a.x(),dz=b.z()-a.z(),denominator=dx*dx+dz*dz;
                if(denominator<1e-10) continue;
                double t=Math.max(0,Math.min(1,((x-a.x())*dx+(z-a.z())*dz)/denominator));
                double distance=Math.hypot(x-a.x()-t*dx,z-a.z()-t*dz);
                if(distance<nearest) {nearest=distance;y=a.y()+(b.y()-a.y())*t;}
            }
            if(nearest<core.halfWidth-0.25 && Math.abs(height-y)<0.75) return true;
        }
        return false;
    }

    private static long column(BlockPos p) {return ((long)p.getX()<<32)^(p.getZ()&0xffffffffL);}
}
