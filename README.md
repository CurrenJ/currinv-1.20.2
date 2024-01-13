# CurrInv

CurrInv is a client-side mod that provides autonomous tools for better storage management, as well as complimentary navigation features.
Users can activate the mod's features by entering commands into the chat window.

## Storage Management

### Full Suite Sorter

Full-suite sorter is a tool that allows users to sort their inventory and storage containers with a single command. The tool will assist in sorting and consolidating any storage containers within a 16 block radius of the user.

**IMPORTANT USAGE NOTES**: 

This tool is designed to work in single-player and while connected to any server, ***even if the server does not have the mod installed***. The content of storage containers is unknown until the tool opens them, so the tool will not be able to sort any storage containers that are locked or otherwise inaccessible to the user. Containers opened by the user themselves will also be inventoried for use in sorting tasks.

To interrupt an ongoing action, run the previous full-suite sorter command again. For example, if you are in the middle of a scan, run `/currInv fullSuiteSorter scan` again to interrupt the scan.

### `/currInv fullSuiteSorter scan`

This command will scan the area for any storage containers within a 16 block radius of the user, and then autonomously navigate the player to open each of them. The tool will then store the location and contents of each storage container in memory. This allows the contents of the container to be sorted and consolidated with other containers using the below commands.

### `/currInv fullSuiteSorter collect [item]`

Given an item, this command will autonomously navigate the player to each storage container that contains the item, and then collect all of the item from the container.

### `/currInv fullSuiteSorter consolidate`

This task attempts to autonomously consolidate the contents of all storage containers in the area. The tool will navigate the player to containers, and then attempt to consolidate the contents of the container with other containers in the area. In a well-used storage system, this can free up a fair number of slots, and clean up erroneous item placements.

### `/currInv fullSuiteSorter whereIs [item]`

This command highlights containers that contain the given item. This is useful for finding where you put that one item that you need.

### `/currInv sorter isQuickStackEnabled [true/false]`

This command enables or disables the quick-stack feature of the sorter. When enabled, the sorter will attempt to quick-stack items into storage containers that are already open. This is useful for quickly sorting items into storage containers.

### `/currInv sorter isSortingEnabled [true/false]`

This command enables or disables the sorting feature of the sorter. When enabled, the sorter will attempt to organize the slots of containers while they are open. This is useful for ordering containers.

## Navigation

### `/currInv nav escapeRope`

When this commamd is run without sky visible directly above the player, the tool will autonomously navigate the player to the surface. This is useful for escaping caves, ravines, and other underground areas. Maybe the most useful feature of this mod. :D

### `/currInv nav toMarker [block]`

Navigates to the closest marker of the given block type. For example, `/currInv nav toMarker orange_wool` will navigate the player to the closest orange wool block, if there is one nearby. This is useful for finding specific resources, or for finding your way back to a specific location. Implemented mainly to demonstrate the capability of the navigation system.

### `/currInv nav toSurfacePos [x] [y] [z]`

Navigates to the nearest position on the surface, given the x, y, and z coordinates of the target location. The y-coordinate is effectively discared. This is useful for finding your way back to a specific location.

## Other

### `/currInv tools whereIsMyPortal`

This command will print the coordinates of the last known location of the player's nether portal. This is useful for finding your way back to the overworld.

## Config

### `/currInv config currentSortingStyle [style]`

Sets the current sorting style. The sorting style determines how the sorter will sort items into storage containers. The following styles are available: `creativeMenu`, `lexicographical`, and `quantity`.

### `/currInv config markSortingExempt`

Marks the container that the player is currently looking at as exempt from sorting. This is useful for marking containers that are already sorted, or for marking containers that you don't want to be sorted. If the selected container is already marked as exempt, the exemption will be removed.

### `/currInv config drawSortingExempt`

Toggles the display of sorting exempt containers. Sorting exempt containers are highlighted in red.
