package com.embabel.guide;

import com.embabel.agent.event.logging.personality.severance.LumonColorPalette;
import com.embabel.agent.rag.ingestion.ChunkRepository;
import com.embabel.agent.shell.TerminalServices;
import com.embabel.chat.Chatbot;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public record GuideShell(
        TerminalServices terminalServices,
        Chatbot chatbot,
        ChunkRepository chunkRepository) {

    @ShellMethod("talk to docs")
    public String talk() {
        var guide = chatbot.createSession(null);
        return terminalServices.chat(
                guide,
                "Welcome to the Guide! How can I assist you today?",
                LumonColorPalette.INSTANCE);
    }

    @ShellMethod("show chunks")
    public String chunks() {
        return chunkRepository.findAll().stream()
                .map(chunk -> chunk.getId() + ": " + chunk.getText())
                .reduce("", (a, b) -> a + "\n" + "*".repeat(80) + "\n" + b);
    }
}
