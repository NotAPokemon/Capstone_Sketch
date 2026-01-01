# Requirements

- msys64 w/ mingw64 & clang (if on windows)
- clang in user path for mac
- linux not fully supported but still possible

## OS

- Windows x86
- Mac sillicon
- Linux (Theoretically can work but you will have to build natives yourself)

# Instructions

- Ensure the port 6967 is not used by another program before running these steps
- if the port is being used change the config file and tell people joining the server to do so aswell

## Singleplayer

1. Run the project by using the ./runjava command
2. Click Host game
3. Run the project in a seperate terminal (this is the game client) with ./runproject
4. click Join game
5. Enjoy!

## Multiplayer

1. (Host only) If you are hosting the server do steps 1-4 from single player instructions
2. (Host only - LAN method) to allow others to join find the IP address of your machine (looks like 192.168.xx.xx)

   - On windows run `ipconfig` in terminal and look for IPv4 address
   - On Mac run `ipconfig getifaddr en0`

3. (Host only - public server method) to allow others to join find your public IP address then port forward port 6967

   - On windows open powershell and run `(Invoke-WebRequest ifconfig.me/ip).Content.Trim()`
   - On Mac run `curl ifconfig.me`

4. To join a server go to [config.json](./src/resources/)

5. create a config.json if not already there

6. set the key `ip` to the server IP given by the host (you may also need to change the port if the host is not using 6967)

7. Then do steps 3-5 from the single player instructions

example config:

```
{
  "port": 6967,
  "ip": "localhost"
}
```
