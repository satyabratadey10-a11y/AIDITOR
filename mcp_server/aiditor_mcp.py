#!/usr/bin/env python3
"""
AIDITOR True Real MCP Server
Connects Antigravity (Model Context Protocol) to the AIDITOR Android App.
Provides live vision (screenshots), physical touch automation (taps, drags),
deep UI state inspection, ViewModel editing action triggers, and real-time logs.
"""

import sys
import json
import os
import subprocess
import time
import urllib.request
import urllib.error
import urllib.parse
from typing import Any, Dict, List, Optional

BRIDGE_URL = "http://127.0.0.1:8765"
DEFAULT_SCREENSHOT_PATH = "/sdcard/Download/aiditor_live_screen.png"
APP_PACKAGE = "com.aiditor.app"
MAIN_ACTIVITY = "com.aiditor.app/.MainActivity"

def http_request(endpoint: str, method: str = "GET", data: Optional[Dict[str, Any]] = None, timeout: float = 6.0) -> Dict[str, Any]:
    url = f"{BRIDGE_URL}{endpoint}"
    req_data = None
    headers = {"Content-Type": "application/json"}
    if data is not None:
        req_data = json.dumps(data).encode("utf-8")
        req = urllib.request.Request(url, data=req_data, headers=headers, method=method)
    else:
        req = urllib.request.Request(url, headers=headers, method=method)

    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            resp_body = response.read().decode("utf-8")
            return json.loads(resp_body)
    except Exception as e:
        return {"status": "error", "message": f"HTTP Bridge request to {url} failed: {e}"}

def broadcast_command(action: str, extras: Optional[Dict[str, Any]] = None) -> bool:
    cmd = ["am", "broadcast", "-a", "com.aiditor.app.MCP_ACTION", "--es", "action", action]
    if extras:
        for k, v in extras.items():
            if isinstance(v, float):
                cmd.extend(["--ef", str(k), str(v)])
            elif isinstance(v, int):
                cmd.extend(["--ei", str(k), str(v)])
            elif isinstance(v, bool):
                cmd.extend(["--ez", str(k), str(v).lower()])
            else:
                cmd.extend(["--es", str(k), str(v)])
    try:
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
        return res.returncode == 0
    except Exception:
        return False

def launch_app() -> Dict[str, Any]:
    try:
        res = subprocess.run(["am", "start", "--user", "0", "-n", MAIN_ACTIVITY], capture_output=True, text=True, timeout=5)
        time.sleep(1.2)
        return {"status": "ok", "message": "AIDITOR app launched", "output": res.stdout.strip()}
    except Exception as e:
        return {"status": "error", "message": f"Failed to launch app: {e}"}

def take_screenshot() -> Dict[str, Any]:
    # Try HTTP bridge first
    res = http_request("/screenshot")
    if res.get("status") == "ok":
        return res

    # If HTTP bridge failed, try broadcast fallback
    broadcast_command("screenshot")
    time.sleep(1.0)
    if os.path.exists(DEFAULT_SCREENSHOT_PATH):
        try:
            stat = os.stat(DEFAULT_SCREENSHOT_PATH)
            return {
                "status": "ok",
                "path": DEFAULT_SCREENSHOT_PATH,
                "fileSizeBytes": stat.st_size,
                "note": "Captured via broadcast fallback"
            }
        except Exception:
            pass

    return {
        "status": "error",
        "message": "Failed to capture screenshot. Is the AIDITOR app running and visible on screen? Try running aiditor_launch_app first."
    }

def click_screen(norm_x: float, norm_y: float, x: Optional[float] = None, y: Optional[float] = None) -> Dict[str, Any]:
    payload = {"normX": norm_x, "normY": norm_y}
    if x is not None:
        payload["x"] = x
    if y is not None:
        payload["y"] = y

    res = http_request("/click", method="POST", data=payload)
    if res.get("status") == "ok":
        return res

    # Fallback broadcast
    extras = {"normX": float(norm_x), "normY": float(norm_y)}
    if x is not None:
        extras["x"] = float(x)
    if y is not None:
        extras["y"] = float(y)
    broadcast_command("click", extras)
    return {"status": "ok", "dispatched": extras, "fallback": True}

def drag_gesture(start_x: float, start_y: float, end_x: float, end_y: float, duration_ms: int = 350) -> Dict[str, Any]:
    payload = {
        "startX": start_x,
        "startY": start_y,
        "endX": end_x,
        "endY": end_y,
        "durationMs": duration_ms
    }
    res = http_request("/drag", method="POST", data=payload)
    if res.get("status") == "ok":
        return res

    # Fallback broadcast
    broadcast_command("drag", payload)
    return {"status": "ok", "dispatched": payload, "fallback": True}

def get_ui_state() -> Dict[str, Any]:
    res = http_request("/ui_state")
    if "isActivityRunning" in res or "workspace" in res:
        return res
    return {
        "status": "offline",
        "message": "AIDITOR bridge is not currently reachable. App might be closed or in background. Use aiditor_launch_app to start it."
    }

