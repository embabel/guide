package com.embabel.guide;

import com.embabel.agent.event.logging.personality.severance.LumonColorPalette;
import com.embabel.agent.shell.TerminalServices;
import com.embabel.chat.Chatbot;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.nio.file.Path;

@ShellComponent
public record GuideShell(
        TerminalServices terminalServices,
        Chatbot chatbot,
        GuideData guideData) {

    @ShellMethod("talk to docs")
    public String talk() {
        var guide = chatbot.createSession(null,
                terminalServices.outputChannel(),
                null);
        return terminalServices.chat(
                guide,
                "Welcome to the Guide! How can I assist you today?",
                LumonColorPalette.INSTANCE);
    }

    @ShellMethod("load docs")
    public String loadDocs() {
        var dir = Path.of(System.getProperty("user.dir"), "data", "docs").toString();
        var directoryParsingResult = guideData.ingestDirectory(dir);
        return "Loaded docs: " + directoryParsingResult;
    }

    @ShellMethod("provision database")
    public String provision() {
        guideData.provisionDatabase();
        return "Database provisioned";
    }

    @ShellMethod("show chunks")
    public String chunks() {
        return guideData.count() + " chunks in the database";
    }
}
