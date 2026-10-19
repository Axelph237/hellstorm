-- Every namespace's "main" script runs when a game's scripts load.
events.on("zombie_killed", function(e)
  if e.headshot then
    command("playsound minecraft:entity.experience_orb.pickup master @a")
  end
end)

log.info("pap_example loaded for " .. #game.players() .. " player(s)")
