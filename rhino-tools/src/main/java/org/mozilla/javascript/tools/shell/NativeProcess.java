/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tools.shell;

import java.util.Locale;
import java.util.Map;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

public class NativeProcess extends ScriptableObject {
    private static final long serialVersionUID = 1L;

    @Override
    public String getClassName() {
        return "process";
    }

    public static void init(Context cx, Scriptable scope, boolean sealed) {
        NativeProcess process = new NativeProcess();
        process.setPrototype(getObjectPrototype(scope));
        process.setParentScope(scope);

        // env - backed by System.getenv()
        ProcessEnv env = new ProcessEnv();
        env.setPrototype(getObjectPrototype(scope));
        env.setParentScope(scope);
        process.defineProperty("env", env, DONTENUM);

        // argv
        process.defineProperty("argv", cx.newArray(scope, 0), DONTENUM);

        // version
        process.defineProperty("version", "v0.0.0", DONTENUM);
        process.defineProperty("versions", cx.newObject(scope), DONTENUM);

        // platform/arch/pid
        process.defineProperty("platform", getPlatform(), DONTENUM);
        process.defineProperty("arch", getArch(), DONTENUM);
        process.defineProperty("pid", ProcessHandle.current().pid(), DONTENUM);

        // stdout
        ScriptableObject stdout = (ScriptableObject) cx.newObject(scope);
        stdout.defineBuiltinProperty(scope, "write", 1, NativeProcess::js_stdoutWrite);
        stdout.defineProperty("isTTY", isTTY(), DONTENUM);
        process.defineProperty("stdout", stdout, DONTENUM);

        // stderr
        ScriptableObject stderr = (ScriptableObject) cx.newObject(scope);
        stderr.defineBuiltinProperty(scope, "write", 1, NativeProcess::js_stderrWrite);
        stderr.defineProperty("isTTY", isTTY(), DONTENUM);
        process.defineProperty("stderr", stderr, DONTENUM);

        // stdin
        ScriptableObject stdin = (ScriptableObject) cx.newObject(scope);
        stdin.defineProperty("isTTY", isTTY(), DONTENUM);
        process.defineProperty("stdin", stdin, DONTENUM);

        // functions
        process.defineBuiltinProperty(scope, "cwd", 0, NativeProcess::js_cwd);
        process.defineBuiltinProperty(scope, "exit", 1, NativeProcess::js_exit);
        process.defineBuiltinProperty(scope, "nextTick", 1, NativeProcess::js_nextTick);
        process.defineBuiltinProperty(scope, "umask", 0, NativeProcess::js_umask);
        process.defineBuiltinProperty(scope, "hrtime", 0, NativeProcess::js_hrtime);
        process.defineBuiltinProperty(scope, "uptime", 0, NativeProcess::js_uptime);

        // hrtime.bigint()
        Object hrtimeFunc = process.get("hrtime", process);
        if (hrtimeFunc instanceof ScriptableObject) {
            ((ScriptableObject) hrtimeFunc)
                    .defineBuiltinProperty(scope, "bigint", 0, NativeProcess::js_hrtimeBigint);
        }

        // event emitter stubs
        process.defineBuiltinProperty(scope, "on", 2, NativeProcess::js_eventStub);
        process.defineBuiltinProperty(scope, "once", 2, NativeProcess::js_eventStub);
        process.defineBuiltinProperty(scope, "off", 2, NativeProcess::js_eventStub);
        process.defineBuiltinProperty(scope, "addListener", 2, NativeProcess::js_eventStub);
        process.defineBuiltinProperty(scope, "removeListener", 2, NativeProcess::js_eventStub);
        process.defineBuiltinProperty(scope, "removeAllListeners", 1, NativeProcess::js_eventStub);
        process.defineBuiltinProperty(scope, "emit", 2, NativeProcess::js_emitStub);
        process.defineBuiltinProperty(scope, "listeners", 1, NativeProcess::js_listenersStub);
        process.defineBuiltinProperty(
                scope, "listenerCount", 1, NativeProcess::js_listenerCountStub);

        if (sealed) {
            process.sealObject();
        }
        ScriptableObject.defineProperty(scope, "process", process, ScriptableObject.DONTENUM);
    }

