package com.embabel.guide;

import com.embabel.agent.event.logging.personality.severance.LumonColorPalette;
import com.embabel.agent.shell.TerminalServices;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class GuideShell {

    private final TerminalServices terminalServices;
    private final ConversationManager conversationManager;

    public GuideShell(
            ConversationManager conversationManager,
            TerminalServices terminalServices) {
        this.conversationManager = conversationManager;
        this.terminalServices = terminalServices;
    }

    @ShellMethod("talk to docs")
    public String talk() {
        var guide = conversationManager.guide();
        return terminalServices.chat(
                guide,
                "Welcome to the Guide! How can I assist you today?",
                LumonColorPalette.INSTANCE);
    }
}
