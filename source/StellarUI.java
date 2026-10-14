package unciv.stellar;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.logic.map.mapunit.MapUnit;
import com.unciv.models.translations.TranslationsKt;
import com.unciv.ui.components.input.KeyboardBinding;
import com.unciv.ui.popups.Popup;
import com.unciv.ui.screens.basescreen.BaseScreen;
import com.unciv.ui.screens.worldscreen.WorldScreen;
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsTable;
import kotlin.Unit;
import java.util.*;

/** 使用原生单位行动栏和弹窗，不引入网页或外部服务。 */
public final class StellarUI {
    static String name(MapUnit unit){return TranslationsKt.tr(unit.getName(),true,false);}
    static boolean writable(WorldScreen screen,MapUnit unit){return screen.getCanChangeState()&&screen.getSelectedCiv()==unit.getCiv()&&unit.getCiv().getGameInfo().getCurrentPlayer().equals(unit.getOwner())&&!unit.isDestroyed();}
    public static void attach(UnitActionsTable table,MapUnit unit){
        if(unit==null||(!StellarRuntime.fleet(unit)&&!StellarRuntime.cargo(unit)&&StellarRuntime.nearbyCarriers(unit).isEmpty()))return;
        if(!writable(table.getWorldScreen(),unit))return;
        if(StellarRuntime.groundCargo(unit))table.clearChildren();
        if(table.findActor("stellar-fleet-hangar")!=null)return;
        TextButton button=new TextButton(StellarRuntime.fleet(unit)?"星际机库 / 运输舱":"登舰 / 卸载",BaseScreen.skin);button.setName("stellar-fleet-hangar");
        button.addListener(new ClickListener(){public void clicked(InputEvent event,float x,float y){show(table.getWorldScreen(),unit);}});
        table.row();table.add(button).growX().minWidth(190f).padTop(4f);table.pack();
    }
    static void label(Popup popup,String text){popup.addGoodSizedLabel(text,18,false,Color.WHITE).row();}
    static void button(Popup popup,String text,Runnable action){var button=new TextButton(text,BaseScreen.skin);button.addListener(new ClickListener(){public void clicked(InputEvent event,float x,float y){action.run();}});popup.add(button).growX().pad(4f).row();}
    static void perform(WorldScreen screen,MapUnit selected,Popup parent,Runnable operation){
        if(!writable(screen,selected))return;
        try{operation.run();parent.close();screen.setShouldUpdate(true);show(screen,selected);}
        catch(IllegalStateException error){var popup=new Popup(screen,Popup.Scrollability.All,0.85f);label(popup,error.getMessage());popup.addCloseButton("关闭",KeyboardBinding.Cancel,()->Unit.INSTANCE);popup.open(true);}
    }
    public static void show(WorldScreen screen,MapUnit selected){
        if(!writable(screen,selected))return;
        var popup=new Popup(screen,Popup.Scrollability.All,0.9f);
        label(popup,"星际舰队 · "+name(selected));
        label(popup,"空军与地面舱独立计数；登舰须相邻且有行动力。卸载耗母舰1行动点，载员当回合不能再行动。母舰摧毁时全部载员一并摧毁。");
        if(StellarRuntime.fleet(selected)){
            label(popup,"空军 "+StellarRuntime.used(selected,true)+"/"+StellarRuntime.capacity(selected,true)+" · 陆军/平民 "+StellarRuntime.used(selected,false)+"/"+StellarRuntime.capacity(selected,false));
            for(var unit:StellarRuntime.passengers(selected)){
                label(popup,"已搭载："+name(unit)+" #"+unit.getId());
                boolean hasExit=false;
                for(var it=selected.getTile().getTilesInDistance(1).iterator();it.hasNext();){var tile=it.next();if(StellarRuntime.canUnload(selected,unit,tile)){hasExit=true;button(popup,"卸载 "+name(unit)+" → "+tile.getPosition(),()->perform(screen,selected,popup,()->StellarRuntime.unload(selected,unit,tile)));}}
                if(!hasExit)label(popup,"当前无相邻合法卸载格；空军也可使用原生转场。");
            }
            for(var tileIt=selected.getTile().getTilesInDistance(1).iterator();tileIt.hasNext();){
                for(var unitIt=tileIt.next().getUnits().iterator();unitIt.hasNext();){var unit=unitIt.next();if(unit==selected||StellarRuntime.cargo(unit)||!unit.hasMovement()||!StellarRuntime.canTransport(false,selected,unit))continue;
                    button(popup,"搭载 "+name(unit)+" #"+unit.getId(),()->perform(screen,selected,popup,()->StellarRuntime.load(selected,unit)));}
            }
        }else if(StellarRuntime.cargo(selected)){
            var ship=selected.getTile().getMilitaryUnit();if(ship!=null&&StellarRuntime.aboard(selected,ship))button(popup,"打开母舰机库",()->{popup.close();show(screen,ship);});
        }else{
            for(var ship:StellarRuntime.nearbyCarriers(selected))if(StellarRuntime.canTransport(false,ship,selected))button(popup,"登上 "+name(ship),()->perform(screen,selected,popup,()->StellarRuntime.load(ship,selected)));
        }
        popup.addCloseButton("关闭",KeyboardBinding.Cancel,()->Unit.INSTANCE);popup.open(true);
    }
}