    public static void setArgv(Scriptable scope, String mainModule, String[] args) {
        Object processObj = scope.get("process", scope);
        if (processObj instanceof NativeProcess) {
            NativeProcess process = (NativeProcess) processObj;
            Context cx = Context.getCurrentContext();
            int offset = mainModule != null ? 2 : 1;
            Object[] argv = new Object[args.length + offset];
            argv[0] = "rhino";
            if (mainModule != null) {
                argv[1] = new java.io.File(mainModule).getAbsolutePath();
            }
            System.arraycopy(args, 0, argv, offset, args.length);
            process.defineProperty("argv", cx.newArray(scope, argv), DONTENUM);
        }
    }

    private static Object js_cwd(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return System.getProperty("user.dir");
    }

    private static Object js_exit(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        int code = 0;
        if (args.length > 0) {
            code = ScriptRuntime.toInt32(args[0]);
        }
        System.exit(code);
        return Undefined.instance;
    }

    private static Object js_nextTick(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        if (args.length > 0 && args[0] instanceof org.mozilla.javascript.Function) {
            org.mozilla.javascript.Function fn = (org.mozilla.javascript.Function) args[0];
            // Collect extra arguments for nextTick callback
            Object[] cbArgs = new Object[Math.max(0, args.length - 1)];
            System.arraycopy(args, 1, cbArgs, 0, cbArgs.length);
            fn.call(cx, scope, scope, cbArgs);
        }
        return Undefined.instance;
    }

    private static Object js_umask(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return 0;
    }

    private static final long START_TIME = System.nanoTime();
    private static final long START_TIME_MS = System.currentTimeMillis();

    private static Object js_hrtime(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        long elapsed = System.nanoTime() - START_TIME;
        long seconds = elapsed / 1_000_000_000L;
        long nanos = elapsed % 1_000_000_000L;
        return cx.newArray(scope, new Object[] {(double) seconds, (double) nanos});
    }

    private static Object js_hrtimeBigint(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return (double) (System.nanoTime() - START_TIME);
    }

    private static Object js_uptime(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return (System.currentTimeMillis() - START_TIME_MS) / 1000.0;
    }

    private static Object js_stdoutWrite(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        if (args.length > 0) {
            System.out.print(ScriptRuntime.toString(args[0]));
            System.out.flush();
        }
        return Boolean.TRUE;
    }

    private static Object js_stderrWrite(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        if (args.length > 0) {
            System.err.print(ScriptRuntime.toString(args[0]));
            System.err.flush();
        }
        return Boolean.TRUE;
    }

    // Event emitter stubs - return `this` for chaining
    private static Object js_eventStub(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return thisObj;
    }

    private static Object js_emitStub(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return Boolean.FALSE;
    }

    private static Object js_listenersStub(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return cx.newArray(scope, 0);
    }

    private static Object js_listenerCountStub(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return 0;
    }

    @SuppressWarnings("SystemConsoleNull")
    private static boolean isTTY() {
        return System.console() != null;
    }

    private static String getPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("linux")) return "linux";
        if (os.contains("mac") || os.contains("darwin")) return "darwin";
        if (os.contains("win")) return "win32";
        if (os.contains("freebsd")) return "freebsd";
        if (os.contains("sunos") || os.contains("solaris")) return "sunos";
        return os;
    }

    private static String getArch() {
        String arch = System.getProperty("os.arch", "");
        switch (arch) {
            case "amd64":
            case "x86_64":
                return "x64";
            case "aarch64":
                return "arm64";
            case "x86":
            case "i386":
            case "i686":
                return "ia32";
            default:
                return arch;
        }
    }

    /** process.env - backed by System.getenv() */
    static class ProcessEnv extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        @Override
        public String getClassName() {
            return "Object";
        }

        @Override
        public boolean has(String name, Scriptable start) {
            if (super.has(name, start)) return true;
            return System.getenv(name) != null;
        }

        @Override
        public Object get(String name, Scriptable start) {
            Object sup = super.get(name, start);
            if (sup != NOT_FOUND) return sup;
            String val = System.getenv(name);
            if (val != null) return val;
            return NOT_FOUND;
        }

        @Override
        public void put(String name, Scriptable start, Object value) {
            // Environment variables can't be set from Java, but allow
            // script-level overrides via the normal property mechanism
            super.put(name, start, value);
        }

        @Override
        public Object[] getIds() {
            Map<String, String> env = System.getenv();
            return env.keySet().toArray();
        }

        @Override
        public Object[] getAllIds() {
            return getIds();
        }
    }
}
