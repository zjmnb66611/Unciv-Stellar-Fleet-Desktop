import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** 对生成工具已完成的透明素材做发布尺寸导出，不重画或更改轮廓。 */
public final class BuildFleetAssets {
    public static void main(String[] args)throws Exception{
        Path pack=Path.of(args[0]),mod=pack.resolve("mods/Stellar Fleet");
        String[] names={"SF Victory Star Destroyer","SF Executor Star Dreadnought","SF Death Star"};
        String[] titles={"胜利级歼星舰 · 3格射程","执行者歼星舰 · 3格射程","死星 · 6格射程"};
        BufferedImage preview=new BufferedImage(1050,430,BufferedImage.TYPE_INT_RGB);Graphics2D board=preview.createGraphics();board.setColor(new Color(245,244,236));board.fillRect(0,0,1050,430);board.setColor(Color.BLACK);board.setFont(new Font("Microsoft YaHei",Font.PLAIN,21));
        for(int i=0;i<3;i++){
            Path source=Path.of(args[i+1]);BufferedImage original=ImageIO.read(source.toFile());
            Path editable=pack.resolve("素材源文件/"+names[i]+".png");Files.createDirectories(editable.getParent());Files.copy(source,editable,StandardCopyOption.REPLACE_EXISTING);
            BufferedImage icon=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);Graphics2D g=icon.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(original,0,0,128,128,null);g.dispose();
            String[] categories={"UnitIcons","TileSets/FantasyHex/Units","TileSets/HexaRealm/Units","TileSets/Minimal/Units"};
            for(String category:categories){Path path=mod.resolve("Images/"+category+"/"+names[i]+".png");Files.createDirectories(path.getParent());ImageIO.write(icon,"png",path.toFile());}
            board.drawImage(original,25+350*i,15,300,300,null);board.drawString(titles[i],18+350*i,340);
            for(int j=0;j<3;j++){int size=new int[]{32,48,64}[j];board.drawImage(icon,40+350*i+j*82,360,size,size,null);}
        }
        board.dispose();ImageIO.write(preview,"png",pack.resolve("星际舰队图标预览.png").toFile());
    }
}