def trigger_action(action: str, params: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    payload = {"action": action}
    if params:
        payload.update(params)

    res = http_request("/action", method="POST", data=payload)
    if res.get("status") == "ok":
        return res

    # Fallback broadcast
    broadcast_command(action, payload)
    return {"status": "ok", "action": action, "fallback": True}

def get_logs(lines: int = 50) -> Dict[str, Any]:
    res = http_request(f"/logs?lines={lines}")
    if res.get("status") == "ok":
        return res

    # Fallback to local logcat if bridge is offline
    try:
        p = subprocess.run(
            ["logcat", "-d", "-s", "AIDITOR:*", "McpBridge:*", "-t", str(lines)],
            capture_output=True, text=True, timeout=3
        )
        log_lines = [l for l in p.stdout.split("\n") if l.strip()]
        return {"status": "ok", "logs": log_lines, "source": "direct_logcat"}
    except Exception as e:
        return {"status": "error", "message": f"Could not retrieve logs: {e}"}

# ----------------- MCP Tool Definitions -----------------

TOOLS = [
    {
        "name": "aiditor_take_screenshot",
        "description": "Takes a live hardware screenshot of the AIDITOR Android app window. The screenshot is saved to /sdcard/Download/aiditor_live_screen.png and can be inspected directly using view_file.",
        "inputSchema": {
            "type": "object",
            "properties": {},
            "required": []
        }
    },
    {
        "name": "aiditor_click_screen",
        "description": "Performs an actual physical touch click/tap on the AIDITOR app screen. Dispatches genuine ACTION_DOWN and ACTION_UP MotionEvents directly to the Compose decorView. Requires normalized coordinates (0.0 to 1.0) where (0.0, 0.0) is top-left and (1.0, 1.0) is bottom-right.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "normX": {
                    "type": "number",
                    "description": "Normalized X coordinate (0.0 = left edge, 1.0 = right edge, 0.5 = center horizontal)"
                },
                "normY": {
                    "type": "number",
                    "description": "Normalized Y coordinate (0.0 = top edge, 1.0 = bottom edge, 0.5 = center vertical)"
                }
            },
            "required": ["normX", "normY"]
        }
    },
    {
        "name": "aiditor_drag_gesture",
        "description": "Performs an actual smooth drag gesture across the AIDITOR app screen (e.g. for scrubbing the timeline slider, dragging motion tracking points, or tweaking speed curve handles). Uses normalized start and end coordinates.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "startX": {
                    "type": "number",
                    "description": "Normalized start X coordinate (0.0 to 1.0)"
                },
                "startY": {
                    "type": "number",
                    "description": "Normalized start Y coordinate (0.0 to 1.0)"
                },
                "endX": {
                    "type": "number",
                    "description": "Normalized end X coordinate (0.0 to 1.0)"
                },
                "endY": {
                    "type": "number",
                    "description": "Normalized end Y coordinate (0.0 to 1.0)"
                },
                "durationMs": {
                    "type": "integer",
                    "description": "Duration of the drag gesture in milliseconds (default: 350)"
                }
            },
            "required": ["startX", "startY", "endX", "endY"]
        }
    },
    {
        "name": "aiditor_get_ui_state",
        "description": "Inspects the live state of the AIDITOR app. Returns JSON containing the active screen, project details, playhead position, playback status, all timeline clips and layers, active editing tools, and undo/redo capabilities.",
        "inputSchema": {
            "type": "object",
            "properties": {},
            "required": []
        }
    },
    {
        "name": "aiditor_trigger_action",
        "description": "Executes a direct high-level editing or navigation action on the active AIDITOR ViewModel. Actions supported: 'play_pause', 'seek' (with 'time' parameter), 'step_forward', 'step_back', 'split', 'delete', 'duplicate', 'toggle_mute', 'select_tool' (with 'tool' parameter: 'OPTICAL_FLOW', 'COLOR_GRADE', 'SPEED_RAMP', 'MOTION_TRACKING', 'ROTOSCOPE'), 'start_motion_tracking', 'stop_tracking', 'clear_tracking', 'apply_tool', 'close_tool', 'show_export', 'hide_export', 'undo', 'redo'.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "description": "The action name to execute (e.g. 'play_pause', 'seek', 'split', 'delete', 'select_tool', 'start_motion_tracking', 'apply_tool', 'show_export', 'undo', 'redo')"
                },
                "params": {
                    "type": "object",
                    "description": "Optional parameters for the action (e.g. {'time': 2.5} for 'seek', {'tool': 'MOTION_TRACKING'} for 'select_tool')"
                }
            },
            "required": ["action"]
        }
    },
    {
        "name": "aiditor_get_logs",
        "description": "Fetches real-time in-app debug logs, error messages, and filtered Android Logcat output from AIDITOR.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "lines": {
                    "type": "integer",
                    "description": "Maximum number of recent log lines to retrieve (default: 50)"
                }
            },
            "required": []
        }
    },
    {
        "name": "aiditor_launch_app",
        "description": "Launches or brings the AIDITOR application to the foreground on the Android device.",
        "inputSchema": {
            "type": "object",
            "properties": {},
            "required": []
        }
    }
]

