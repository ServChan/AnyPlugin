package org.lts.anyplugin.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdminCommandsTest {

    private static String run(String op, String json) {
        return AdminCommands.command(op, JsonParser.parseString(json).getAsJsonObject());
    }

    private static void rejects(String op, String json) {
        assertThrows(IllegalArgumentException.class, () -> run(op, json));
    }

    @Test
    void groupOperations() {
        assertEquals("perm group vip create", run("group.create", "{group:'vip'}"));
        assertEquals("perm group vip delete", run("group.delete", "{group:'vip'}"));
        assertEquals("perm group vip set anyplugin.fly true", run("group.set", "{group:'vip',node:'anyplugin.fly',value:true}"));
        assertEquals("perm group vip set anyplugin.* false", run("group.set", "{group:'vip',node:'anyplugin.*',value:false}"));
        assertEquals("perm group vip unset anyplugin.fly", run("group.unset", "{group:'vip',node:'anyplugin.fly'}"));
        assertEquals("perm group vip parent add default", run("group.parent", "{group:'vip',add:true,parent:'default'}"));
        assertEquals("perm group vip parent remove default", run("group.parent", "{group:'vip',add:false,parent:'default'}"));
        assertEquals("perm group vip prefix <gold>[VIP]", run("group.meta", "{group:'vip',field:'prefix',value:' <gold>[VIP] '}"));
        assertEquals("perm group vip suffix clear", run("group.meta", "{group:'vip',field:'suffix',value:''}"));
        assertEquals("perm group vip color clear", run("group.meta", "{group:'vip',field:'COLOR'}"));
        assertEquals("perm group vip weight 25", run("group.meta", "{group:'vip',field:'weight',value:'25'}"));
    }

    @Test
    void userOperations() {
        assertEquals("perm user Steve_1 group add vip", run("user.group", "{name:'Steve_1',mode:'add',group:'vip'}"));
        assertEquals("perm user Steve_1 group set vip 7d", run("user.group", "{name:'Steve_1',mode:'set',group:'vip',duration:' 7d '}"));
        assertEquals("perm user Steve_1 group remove vip", run("user.group", "{name:'Steve_1',mode:'remove',group:'vip',duration:'  '}"));
        assertEquals("perm user Steve_1 set anyplugin.fly false 1h30m",
                run("user.set", "{name:'Steve_1',node:'anyplugin.fly',value:false,duration:'1h30m'}"));
        assertEquals("perm user Steve_1 set anyplugin.fly true", run("user.set", "{name:'Steve_1',node:'anyplugin.fly',value:true,duration:null}"));
        assertEquals("perm user Steve_1 unset anyplugin.fly", run("user.unset", "{name:'Steve_1',node:'anyplugin.fly'}"));
        assertEquals("perm user Steve_1 prefix <red>Boss", run("user.meta", "{name:'Steve_1',field:'prefix',value:'<red>Boss'}"));
        assertEquals("perm user Steve_1 suffix clear", run("user.meta", "{name:'Steve_1',field:'suffix',value:''}"));
    }

    @Test
    void moderationOperations() {
        assertEquals("ban Griefer 7d x-ray", run("punish", "{kind:'ban',name:'Griefer',duration:'7d',reason:'x-ray'}"));
        assertEquals("ipban Griefer", run("punish", "{kind:'ipban',name:'Griefer'}"));
        assertEquals("mute Griefer 30m spam in chat", run("punish", "{kind:'mute',name:'Griefer',duration:'30m',reason:'spam\\nin chat'}"));
        assertEquals("warn Griefer rude", run("punish", "{kind:'warn',name:'Griefer',duration:'7d',reason:'rude'}"));
        assertEquals("kick Griefer", run("punish", "{kind:'kick',name:'Griefer',duration:'not a duration'}"));
        assertEquals("unban Griefer", run("revoke", "{kind:'unban',name:'Griefer'}"));
        assertEquals("unmute Griefer", run("revoke", "{kind:'unmute',name:'Griefer'}"));
        assertEquals("unwarn 42", run("unwarn", "{id:42}"));
        assertEquals("unwarn 1", run("unwarn", "{id:-5}"));
    }

    @Test
    void playerOperations() {
        assertEquals("gm creative Alex", run("player.gamemode", "{mode:'Creative',name:'Alex'}"));
        assertEquals("heal Alex", run("player.heal", "{name:'Alex'}"));
        assertEquals("feed Alex", run("player.feed", "{name:'Alex'}"));
        assertEquals("fly Alex", run("player.fly", "{name:'Alex'}"));
        assertEquals("freeze Alex", run("player.freeze", "{name:'Alex'}"));
        assertEquals("minecraft:tp Alex", run("player.tpto", "{name:'Alex'}"));
        assertEquals("tphere Alex", run("player.tphere", "{name:'Alex'}"));
        assertEquals("invsee Alex", run("player.invsee", "{name:'Alex'}"));
        assertEquals("enderchest Alex", run("player.enderchest", "{name:'Alex'}"));
    }

    @Test
    void warpOperations() {
        assertEquals("warp спавн", run("warp.tp", "{name:'спавн'}"));
        assertEquals("delwarp shop-1", run("warp.delete", "{name:'shop-1'}"));
        assertEquals("setwarp shop diamond Main market", run("warp.create", "{name:'shop',icon:'diamond',description:' Main market '}"));
        assertEquals("setwarp shop diamond", run("warp.create", "{name:'shop',icon:'diamond'}"));
    }

    @Test
    void serverOperations() {
        assertEquals("maintenance on", run("server.maintenance", "{value:true}"));
        assertEquals("maintenance off", run("server.maintenance", "{value:false}"));
        assertEquals("restart 5m", run("server.restart", "{delay:'5m'}"));
        assertEquals("restart cancel", run("server.restart", "{delay:'cancel'}"));
        assertEquals("broadcast Server restarts soon", run("server.broadcast", "{value:'Server restarts soon'}"));
        assertEquals("day", run("server.time", "{value:'day'}"));
        assertEquals("night", run("server.time", "{value:'NIGHT'}"));
        assertEquals("thunder", run("server.weather", "{value:'thunder'}"));
        assertEquals("vanish", run("server.vanish", "{}"));
        assertEquals("anyplugin reload", run("server.reload", "{}"));
    }

    @Test
    void unknownOperationReturnsNull() {
        assertNull(run("op.me", "{name:'Alex'}"));
        assertNull(run("", "{}"));
        assertNull(run("config.set", "{key:'language',value:'en'}"));
        assertNull(run("GROUP.CREATE", "{group:'vip'}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Alex op", "Alex\nop", "Alex\n", "Alex;op", "Al/ex", "", "ThisNameIsWayTooLong", "Алекс", "Alex\u0000"})
    void rejectsBadPlayerNames(String name) {
        JsonObject m = new JsonObject();
        m.addProperty("name", name);
        assertThrows(IllegalArgumentException.class, () -> AdminCommands.command("player.heal", m));
        m.addProperty("kind", "ban");
        assertThrows(IllegalArgumentException.class, () -> AdminCommands.command("punish", m));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vip create", "vip\ndelete", "VIP", "v.ip", "", "a23456789012345678901234567890123", "vip*"})
    void rejectsBadGroupNames(String group) {
        JsonObject m = new JsonObject();
        m.addProperty("group", group);
        assertThrows(IllegalArgumentException.class, () -> AdminCommands.command("group.create", m));
        JsonObject parent = new JsonObject();
        parent.addProperty("group", "vip");
        parent.addProperty("add", true);
        parent.addProperty("parent", group);
        assertThrows(IllegalArgumentException.class, () -> AdminCommands.command("group.parent", parent));
    }

    @ParameterizedTest
    @ValueSource(strings = {"anyplugin.fly true", "anyplugin.fly\n", "AnyPlugin.Fly", "anyplugin/fly", "", "anyplugin.fly;op"})
    void rejectsBadNodes(String node) {
        JsonObject m = new JsonObject();
        m.addProperty("group", "vip");
        m.addProperty("name", "Alex");
        m.addProperty("node", node);
        m.addProperty("value", true);
        assertThrows(IllegalArgumentException.class, () -> AdminCommands.command("group.set", m));
        assertThrows(IllegalArgumentException.class, () -> AdminCommands.command("user.set", m));
        assertThrows(IllegalArgumentException.class, () -> AdminCommands.command("user.unset", m));
    }

    @Test
    void rejectsOversizeNode() {
        rejects("group.unset", "{group:'vip',node:'" + "a".repeat(129) + "'}");
        assertEquals("perm group vip unset " + "a".repeat(128), run("group.unset", "{group:'vip',node:'" + "a".repeat(128) + "'}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"spawn tp", "spawn\nop", "spa.wn", "", "a234567890123456789012345", "../spawn"})
    void rejectsBadWarpNames(String warp) {
        JsonObject m = new JsonObject();
        m.addProperty("name", warp);
        m.addProperty("icon", "diamond");
        assertThrows(IllegalArgumentException.class, () -> AdminCommands.command("warp.tp", m));
        assertThrows(IllegalArgumentException.class, () -> AdminCommands.command("warp.delete", m));
        assertThrows(IllegalArgumentException.class, () -> AdminCommands.command("warp.create", m));
    }

    @Test
    void rejectsBadWarpIcon() {
        rejects("warp.create", "{name:'shop',icon:'minecraft:diamond'}");
        rejects("warp.create", "{name:'shop',icon:'diamond sword'}");
        rejects("warp.create", "{name:'shop',icon:'Diamond'}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"7d op", "7d\nop", "7x", "0m", "forever", "1d 2h", "-1d", "9999999d"})
    void rejectsBadDurations(String duration) {
        JsonObject m = new JsonObject();
        m.addProperty("name", "Alex");
        m.addProperty("group", "vip");
        m.addProperty("mode", "add");
        m.addProperty("node", "anyplugin.fly");
        m.addProperty("value", true);
        m.addProperty("kind", "ban");
        m.addProperty("duration", duration);
        assertThrows(IllegalArgumentException.class, () -> AdminCommands.command("user.group", m));
        assertThrows(IllegalArgumentException.class, () -> AdminCommands.command("user.set", m));
        assertThrows(IllegalArgumentException.class, () -> AdminCommands.command("punish", m));
    }

    @Test
    void rejectsBadRestartDelay() {
        rejects("server.restart", "{delay:'5m now'}");
        rejects("server.restart", "{delay:''}");
        rejects("server.restart", "{delay:'soon'}");
        rejects("server.restart", "{}");
        rejects("server.restart", "{delay:['5m']}");
    }

    @Test
    void rejectsOversizeText() {
        rejects("punish", "{kind:'ban',name:'Alex',reason:'" + "x".repeat(201) + "'}");
        assertEquals("kick Alex " + "x".repeat(200), run("punish", "{kind:'kick',name:'Alex',reason:'" + "x".repeat(200) + "'}"));
        rejects("server.broadcast", "{value:'" + "x".repeat(201) + "'}");
        rejects("group.meta", "{group:'vip',field:'prefix',value:'" + "x".repeat(121) + "'}");
        rejects("user.meta", "{name:'Alex',field:'prefix',value:'" + "x".repeat(121) + "'}");
        rejects("warp.create", "{name:'shop',icon:'diamond',description:'" + "x".repeat(121) + "'}");
    }

    @Test
    void textCannotStartANewLine() {
        assertEquals("broadcast hi /op Alex", run("server.broadcast", "{value:'hi\\n/op Alex'}"));
        assertEquals("broadcast hi  /op Alex", run("server.broadcast", "{value:'hi\\r\\n/op Alex'}"));
        assertEquals("perm user Alex prefix a b", run("user.meta", "{name:'Alex',field:'prefix',value:'a\\nb'}"));
    }

    @Test
    void rejectsEmptyBroadcast() {
        rejects("server.broadcast", "{value:'   '}");
        rejects("server.broadcast", "{value:'\\n'}");
        rejects("server.broadcast", "{}");
    }

    @Test
    void rejectsUnknownChoices() {
        rejects("punish", "{kind:'op',name:'Alex'}");
        rejects("revoke", "{kind:'unwarn',name:'Alex'}");
        rejects("player.gamemode", "{mode:'god',name:'Alex'}");
        rejects("user.group", "{name:'Alex',mode:'promote',group:'vip'}");
        rejects("group.meta", "{group:'vip',field:'parents',value:'admin'}");
        rejects("user.meta", "{name:'Alex',field:'color',value:'red'}");
        rejects("server.time", "{value:'noon'}");
        rejects("server.weather", "{value:'snow'}");
    }

    @Test
    void rejectsNonNumericWeight() {
        rejects("group.meta", "{group:'vip',field:'weight',value:'10 op'}");
        rejects("group.meta", "{group:'vip',field:'weight',value:'heavy'}");
        rejects("group.meta", "{group:'vip',field:'weight',value:''}");
    }

    @Test
    void rejectsMissingOrNonPrimitiveFields() {
        rejects("player.heal", "{}");
        rejects("player.heal", "{name:null}");
        rejects("player.heal", "{name:['Alex']}");
        rejects("player.heal", "{name:{a:'Alex'}}");
        rejects("group.set", "{group:'vip',node:'anyplugin.fly'}");
        rejects("server.maintenance", "{}");
        rejects("unwarn", "{id:'3 4'}");
    }
}
