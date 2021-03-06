package zombie.commands.serverCommands;

import zombie.commands.CommandBase;
import zombie.commands.CommandHelp;
import zombie.commands.CommandName;
import zombie.commands.RequiredRight;
import zombie.core.logger.LoggerManager;
import zombie.core.raknet.UdpConnection;
import zombie.iso.weather.ClimateManager;

@CommandName(
   name = "stopweather"
)
@CommandHelp(
   helpText = "UI_ServerOptionDesc_StopWeather"
)
@RequiredRight(
   requiredRights = 60
)
public class StopWeatherCommand extends CommandBase {
   public StopWeatherCommand(String var1, String var2, String var3, UdpConnection var4) {
      super(var1, var2, var3, var4);
   }

   protected String Command() {
      ClimateManager.getInstance().transmitServerStopRain();
      ClimateManager.getInstance().transmitServerStopWeather();
      LoggerManager.getLogger("admin").write(this.getExecutorUsername() + " stopped weather");
      return "Weather stopped";
   }
}
