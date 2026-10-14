import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.files.FileHandle;
import com.unciv.UncivGame;
import com.unciv.logic.files.UncivFiles;
import com.unciv.models.metadata.GameSettings;
import com.unciv.models.ruleset.*;
import java.util.*;

public final class ValidateDelta {
    static Ruleset load(String path){var file=new FileHandle(path);var mod=new Ruleset();mod.setName(file.name());mod.setFolderLocation(file);mod.load(file.child("jsons"));return mod;}
    public static void main(String[] args){try{run(args);System.exit(0);}catch(Throwable error){error.printStackTrace();System.exit(1);}}
    static void run(String[] args){
        Gdx.files=new HeadlessFiles();UncivGame.Current=new UncivGame();UncivGame.Current.setSettings(new GameSettings());UncivGame.Current.setFiles(new UncivFiles(Gdx.files,"work/stellar-fleet/validation-runtime"));RulesetCache.INSTANCE.loadRulesets(false,true);
        List<Ruleset> mods=new ArrayList<>();for(int i=1;i<args.length;i++)mods.add(load(args[i]));
        var before=RulesetCache.INSTANCE.getComplexRuleset(RulesetCache.INSTANCE.get("Civ V - Gods & Kings"),mods);Set<String> inherited=new HashSet<>();for(var error:before.getErrorList(false))inherited.add(error.getText());
        var own=load(args[0]);for(String name:own.getUnits().keySet())if(before.getUnits().containsKey(name))throw new AssertionError("覆盖了既有单位："+name);
        if(!own.getTechnologies().isEmpty()||!own.getEras().isEmpty()||!own.getUnitPromotions().isEmpty())throw new AssertionError("不应修改科技、时代和晋升");
        mods.add(own);var combined=RulesetCache.INSTANCE.getComplexRuleset(RulesetCache.INSTANCE.get("Civ V - Gods & Kings"),mods);combined.setFolderLocation(own.getFolderLocation());int added=0,old=0;
        for(var error:combined.getErrorList(false)){
            if(error.getErrorSeverityToReport().toString().equals("OK"))continue;
            if(inherited.contains(error.getText()))old++;else{added++;System.out.println("NEW "+error.getErrorSeverityToReport()+" "+error.getText());}
        }
        System.out.println("RESULT newProblems="+added+" inheritedProblems="+old+" unitsAdded="+own.getUnits().size()+" noUnitOrTechOverrides=true");
        if(added>0)throw new AssertionError("引入新增规则问题");
    }
}
