# Rive Android Perfetto Tracing

This folder contains a reusable Perfetto config for profiling Rive Android runtime rendering:

- `rive-trace.textproto`

## Usage

1. Update `atrace_apps` in `rive-trace.textproto` to your Android package name. It is currently set to the value for the Rive Android sample app.
2. Push the config to the device:

```bash
adb push tools/perfetto/rive-trace.textproto /data/local/tmp/rive-trace.textproto
```

3. Record a trace:

```bash
adb shell perfetto -c /data/local/tmp/rive-trace.textproto -o /data/misc/perfetto-traces/rive-trace.pftrace
```

4. Pull the trace:

```bash
adb pull /data/misc/perfetto-traces/rive-trace.pftrace .
```

5. Open the trace in the Perfetto UI:
   [https://ui.perfetto.dev](https://ui.perfetto.dev)
