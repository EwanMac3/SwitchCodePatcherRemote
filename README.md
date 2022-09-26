# SwitchCodePatcherRemote
Java application to remotely patch code memory on Nintendo Switch with GDB. Has a basic check for scene loading in the Splatoon games to automatically disable patches.

Uses GDB breakpoints + memory editing to write to games. It works fast enough to not hugely freeze games while writing, so it should be fine to use online.
