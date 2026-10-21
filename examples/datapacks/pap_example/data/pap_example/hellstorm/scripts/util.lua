-- Helpers shared by the example scripts. Loaded once with require("pap_example:util").
local util = {}

-- Builds a JSON text component for /title and /tellraw, escaping quotes and backslashes.
function util.json_text(text, color)
  local escaped = text:gsub("\\", "\\\\"):gsub('"', '\\"')
  return '{"text":"' .. escaped .. '","color":"' .. (color or "red") .. '"}'
end

function util.title(text)
  command("title @a title " .. util.json_text(text))
end

return util