def handle_tool_call(name: str, args: Dict[str, Any]) -> Dict[str, Any]:
    if name == "aiditor_take_screenshot":
        res = take_screenshot()
        msg = json.dumps(res, indent=2)
        if res.get("status") == "ok":
            msg += f"\n\nLive screen saved to: {res.get('path', DEFAULT_SCREENSHOT_PATH)}\nYou can view this image using view_file."
        return {"content": [{"type": "text", "text": msg}], "isError": res.get("status") != "ok"}

    elif name == "aiditor_click_screen":
        norm_x = float(args.get("normX", 0.5))
        norm_y = float(args.get("normY", 0.5))
        x = args.get("x")
        y = args.get("y")
        res = click_screen(norm_x, norm_y, x, y)
        return {"content": [{"type": "text", "text": json.dumps(res, indent=2)}], "isError": res.get("status") == "error"}

    elif name == "aiditor_drag_gesture":
        start_x = float(args.get("startX", 0.2))
        start_y = float(args.get("startY", 0.5))
        end_x = float(args.get("endX", 0.8))
        end_y = float(args.get("endY", 0.5))
        duration = int(args.get("durationMs", 350))
        res = drag_gesture(start_x, start_y, end_x, end_y, duration)
        return {"content": [{"type": "text", "text": json.dumps(res, indent=2)}], "isError": res.get("status") == "error"}

    elif name == "aiditor_get_ui_state":
        res = get_ui_state()
        return {"content": [{"type": "text", "text": json.dumps(res, indent=2)}], "isError": False}

    elif name == "aiditor_trigger_action":
        action = str(args.get("action", ""))
        params = args.get("params", {})
        res = trigger_action(action, params)
        return {"content": [{"type": "text", "text": json.dumps(res, indent=2)}], "isError": res.get("status") == "error"}

    elif name == "aiditor_get_logs":
        lines = int(args.get("lines", 50))
        res = get_logs(lines)
        return {"content": [{"type": "text", "text": json.dumps(res, indent=2)}], "isError": False}

    elif name == "aiditor_launch_app":
        res = launch_app()
        return {"content": [{"type": "text", "text": json.dumps(res, indent=2)}], "isError": res.get("status") == "error"}

    else:
        return {"content": [{"type": "text", "text": f"Unknown tool: {name}"}], "isError": True}

# ----------------- MCP Server Loop (Stdio JSON-RPC 2.0) -----------------

def run_mcp_server():
    while True:
        try:
            line = sys.stdin.readline()
            if not line:
                break
            line = line.strip()
            if not line:
                continue

            # Support Content-Length prefix if present
            if line.lower().startswith("content-length:"):
                length = int(line.split(":")[1].strip())
                # Read empty line
                sys.stdin.readline()
                body = sys.stdin.read(length)
                request = json.loads(body)
            else:
                request = json.loads(line)

            req_id = request.get("id")
            method = request.get("method")
            params = request.get("params", {})

            if method == "initialize":
                response = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": {
                            "tools": {}
                        },
                        "serverInfo": {
                            "name": "aiditor-mcp",
                            "version": "1.0.0"
                        }
                    }
                }
            elif method == "notifications/initialized":
                continue
            elif method == "ping":
                response = {"jsonrpc": "2.0", "id": req_id, "result": {}}
            elif method == "tools/list":
                response = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "tools": TOOLS
                    }
                }
            elif method == "tools/call":
                tool_name = params.get("name", "")
                tool_args = params.get("arguments", {})
                tool_result = handle_tool_call(tool_name, tool_args)
                response = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": tool_result
                }
            else:
                response = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "error": {
                        "code": -32601,
                        "message": f"Method not found: {method}"
                    }
                }

            out = json.dumps(response)
            sys.stdout.write(f"Content-Length: {len(out.encode('utf-8'))}\r\n\r\n{out}")
            sys.stdout.flush()

        except (KeyboardInterrupt, EOFError):
            break
        except Exception as e:
            err_resp = {
                "jsonrpc": "2.0",
                "id": None,
                "error": {"code": -32603, "message": str(e)}
            }
            out = json.dumps(err_resp)
            sys.stdout.write(f"Content-Length: {len(out.encode('utf-8'))}\r\n\r\n{out}")
            sys.stdout.flush()

if __name__ == "__main__":
    if len(sys.argv) > 1:
        # CLI direct testing modes
        arg = sys.argv[1]
        if arg == "--test-screenshot":
            print(json.dumps(take_screenshot(), indent=2))
        elif arg == "--test-ui":
            print(json.dumps(get_ui_state(), indent=2))
        elif arg == "--test-logs":
            print(json.dumps(get_logs(), indent=2))
        elif arg == "--launch":
            print(json.dumps(launch_app(), indent=2))
        elif arg == "--list-tools":
            print(json.dumps(TOOLS, indent=2))
        else:
            print(f"Usage: {sys.argv[0]} [--test-screenshot | --test-ui | --test-logs | --launch | --list-tools]")
    else:
        run_mcp_server()
