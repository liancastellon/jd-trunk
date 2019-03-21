package org.jdownloader.startup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.appwork.app.launcher.parameterparser.CommandSwitch;
import org.appwork.app.launcher.parameterparser.ParameterParser;
import org.appwork.utils.Application;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.singleapp.InstanceMessageListener;
import org.jdownloader.logging.LogController;
import org.jdownloader.startup.commands.AbstractStartupCommand;
import org.jdownloader.startup.commands.AddContainerCommand;
import org.jdownloader.startup.commands.AddExtractionPasswordsCommand;
import org.jdownloader.startup.commands.AddLinkCommand;
import org.jdownloader.startup.commands.BRDebugCommand;
import org.jdownloader.startup.commands.DisableSysErr;
import org.jdownloader.startup.commands.FileCommand;
import org.jdownloader.startup.commands.GuiFocusCommand;
import org.jdownloader.startup.commands.GuiMinimizeCommand;
import org.jdownloader.startup.commands.HelpCommand;
import org.jdownloader.startup.commands.JACShowCommand;
import org.jdownloader.startup.commands.MyJDownloaderCommand;
import org.jdownloader.startup.commands.ReScanPluginsCommand;
import org.jdownloader.startup.commands.ReconnectCommand;
import org.jdownloader.startup.commands.SetConfigCommand;
import org.jdownloader.startup.commands.ThreadDump;
import org.jdownloader.updatev2.RestartController;

import jd.SecondLevelLaunch;

public class ParameterHandler implements InstanceMessageListener {
    private HashMap<String, StartupCommand> commandMap;
    private LogSource                       logger;
    private ArrayList<StartupCommand>       commands;
    private ParameterParser                 startupParameters;

    public ParameterHandler() {
        logger = LogController.getInstance().getLogger("StartupParameterHandler");
        commandMap = new HashMap<String, StartupCommand>();
        commands = new ArrayList<StartupCommand>();
        addCommand(new AddContainerCommand());
        addCommand(new AddExtractionPasswordsCommand());
        addCommand(new AddLinkCommand());
        //
        addCommand(new GuiFocusCommand());
        addCommand(new GuiMinimizeCommand());
        addCommand(new HelpCommand(this));
        addCommand(new JACShowCommand());
        addCommand(new ReconnectCommand());
        addCommand(new FileCommand());
        addCommand(new BRDebugCommand());
        addCommand(new ReScanPluginsCommand());
        addCommand(new MyJDownloaderCommand());
        addCommand(new DisableSysErr());
        addCommand(new SetConfigCommand());
        addCommand(new ThreadDump());
        addCommand(new AbstractStartupCommand("n") {
            @Override
            public void run(String command, String... parameters) {
            }

            @Override
            public String getDescription() {
                return "Force a new Instance.";
            }
        });
        addCommand(new AbstractStartupCommand("console") {
            @Override
            public void run(String command, String... parameters) {
            }

            @Override
            public String getDescription() {
                return "Write all Logs to STDOUt or STDERR";
            }
        });
    }

    private void addCommand(StartupCommand helpCommand) {
        for (String s : helpCommand.getCommandSwitches()) {
            if (commandMap.containsKey(s)) {
                throw new IllegalStateException("Command " + s + " already is used");
            }
            commandMap.put(s, helpCommand);
        }
        commands.add(helpCommand);
    }

    @Override
    public void parseMessage(String[] args) {
        logger.info("Sent: " + Arrays.toString(args));
        ParameterParser pp = new ParameterParser(args);
        pp.parse(null);
        execute(pp, false);
    }

    protected void execute(ParameterParser pp, boolean startup) {
        for (CommandSwitch cmd : pp.getList()) {
            StartupCommand command = commandMap.get(cmd.getSwitchCommand());
            if (command != null && (command.isRunningInstanceEnabled() || startup)) {
                command.run(cmd.getSwitchCommand(), cmd.getParameters());
            } else {
                logger.warning("Invalid Command: " + cmd.getSwitchCommand() + " - " + Arrays.toString(cmd.getParameters()));
            }
        }
    }

    public List<StartupCommand> getCommands() {
        return commands;
    }

    public void onStartup(String[] args) {
        logger.info("Startup: " + Arrays.toString(args));
        startupParameters = RestartController.getInstance().getParameterParser(args);
        startupParameters.parse(null);
        execute(startupParameters, true);
        if (!startupParameters.hasCommandSwitch("console") && Application.isJared(SecondLevelLaunch.class)) {
            logger.info("Remove ConsoleHandler");
            LogController.getInstance().removeConsoleHandler();
        }
    }
}
