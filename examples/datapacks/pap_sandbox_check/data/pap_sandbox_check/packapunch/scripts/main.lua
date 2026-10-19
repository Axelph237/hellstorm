-- Each line reports whether a dangerous global is reachable. All should say "blocked".
for _, name in ipairs({"io", "os", "luajava", "debug", "dofile", "loadfile", "load", "package", "coroutine"}) do
  log.info(name .. ": " .. (_G[name] == nil and "blocked" or "REACHABLE"))
end

-- A runaway listener. The server must keep ticking and log "instruction budget ... exceeded".
events.on("round_start", function()
  while true do end
end)
