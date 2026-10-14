import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.files.FileHandle;
import com.unciv.UncivGame;
import com.unciv.logic.GameInfo;
import com.unciv.logic.civilization.*;
import com.unciv.logic.city.City;
import com.unciv.logic.map.*;
import com.unciv.logic.map.tile.Tile;
import com.unciv.logic.map.mapunit.MapUnit;
import com.unciv.logic.map.mapunit.movement.MovementCost;
import com.unciv.logic.files.UncivFiles;
import com.unciv.models.metadata.GameSettings;
import com.unciv.models.ruleset.*;
import com.unciv.models.stats.Stat;
import com.unciv.json.UncivJsonKt;
import unciv.stellar.StellarRuntime;
import java.util.*;

/** 所有测试使用交付代理和实际引擎，独立内存地图，不读取或写入真实存档。 */
public final class TestStellarFleet {
    static int checks;static Ruleset rules;static String[] modNames;
    static void check(boolean ok,String title){if(!ok)throw new AssertionError(title);checks++;System.out.println("PASS "+title);}
    record Fixture(GameInfo game,Civilization china,Civilization germany,City city){}
    static Fixture fixture(){
        var game=new GameInfo();game.setRuleset(rules);game.setDifficulty("Prince");game.getGameParameters().setSpeed("Standard");
        for(String mod:modNames)game.getGameParameters().getMods().add(mod);
        game.setGlobalTransients();var map=new TileMap(14,rules,false);map.setGameInfo(game);game.setTileMap(map);
        for(var tile:map.getValues()){tile.baseTerrain="Grassland";tile.setTerrainTransients();}
        List<Civilization> civs=new ArrayList<>();
        for(String name:List.of("China","Germany")){
            var civ=new Civilization(rules.getNations().get(name));civ.setGameInfo(game);civ.setPlayerType(PlayerType.Human);game.getCivilizations().add(civ);civ.getCache().updateState();civ.setTransients();
            civ.getTech().getTechsResearched().addAll(rules.getTechnologies().keySet());civ.getTech().setTransients(civ);civ.addGold(100000);civs.add(civ);
        }
        game.setCurrentPlayer(civs.get(0).getCivID());game.setCurrentPlayerCiv(civs.get(0));UncivGame.Current.setGameInfo(game);
        var city=civs.get(0).addCity(new HexCoord(0,0),null);civs.get(1).addCity(new HexCoord(10,0),null);
        city.getPopulation().setPopulation(20);city.getCenterTile().setTileResource("Uranium",false);city.getCenterTile().setResourceAmount(200);
        var iron=civs.get(0).addCity(new HexCoord(-6,0),null);iron.getCenterTile().setTileResource("Iron",false);iron.getCenterTile().setResourceAmount(200);
        for(var civ:civs){for(var tile:map.getValues())tile.setExplored(civ,true,null);civ.getCache().updateState();civ.getCache().updateCivResources();}
        civs.get(0).getDiplomacyFunctions().makeCivilizationsMeet(civs.get(1),false);
        return new Fixture(game,civs.get(0),civs.get(1),city);
    }
    static MapUnit spawn(Fixture f,String name,int x,int y){
        MapUnit unit=rules.getUnits().get(name).newMapUnit(f.china,null);f.china.getUnits().addUnit(unit,false);unit.putInTile(f.game.getTileMap().get(x,y));unit.setCurrentMovement(unit.getMaxMovement(false));return unit;
    }
    static void specs()throws Exception{
        var f=fixture();
        for(String name:List.of(StellarRuntime.VICTORY,StellarRuntime.EXECUTOR,StellarRuntime.DEATH)){
            var base=rules.getUnits().get(name);check(base!=null&&base.getUniqueTo()==null,name+"所有文明可用");
            check(rules.getTechnologies().get(base.getRequiredTech()).era().equals("Information era"),name+"归于信息时代");
            check(Objects.equals(base.getStatBuyCost(f.city,Stat.Gold),name.equals(StellarRuntime.DEATH)?null:Integer.valueOf(name.equals(StellarRuntime.VICTORY)?1000:3000)),name+"准确金币价/死星不可金币购买");
            var reasons=base.getRejectionReasons(f.city.getCityConstructions()).iterator();boolean coastal=false;while(reasons.hasNext())coastal|=reasons.next().getType().name().equals("WaterUnitsInCoastalCities");check(!coastal,name+"内陆城市不受海军海岸生产限制");
        }
        check(!rules.getUnits().get(StellarRuntime.DEATH).canBePurchasedWithStat(f.city,Stat.Gold),"死星原生禁购检查生效");
        for(var entry:Map.of(StellarRuntime.VICTORY,new int[]{10,3},StellarRuntime.EXECUTOR,new int[]{20,6},StellarRuntime.DEATH,new int[]{40,15}).entrySet()){
            var requirements=rules.getUnits().get(entry.getKey()).getResourceRequirementsPerTurn(f.city.getState());
            check(requirements.get("Iron")==entry.getValue()[0]&&requirements.get("Uranium")==entry.getValue()[1],entry.getKey()+"原生资源占用数值正确");
        }
        var ship=spawn(f,StellarRuntime.VICTORY,1,0);
        ship.getPromotions().setXP(100);
        var promotions=new HashSet<String>();ship.getPromotions().getAvailablePromotions().iterator().forEachRemaining(p->promotions.add(p.getName()));
        check(promotions.stream().anyMatch(n->n.startsWith("Targeting")||n.startsWith("Bombardment")),"可选原生远程军舰晋升");
        check(ship.getRange()==3&&spawn(f,StellarRuntime.DEATH,2,0).getRange()==6,"实际单位攻击射程为3/6格");
        var economic=fixture();var construction=economic.city.getCityConstructions();
        for(String name:List.of(StellarRuntime.VICTORY,StellarRuntime.EXECUTOR)){
            var base=rules.getUnits().get(name);int before=economic.city.getStatReserve(Stat.Gold),price=name.equals(StellarRuntime.VICTORY)?1000:3000;
            boolean bought=construction.purchaseConstruction(base,-1,false,Stat.Gold,null);
            if(!bought||before-economic.city.getStatReserve(Stat.Gold)!=price){System.out.println("PURCHASE "+name+" success="+bought+" before="+before+" after="+economic.city.getStatReserve(Stat.Gold)+" resources="+economic.china.getCivResourcesByName());base.getRejectionReasons(construction).iterator().forEachRemaining(reason->System.out.println("REASON "+reason.getType()+" "+reason));}
            check(bought&&before-economic.city.getStatReserve(Stat.Gold)==price,name+"原生购买实际扣除准确金币");
            var units=new ArrayList<MapUnit>();economic.china.getUnits().getCivUnits().iterator().forEachRemaining(units::add);
            for(var unit:units)if(StellarRuntime.fleet(unit))unit.destroy(true);
        }
        var limited=fixture();var base=rules.getUnits().get(StellarRuntime.VICTORY);
        limited.china.addGold(999-limited.city.getStatReserve(Stat.Gold));
        check(!limited.city.getCityConstructions().isConstructionPurchaseAllowed(base,Stat.Gold,1000),"金币不足时原生购买按钮禁用");
        limited.china.addGold(100000);limited.city.getCenterTile().setResourceAmount(0);limited.china.getCache().updateCivResources();
        check(!limited.city.getCityConstructions().isConstructionPurchaseAllowed(base,Stat.Gold,1000),"铀不足时仍受原生资源限制");
        limited.city.getCenterTile().setResourceAmount(200);limited.china.getCache().updateCivResources();
        limited.china.getTech().getTechsResearched().remove("Advanced Ballistics");limited.china.getTech().setTransients(limited.china);
        check(!base.isBuildable(limited.city.getCityConstructions()),"缺少高级弹道学时不能提前解锁");
        limited.china.getTech().getTechsResearched().add("Advanced Ballistics");limited.china.getTech().setTransients(limited.china);
        check(base.isBuildable(limited.city.getCityConstructions()),"信息时代两项科技满足后可正常建造");
        var bulk=fixture();var queue=bulk.city.getCityConstructions().getConstructionQueue();queue.clear();queue.add(StellarRuntime.VICTORY);queue.add(StellarRuntime.DEATH);
        int before=bulk.city.getStatReserve(Stat.Gold);
        Object result=Class.forName("unciv.civic.BulkPurchase").getMethod("execute",City.class,List.class).invoke(null,bulk.city,List.copyOf(queue));
        check(((Number)result.getClass().getMethod("bought").invoke(result)).intValue()==1&&before-bulk.city.getStatReserve(Stat.Gold)==1000,"一键购买实际购入胜利级并跳过禁购死星");
    }
    static void transport(String shipName){
        var f=fixture();var map=f.game.getTileMap();var ship=spawn(f,shipName,1,0);
        var fighter=spawn(f,"Stealth Bomber",0,0);
        check(ship.canTransport(fighter),shipName+"可搭载原版禁止普通航母搭载的隐形轰炸机");
        StellarRuntime.load(ship,fighter);check(fighter.getTile()==ship.getTile()&&fighter.isTransported(),"实际装载隐形轰炸机");
        var land=spawn(f,"Infantry",2,0);land.setHealth(63);land.getPromotions().setXP(27);
        StellarRuntime.load(ship,land);check(StellarRuntime.aboard(land,ship)&&ship.getTile().getMilitaryUnit()==ship&&ship.getTile().getCivilianUnit()==null,"陆军在独立舱内且不覆盖母舰格");
        check(!land.hasMovement()&&land.getMaxMovement(false)==0,"舱内地面单位不能行动或恢复行动力");
        check(!com.unciv.ui.screens.worldscreen.unit.actions.UnitActions.INSTANCE.getUnitActions(land).iterator().hasNext(),"舱内地面单位没有建造/建城/自动化等原生行动");
        int airCap=StellarRuntime.capacity(ship,true),groundCap=StellarRuntime.capacity(ship,false);
        for(int i=1;i<airCap;i++){var plane=spawn(f,"Jet Fighter",0,0);StellarRuntime.load(ship,plane);}
        for(int i=1;i<groundCap;i++){var worker=spawn(f,"Worker",2,0);StellarRuntime.load(ship,worker);}
        var extraAir=spawn(f,"Jet Fighter",0,0);var extraGround=spawn(f,"Worker",2,0);
        check(!ship.canTransport(extraAir)&&ship.checkCarryCapacity(extraAir)==0,"空军舱满拒绝继续装载");
        check(!ship.canTransport(extraGround)&&ship.checkCarryCapacity(extraGround)==0,"陆军和平民共用地面舱且满舱拒绝");
        var foreign=rules.getUnits().get("Infantry").newMapUnit(f.germany,null);check(!ship.canTransport(foreign),"拒绝异国单位搭载");
        check(ship.canTransport(land),"满舱状态仍识别已在舰上的载员");
        fighter.setCurrentMovement(2f);
        for(String terrain:List.of("Coast","Ocean","Mountain","Desert","Grassland")){
            Tile tile=map.get(1,-1);tile.baseTerrain=terrain;tile.setTerrainTransients();
            check(ship.getMovement().canMoveTo(tile,false,false,true),shipName+"可进入"+terrain);
            check(MovementCost.INSTANCE.getMovementCostBetweenAdjacentTiles(ship,ship.getTile(),tile,false)==1f,"跨"+terrain+"每格消耗1移动力");
            ship.setCurrentMovement(20f);ship.getMovement().moveToTile(tile,false);
            check(ship.getTile()==tile&&StellarRuntime.passengers(ship).size()==airCap+groundCap&&StellarRuntime.passengers(ship).stream().allMatch(u->u.getTile()==tile&&u.isTransported()),"满载舰实际移动，全部载员随舰");
            check(fighter.getCurrentMovement()==2f,"母舰移动不额外耗尽舰载空军行动力");
            ship.setCurrentMovement(20f);ship.getMovement().moveToTile(map.get(1,0),false);
        }
        var json=UncivJsonKt.json();String serialized=json.toJson(f.game);var restored=json.fromJson(GameInfo.class,serialized);restored.setTransients();
        var restoredShip=restored.getTileMap().get(1,0).getMilitaryUnit();
        check(StellarRuntime.passengers(restoredShip).size()==airCap+groundCap,"真实JSON存档往返恢复全部载员");
        var restoredLand=StellarRuntime.passengers(restoredShip).stream().filter(u->u.getName().equals("Infantry")).findFirst().orElseThrow();
        check(restoredLand.getHealth()==63&&restoredLand.getPromotions().getXP()==27&&StellarRuntime.aboard(restoredLand,restoredShip),"存档恢复保留载员生命、经验和所属母舰");
        var clone=f.game.clone();clone.setTransients();check(StellarRuntime.passengers(clone.getTileMap().get(1,0).getMilitaryUnit()).size()==airCap+groundCap,"原生克隆同样保留载员关系");
        UncivGame.Current.setGameInfo(f.game);ship.setCurrentMovement(6f);
        check(StellarRuntime.canUnload(ship,land,map.get(1,-1)),"相邻合法陆地可卸载");StellarRuntime.unload(ship,land,map.get(1,-1));
        check(!land.isTransported()&&!StellarRuntime.cargo(land)&&land.getTile().getMilitaryUnit()==land&&land.getHealth()==63,"卸载不复制单位并保留生命");
        check(land.getCurrentMovement()==0&&ship.getCurrentMovement()==5f,"卸载消耗母舰1移动力，载员本回合不可行动");
        var payload=List.copyOf(StellarRuntime.passengers(ship));ship.takeDamage(1000);
        check(ship.isDestroyed()&&payload.stream().allMatch(MapUnit::isDestroyed),"母舰被击毁时所有剩余空军陆军平民同步摧毁");
        check(!land.isDestroyed(),"已卸载陆军不随母舰摧毁");
        var fresh=fixture();var doomed=spawn(fresh,shipName,1,0);var worker=spawn(fresh,"Worker",2,0);StellarRuntime.load(doomed,worker);doomed.disband();
        check(doomed.isDestroyed()&&worker.isDestroyed(),"主动删除母舰同样不自动保全载员");
        var blast=fixture();var target=spawn(blast,shipName,1,0);var victim=spawn(blast,"Worker",2,0);StellarRuntime.load(target,victim);
        var weapon=rules.getUnits().get("Nuclear Missile").newMapUnit(blast.germany,null);blast.germany.getUnits().addUnit(weapon,false);weapon.putInTile(blast.game.getTileMap().get(10,0));
        com.unciv.logic.battle.Nuke.INSTANCE.NUKE(new com.unciv.logic.battle.MapUnitCombatant(weapon),target.getTile());
        check(target.isDestroyed()&&victim.isDestroyed(),"原生核爆摧毁母舰时地面载员一并摧毁");
    }
    static void run(String[] args)throws Exception{
        Gdx.files=new HeadlessFiles();UncivGame.Current=new UncivGame();UncivGame.Current.setSettings(new GameSettings());UncivGame.Current.setFiles(new UncivFiles(Gdx.files,"work/stellar-fleet/test-runtime"));
        RulesetCache.INSTANCE.loadRulesets(false,true);List<Ruleset> mods=new ArrayList<>();List<String> names=new ArrayList<>();
        for(String path:args){var folder=new FileHandle(path);var mod=new Ruleset();mod.setName(folder.name());mod.setFolderLocation(folder);mod.load(folder.child("jsons"));RulesetCache.INSTANCE.put(mod.getName(),mod);mods.add(mod);names.add(mod.getName());}
        modNames=names.toArray(String[]::new);rules=RulesetCache.INSTANCE.getComplexRuleset(RulesetCache.INSTANCE.get("Civ V - Gods & Kings"),mods);for(var base:rules.getUnits().values())base.setRuleset(rules);
        specs();for(String ship:List.of(StellarRuntime.VICTORY,StellarRuntime.EXECUTOR,StellarRuntime.DEATH))transport(ship);
        System.out.println("RESULT {\"passed\":true,\"checks\":"+checks+",\"mods\":\""+String.join(",",modNames)+"\"}");
    }
    public static void main(String[] args){try{run(args);System.exit(0);}catch(Throwable error){error.printStackTrace();System.exit(1);}}
}
