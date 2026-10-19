
- **Door opening.** Ground-navigating game mobs can open wooden doors (`setCanOpenDoors` plus `OpenDoorGoal`, which leaves them open), so a door's state when a game starts doesn't cut zones apart.
- **Window-aware spawning.** The spawner only picks a profile that fits the marker's window. It uses `GameWorld.canSpawn`, which compares the profile's entity-type size, rounded up to whole blocks, with the window opening. An iron golem (2x3) is never sent to a 1x2 or 2x2 window.
