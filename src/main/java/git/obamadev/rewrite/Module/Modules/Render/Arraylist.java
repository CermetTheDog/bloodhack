package git.obamadev.rewrite.Module.Modules.Render;

import git.obamadev.rewrite.Module.Category;
import git.obamadev.rewrite.Module.Module;
import git.obamadev.rewrite.ObamaMod;
import git.obamadev.rewrite.Utils.Render.ColourUtils;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;

public class Arraylist extends Module {

    public Arraylist(){
        super("Arraylist", Category.RENDER);
    }
    public int GenRainbow(){
        int drgb;
        int color;
        int argb;
        float[] hue = new float[]{(float) (System.currentTimeMillis() % 11520L) / 11520.0f};
        int rgb = Color.HSBtoRGB(hue[0], 1.0f, 1.0f);
        int red = rgb >> 16 & 255;
        int green = rgb >> 8 & 255;
        int blue = rgb & 255;
        color = argb = ColourUtils.toRGBA(red, green, blue, 255);
        return color;
    }
    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent event) {
        if(mc.player != null){
        if(this.isToggled()) {
            if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
                float currY = mc.fontRenderer.FONT_HEIGHT + 5;
                for (Module m : ObamaMod.moduleManager.getModules()) {
                    if (m.isToggled()) {
                        mc.fontRenderer.drawStringWithShadow(m.getName(), 5, currY + 1, GenRainbow());
                        currY += mc.fontRenderer.FONT_HEIGHT;
                    }
                }
            }
            }
        }
    }
}
