package unciv.stellar;

import java.lang.instrument.*;
import java.security.*;
import java.util.*;
import org.objectweb.asm.*;

/** 支持重转换的后置代理，保留已加载的东风/核组织/购买补丁，不增删原生字段。 */
public final class StellarAgent implements Opcodes {
    static final String UNIT="com/unciv/logic/map/mapunit/MapUnit",MOVE=UNIT.substring(0,UNIT.lastIndexOf('/')+1)+"movement/UnitMovement";
    static final String COST="com/unciv/logic/map/mapunit/movement/MovementCost",BASE="com/unciv/models/ruleset/unit/BaseUnit";
    static final String ACTIONS="com/unciv/ui/screens/worldscreen/unit/actions/UnitActions",UI=ACTIONS+"Table",RT="unciv/stellar/StellarRuntime";
    static final String MANAGER="com/unciv/logic/civilization/managers/UnitManager";
    static final String U="L"+UNIT+";",T="Lcom/unciv/logic/map/tile/Tile;",SEQ="Lkotlin/sequences/Sequence;",REASON="L"+MOVE+"$CannotMoveToReason;";
    static final Map<String,Integer> EXPECTED=Map.of(UNIT,9,MOVE,2,COST,2,BASE,2,ACTIONS,2,UI,1,MANAGER,1);
    static final Set<String> ACTIVE=new HashSet<>();
    public static void premain(String args,Instrumentation instrumentation)throws Exception{
        if(System.getProperty("unciv.stellar.active")!=null)throw new IllegalStateException("星际舰队扩展重复加载");
        if(!instrumentation.isRetransformClassesSupported())throw new IllegalStateException("运行环境不支持兼容挂接");
        Properties hashes=new Properties();try(var in=StellarAgent.class.getResourceAsStream("/stellar-targets.properties")){hashes.load(in);}
        for(String name:EXPECTED.keySet()){
            try(var in=ClassLoader.getSystemResourceAsStream(name+".class")){
                if(in==null||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(hashes.getProperty(name)))throw new IllegalStateException("原生类版本不匹配："+name);
            }
        }
        instrumentation.addTransformer(new ClassFileTransformer(){
            public byte[] transform(ClassLoader loader,String name,Class<?> cls,ProtectionDomain domain,byte[] bytes){
                if(!EXPECTED.containsKey(name))return null;
                try{return patch(name,bytes);}catch(Throwable error){System.err.println("[星际舰队] 挂接失败："+name+" "+error);error.printStackTrace();Runtime.getRuntime().halt(47);return null;}
            }
        },true);
        List<Class<?>> loaded=new ArrayList<>();
        for(Class<?> cls:instrumentation.getAllLoadedClasses())if(EXPECTED.containsKey(cls.getName().replace('.','/')))loaded.add(cls);
        if(!loaded.isEmpty())instrumentation.retransformClasses(loaded.toArray(Class<?>[]::new));
        for(String name:EXPECTED.keySet())Class.forName(name.replace('/','.'),false,ClassLoader.getSystemClassLoader());
        if(!ACTIVE.containsAll(EXPECTED.keySet()))throw new IllegalStateException("星际舰队挂接不完整："+ACTIVE);
        System.setProperty("unciv.stellar.active","1.0.0");
        System.out.println("[星际舰队] 三种跨地形星舰、独立双舱、固定金币价和单位行动栏已加载。");
    }
    static byte[] patch(String cls,byte[] bytes){
        var reader=new ClassReader(bytes);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] count={0};
        reader.accept(new ClassVisitor(ASM9,writer){
            public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){
                var original=super.visitMethod(access,name,desc,sig,exceptions);String selected="";
                if(cls.equals(UNIT))selected=switch(name){case "isTransportTypeOf"->"transportType";case "checkCarryCapacity"->"carryCapacity";case "canTransport"->"canTransport";case "putInTile"->"place";case "moveThroughTile"->"through";case "getMaxMovement"->"maxMovement";case "hasMovement"->"hasMovement";case "destroy"->"destroy";case "disband"->"disband";default->"";};
                if(cls.equals(MOVE)&&name.equals("cannotPassThroughReason")&&!name.contains("$"))selected="pass";
                if(cls.equals(MOVE)&&name.equals("moveToTile"))selected="move";
                if(cls.equals(COST)&&(name.equals("getMovementCostBetweenAdjacentTiles")||name.equals("getMovementCostBetweenAdjacentTilesEscort")))selected="cost";
                if(cls.equals(BASE)&&name.equals("getStatBuyCost"))selected="price";
                if(cls.equals(BASE)&&name.equals("getRejectionReasons")&&desc.contains("Civilization;"))selected="reasons";
                if(cls.equals(ACTIONS)&&name.equals("getUnitActions"))selected="actions";
                if(cls.equals(UI)&&name.equals("update")&&desc.equals("("+U+")V"))selected="ui";
                if(cls.equals(MANAGER)&&name.equals("addUnit")&&desc.startsWith("(L"+BASE+";Lcom/unciv/logic/city/City;)"))selected="construct";
                if(selected.isEmpty())return original;count[0]++;final String mode=selected;
                return new MethodVisitor(ASM9,original){
                    void invoke(String method,String descriptor){super.visitMethodInsn(INVOKESTATIC,RT,method,descriptor,false);}
                    void early(String method,String descriptor,boolean tile){
                        super.visitVarInsn(ALOAD,0);if(tile)super.visitVarInsn(ALOAD,1);invoke(method,descriptor);
                        Label proceed=new Label();super.visitJumpInsn(IFEQ,proceed);super.visitInsn(RETURN);super.visitLabel(proceed);super.visitFrame(F_SAME,0,null,0,null);
                    }
                    public void visitCode(){
                        super.visitCode();
                        switch(mode){
                            case "place"->early("placeCargo","("+U+T+")Z",true);
                            case "through"->early("moveCargoThrough","("+U+T+")Z",true);
                            case "move"->early("preventMove","(L"+MOVE+";"+T+")Z",true);
                            case "disband"->early("disband","("+U+")Z",false);
                            case "destroy"->{super.visitVarInsn(ALOAD,0);invoke("beforeDestroy","("+U+")V");}
                            case "construct"->{
                                String signature="(L"+BASE+";Lcom/unciv/logic/city/City;)";
                                super.visitVarInsn(ALOAD,1);super.visitVarInsn(ALOAD,2);invoke("customConstruction",signature+"Z");
                                Label proceed=new Label();super.visitJumpInsn(IFEQ,proceed);super.visitVarInsn(ALOAD,1);super.visitVarInsn(ALOAD,2);invoke("constructAtCity",signature+U);super.visitInsn(ARETURN);super.visitLabel(proceed);super.visitFrame(F_SAME,0,null,0,null);
                            }
                        }
                    }
                    public void visitInsn(int opcode){
                        if(opcode==IRETURN){
                            if(Set.of("transportType","carryCapacity","canTransport").contains(mode)){
                                super.visitVarInsn(ALOAD,0);super.visitVarInsn(ALOAD,1);invoke(mode,"("+(mode.equals("carryCapacity")?"I":"Z")+U+U+")"+(mode.equals("carryCapacity")?"I":"Z"));
                            }else if(mode.equals("maxMovement")||mode.equals("hasMovement")){super.visitVarInsn(ALOAD,0);invoke(mode,"("+(mode.equals("maxMovement")?"I":"Z")+U+")"+(mode.equals("maxMovement")?"I":"Z"));}
                        }
                        if(opcode==FRETURN&&mode.equals("cost")){super.visitVarInsn(ALOAD,1);invoke("moveCost","(F"+U+")F");}
                        if(opcode==ARETURN)switch(mode){
                            case "pass"->{super.visitVarInsn(ALOAD,0);super.visitVarInsn(ALOAD,1);invoke("passReason","("+REASON+"L"+MOVE+";"+T+")"+REASON);}
                            case "price"->{super.visitVarInsn(ALOAD,0);super.visitVarInsn(ALOAD,1);super.visitVarInsn(ALOAD,2);invoke("buyCost","(Ljava/lang/Integer;L"+BASE+";Lcom/unciv/logic/city/City;Lcom/unciv/models/stats/Stat;)Ljava/lang/Integer;");}
                            case "reasons"->{super.visitVarInsn(ALOAD,0);invoke("buildReasons","("+SEQ+"L"+BASE+";)"+SEQ);}
                            case "actions"->{super.visitVarInsn(ALOAD,1);invoke("actions","("+SEQ+U+")"+SEQ);}
                        }
                        if(opcode==RETURN){
                            if(mode.equals("place")){super.visitVarInsn(ALOAD,0);super.visitVarInsn(ALOAD,1);invoke("afterPut","("+U+T+")V");}
                            if(mode.equals("ui")){super.visitVarInsn(ALOAD,0);super.visitVarInsn(ALOAD,1);super.visitMethodInsn(INVOKESTATIC,"unciv/stellar/StellarUI","attach","(L"+UI+";"+U+")V",false);}
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        },0);
        if(count[0]!=EXPECTED.get(cls))throw new IllegalStateException("方法数量不匹配："+cls+"="+count[0]);
        ACTIVE.add(cls);return writer.toByteArray();
    }
}
