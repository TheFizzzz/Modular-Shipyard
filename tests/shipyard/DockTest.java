package shipyard;

import java.lang.reflect.Method;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.input.InputHandler;

/** Regression requests now travel through actual possession; no authority bypass in production. */
final class DockTest{
    static Object apply(Method method,Object... args) throws Exception{
        Player player=(Player)args[0];Building yard=Vars.world.build((int)args[1]);
        if(yard!=null && player!=null && !player.dead() && yard.canControlSelect(player.unit()))InputHandler.buildingControlSelect(player,yard);
        try{return method.invoke(null,args);}finally{
            if(yard!=null)yard.getClass().getMethod("release",Player.class).invoke(yard,player);
        }
    }
}
