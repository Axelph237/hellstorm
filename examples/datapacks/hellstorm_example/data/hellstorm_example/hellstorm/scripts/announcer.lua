local util = require("hellstorm_example:util")

local M = {}

function M.on_round_end(e)
  util.title("Round " .. e.round .. " survived")
end

function M.on_late_round(e)
  command("say Round " .. e.round .. ". They're getting faster.")
end

return M
