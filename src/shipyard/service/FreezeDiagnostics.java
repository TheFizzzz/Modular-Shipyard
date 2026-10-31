package shipyard.service;

import arc.Core;
import arc.Events;
import arc.util.Log;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import mindustry.Vars;
import mindustry.core.Version;
import mindustry.game.EventType.*;

/** Local evidence for rare hangs: no uploads, gameplay changes or file writes on healthy frames. */
public final class FreezeDiagnostics{
    private static final long timeout=20_000_000_000L;
    private static final DateTimeFormatter stamp=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private static volatile boolean running;
    private static volatile long heartbeat;
    private static volatile String stage="starting",context="",platform="";
    private static long nextContext;
    private static Thread main,worker;
    private static Path directory;
    private FreezeDiagnostics(){}

    public static void init(){
        if(Vars.headless)return;
        Events.on(ClientLoadEvent.class,event->start());
        Events.on(WorldLoadBeginEvent.class,event->mark("world load beginning"));
        Events.on(WorldLoadEvent.class,event->mark("world loaded"));
        Events.run(Trigger.update,FreezeDiagnostics::update);
        Events.run(Trigger.afterGameUpdate,()->stage="game update finished");
        Events.run(Trigger.preDraw,()->stage="world rendering");
        Events.run(Trigger.uiDrawEnd,()->stage="UI rendering finished; next frame pending");
        Events.on(DisposeEvent.class,event->{running=false;if(worker!=null)worker.interrupt();});
    }
    private static void start(){
        if(worker!=null)return;
        main=Thread.currentThread();
        directory=Core.settings.getDataDirectory().child("shipyard-diagnostics").file().toPath();
        StringBuilder metadata=new StringBuilder("Mindustry: ").append(Version.combined())
            .append("\nOS: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
            .append("\nJava: ").append(System.getProperty("java.version"))
            .append("\nGraphics: ").append(Core.graphics.getGLVersion().getDebugVersionString()).append("\nMods:");
        for(var mod:Vars.mods.list())if(mod.enabled())metadata.append("\n  ").append(mod.name).append(':').append(mod.meta.version);
        platform=metadata.toString();
        mark("client loaded");running=true;
        worker=new Thread(FreezeDiagnostics::watch,"Shipyard freeze recorder");worker.setDaemon(true);worker.start();
    }
    private static void mark(String checkpoint){stage=checkpoint;heartbeat=System.nanoTime();}
    private static void update(){
        mark("application update");
        long now=heartbeat;
        if(now>=nextContext && Vars.state!=null){
            nextContext=now+2_000_000_000L;
            context="State: "+Vars.state.getState()+"; map dimensions: "+Vars.world.width()+"x"+Vars.world.height()
                +"; network client: "+Vars.net.client()+"; network server: "+Vars.net.server()
                +"; units: "+mindustry.gen.Groups.unit.size()+"; bullets: "+mindustry.gen.Groups.bullet.size();
        }
    }
    private static void watch(){
        long reported=-1L;
        while(running){
            try{Thread.sleep(2000L);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}
            long observed=heartbeat,elapsed=System.nanoTime()-observed;
            if(!running || !main.isAlive() || elapsed<timeout || observed==reported)continue;
            reported=observed;
            // One report per stalled heartbeat. Slow loads/OS suspension can also trigger this;
            // the report records evidence and deliberately makes no claim about fault ownership.
            String checkpoint=stage,snapshot=context;
            Path report=directory.resolve("freeze-"+stamp.format(Instant.now())+"-UTC.txt");
            StringBuilder text=new StringBuilder("Modular Shipyard possible freeze / long frame\n")
                .append("Captured UTC: ").append(Instant.now()).append('\n').append(platform)
                .append("\nSeconds since application heartbeat: ").append(elapsed/1_000_000_000L)
                .append("\nLast checkpoint: ").append(checkpoint).append('\n').append(snapshot)
                .append("\nMain thread: ").append(main.getName()).append(" (id ").append(main.getId()).append(")\n")
                .append("A slow load or suspended computer can also produce this report. This is not a crash verdict.\n");
            Thread.getAllStackTraces().entrySet().stream()
                .sorted(Comparator.comparing(entry->entry.getKey().getName()))
                .forEach(entry->{
                    Thread thread=entry.getKey();
                    text.append("\n\"").append(thread.getName()).append("\" id=").append(thread.getId())
                        .append(" state=").append(thread.getState()).append(thread==main?" [APPLICATION THREAD]":"").append('\n');
                    for(StackTraceElement frame:entry.getValue())text.append("    at ").append(frame).append('\n');
                });
            try{
                Files.createDirectories(directory);
                Files.writeString(report,text,StandardOpenOption.CREATE_NEW);
                Log.warn("Shipyard saved possible-freeze diagnostics: @",report);
            }catch(IOException writeFailure){Log.err("Cannot save Shipyard freeze diagnostics to "+report,writeFailure);}
        }
    }
}
