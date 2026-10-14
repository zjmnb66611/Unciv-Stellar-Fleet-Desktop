package unciv.stellar;

import com.unciv.logic.map.mapunit.MapUnit;
import com.unciv.logic.map.mapunit.movement.UnitMovement;
import com.unciv.logic.map.mapunit.movement.UnitMovement.CannotMoveToReason;
import com.unciv.logic.map.tile.Tile;
import com.unciv.logic.city.City;
import com.unciv.models.ruleset.unit.BaseUnit;
import com.unciv.models.stats.Stat;
import com.unciv.models.ruleset.RejectionReason;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import java.util.*;

/** 星舰专用逻辑。载员保留在原生单位/格列表中，以原生整数状态表存储母舰ID。 */
public final class StellarRuntime {
    public static final String VICTORY="SF Victory Star Destroyer", EXECUTOR="SF Executor Star Dreadnought", DEATH="SF Death Star";
    public static final String CARRIER_KEY="stellar-fleet.carrier-id.v1";
    private static final Map<String,int[]> SPECS=Map.of(VICTORY,new int[]{2,1,1000},EXECUTOR,new int[]{4,2,3000},DEATH,new int[]{5,4,-1});
    public static boolean fleet(MapUnit unit){return unit!=null && SPECS.containsKey(unit.getName()) && !unit.getCiv().getGameInfo().getGameParameters().isOnlineMultiplayer();}
    public static boolean cargo(MapUnit unit){return unit!=null && unit.getAbilityToTimesUsed().containsKey(CARRIER_KEY);}
    public static boolean groundCargo(MapUnit unit){return cargo(unit)&&!unit.getBaseUnit().isAirUnit();}
    public static boolean aboard(MapUnit unit,MapUnit carrier){return cargo(unit)&&Objects.equals(unit.getAbilityToTimesUsed().get(CARRIER_KEY),carrier.getId())&&unit.getOwner().equals(carrier.getOwner());}
    public static int capacity(MapUnit carrier,boolean air){return SPECS.get(carrier.getName())[air?0:1];}
    public static boolean eligible(MapUnit carrier,MapUnit unit){return fleet(carrier)&&unit!=carrier&&!SPECS.containsKey(unit.getName())&&(unit.getBaseUnit().isAirUnit()||unit.getBaseUnit().isLandUnit());}
    public static List<MapUnit> passengers(MapUnit carrier){
        if(!carrier.hasTile())return List.of();
        return carrier.getTile().getAirUnits().stream().filter(u->u!=carrier&&u.isTransported()&&u.getOwner().equals(carrier.getOwner())&&(aboard(u,carrier)||(!cargo(u)&&u.getBaseUnit().isAirUnit()))).toList();
    }
    public static int used(MapUnit carrier,boolean air){return (int)passengers(carrier).stream().filter(u->u.getBaseUnit().isAirUnit()==air).count();}
    public static boolean transportType(boolean original,MapUnit carrier,MapUnit unit){
        if(!fleet(carrier))return original;
        return eligible(carrier,unit);
    }
    public static int carryCapacity(int original,MapUnit carrier,MapUnit unit){
        if(!fleet(carrier))return original;
        return eligible(carrier,unit)?Math.max(0,capacity(carrier,unit.getBaseUnit().isAirUnit())-used(carrier,unit.getBaseUnit().isAirUnit())):0;
    }
    public static boolean canTransport(boolean original,MapUnit carrier,MapUnit unit){
        if(!fleet(carrier))return original;
        return eligible(carrier,unit)&&unit.getOwner().equals(carrier.getOwner())&&(aboard(unit,carrier)||carryCapacity(0,carrier,unit)>0);
    }
    public static Integer buyCost(Integer original,BaseUnit unit,City city,Stat stat){
        int[] spec=SPECS.get(unit.getName());
        return spec==null||stat!=Stat.Gold?original:spec[2]<0?null:Integer.valueOf(spec[2]);
    }
    public static boolean customConstruction(BaseUnit base,City city){return city!=null&&SPECS.containsKey(base.getName())&&!city.getCiv().getGameInfo().getGameParameters().isOnlineMultiplayer();}
    public static MapUnit constructAtCity(BaseUnit base,City city){
        var unit=base.newMapUnit(city.getCiv(),null);var tile=city.getCenterTile();
        if(!unit.getMovement().canMoveTo(tile,false,false,true))return null;
        city.getCiv().getUnits().addUnit(unit,true);unit.putInTile(tile);unit.setCurrentMovement(unit.getMaxMovement(false));
        return unit;
    }
    public static Sequence<RejectionReason> buildReasons(Sequence<RejectionReason> original,BaseUnit unit){
        if(!SPECS.containsKey(unit.getName()))return original;
        return SequencesKt.filter(original,reason->!reason.getType().name().equals("WaterUnitsInCoastalCities"));
    }
    public static CannotMoveToReason passReason(CannotMoveToReason original,UnitMovement movement,Tile tile){
        MapUnit unit=movement.getUnit();
        if(!fleet(unit)||(original!=CannotMoveToReason.TerrainImpassable&&original!=CannotMoveToReason.BoatCannotGoOnLand))return original;
        if(!tile.canCivPassThrough(unit.getCiv()))return CannotMoveToReason.CannotEnterForeignLand;
        MapUnit first=tile.getFirstUnit();
        if(first!=null&&!first.getOwner().equals(unit.getOwner())&&unit.getCiv().isAtWarWith(first.getCiv()))return CannotMoveToReason.TileIsNotEmpty;
        return null;
    }
    public static float moveCost(float original,MapUnit unit){return fleet(unit)?1f:original;}
    public static int maxMovement(int original,MapUnit unit){return groundCargo(unit)?0:original;}
    public static boolean hasMovement(boolean original,MapUnit unit){return groundCargo(unit)?false:original;}
    public static Sequence<?> actions(Sequence<?> original,MapUnit unit){return groundCargo(unit)?SequencesKt.emptySequence():original;}
    public static boolean preventMove(UnitMovement movement,Tile destination){
        MapUnit unit=movement.getUnit();
        if(groundCargo(unit))return true;
        if(cargo(unit)&&unit.getBaseUnit().isAirUnit()&&unit.getTile()!=destination)unit.getAbilityToTimesUsed().remove(CARRIER_KEY);
        return false;
    }
    public static boolean placeCargo(MapUnit unit,Tile tile){
        if(!cargo(unit))return false;
        MapUnit carrier=tile.getMilitaryUnit();
        if(carrier==null||!fleet(carrier)||!aboard(unit,carrier))throw new IllegalStateException("载员母舰缺失，拒绝错误落位");
        if(!tile.getAirUnits().contains(unit))tile.getAirUnits().add(unit);
        unit.setCurrentTile(tile);unit.setTransported(true);if(groundCargo(unit))unit.setCurrentMovement(0f);unit.getCache().updateUniques();
        return true;
    }
    public static boolean moveCargoThrough(MapUnit unit,Tile tile){
        if(!groundCargo(unit))return false;
        unit.setCurrentTile(tile);return true;
    }
    public static void afterPut(MapUnit unit,Tile tile){
        if(unit.getBaseUnit().isAirUnit()&&unit.isTransported()&&fleet(tile.getMilitaryUnit()))unit.getAbilityToTimesUsed().put(CARRIER_KEY,tile.getMilitaryUnit().getId());
    }
    public static List<MapUnit> nearbyCarriers(MapUnit unit){
        List<MapUnit> ships=new ArrayList<>();
        for(var it=unit.getTile().getTilesInDistance(1).iterator();it.hasNext();){var ship=it.next().getMilitaryUnit();if(ship!=null&&fleet(ship)&&ship.getOwner().equals(unit.getOwner()))ships.add(ship);}
        return ships;
    }
    public static void load(MapUnit carrier,MapUnit unit){
        if(!fleet(carrier)||carrier.isDestroyed()||unit.isDestroyed()||cargo(unit)||!unit.hasMovement()||unit.getTile().aerialDistanceTo(carrier.getTile())>1||!canTransport(false,carrier,unit))throw new IllegalStateException("无法登舰：请检查距离、所属国家、行动力和对应舱位。");
        unit.stopEscorting();unit.removeFromTile();unit.setAction(null);unit.setAutomated(false);
        unit.getAbilityToTimesUsed().put(CARRIER_KEY,carrier.getId());placeCargo(unit,carrier.getTile());unit.setCurrentMovement(0f);
        unit.setDue(false);unit.getCiv().getCache().updateViewableTiles(null);unit.getCiv().updateStatsForNextTurn();
    }
    public static boolean canUnload(MapUnit carrier,MapUnit unit,Tile tile){
        if(!fleet(carrier)||!aboard(unit,carrier)||carrier.isDestroyed()||unit.isDestroyed()||!carrier.hasMovement()||carrier.getTile().aerialDistanceTo(tile)>1)return false;
        if(!unit.getBaseUnit().isAirUnit()&&(!tile.isLand()||tile.isImpassible()))return false;
        Integer key=unit.getAbilityToTimesUsed().remove(CARRIER_KEY);
        boolean transported=unit.isTransported();unit.setTransported(false);
        try{return unit.getMovement().canMoveTo(tile,false,false,true);}
        finally{unit.getAbilityToTimesUsed().put(CARRIER_KEY,key);unit.setTransported(transported);}
    }
    public static void unload(MapUnit carrier,MapUnit unit,Tile tile){
        if(!canUnload(carrier,unit,tile))throw new IllegalStateException("不能在此处卸载：需要母舰行动力和相邻合法空位。");
        unit.removeFromTile();unit.getAbilityToTimesUsed().remove(CARRIER_KEY);unit.setTransported(false);
        unit.putInTile(tile);unit.setCurrentMovement(0f);unit.setDue(true);carrier.useMovementPoints(1f);
        unit.getCiv().getCache().updateViewableTiles(null);unit.getCiv().updateStatsForNextTurn();
    }
    public static void beforeDestroy(MapUnit carrier){
        if(!fleet(carrier))return;
        // 快照避免原生destroy遍历格列表时并发修改；包括空军、陆军和平民。
        for(MapUnit unit:List.copyOf(passengers(carrier)))if(!unit.isDestroyed())unit.destroy(true);
    }
    public static boolean disband(MapUnit carrier){if(fleet(carrier))beforeDestroy(carrier);return false;}
}
