-- Each line reports whether a dangerous global is reachable. All should say "blocked".
for _, name in ipairs({"io", "os", "luajava", "debug", "dofile", "loadfile", "load", "package", "coroutine"}) do
  log.info(name .. ": " .. (_G[name] == nil and "blocked" or "REACHABLE"))
end

-- String methods use this script's own capped string table, never the one TacZ's gun scripts share. All should say "blocked".
log.info("method rep past the cap: " .. (pcall(function() return ("x"):rep(100000000) end) and "REACHABLE" or "blocked"))
log.info("string.dump through a method: " .. (("x").dump == nil and "blocked" or "REACHABLE"))
log.info("shared string metatable: " .. (getmetatable("").__index == string and "blocked" or "REACHABLE"))
log.info("doubling with ..: " .. (pcall(function() local s = "x" for _ = 1, 40 do s = s .. s end return s end) and "REACHABLE" or "blocked"))

-- A runaway listener. The server must keep ticking and log "instruction budget ... exceeded".
events.on("round_start", function()
  while true do end
end)
