# rawcast

Record a screencast as two raw files: the screen with your microphone, and
the webcam with the same microphone. No OBS, no scenes, no overlays. The
picture-in-picture, cuts and captions happen later in the editor, where the
two clips auto-align by waveform.

```
record sample
Recording into /home/max/Videos/Recordings/2026-10-21T11:56-sample
Press Enter to stop both recordings.
```

```
~/Videos/Recordings/2026-10-21T11:56-sample/
├── screen.mkv   full resolution, 60 fps, H.264, microphone as opus track
└── webcam.mkv   1920x1080, 30 fps, H.264, microphone as FLAC track
```

Three babashka scripts:

| script | records |
|---|---|
| `record [name]` | both, into one dated folder |
| `record-screen [out.mkv]` | the screen |
| `record-webcam [out.mkv]` | the webcam |

Enter or Ctrl-C stops a recording. The files are finalized before the script
exits.

## Why

On KDE Plasma (Wayland) nothing simple records the screen: `ffmpeg -f x11grab`
sees only XWayland windows and `kmsgrab` needs root. The screen therefore goes
through the xdg-desktop-portal via [gpu-screen-recorder](https://git.dec05eba.com/gpu-screen-recorder/about/),
the webcam through ffmpeg. Two files instead of one composite keep every pixel
of both sources for the edit.

## Requirements

- [babashka](https://babashka.org/)
- gpu-screen-recorder as Flatpak:
  `flatpak install flathub com.dec05eba.gpu_screen_recorder`
- an ffmpeg with the `pulse` input device and your camera encoder
  (`h264_nvenc` or `libx264`). The Ubuntu package `ffmpeg` has both; the
  Homebrew build has neither, hence the default `/usr/bin/ffmpeg`.
- `pactl` (pulseaudio-utils) and `setsid` (util-linux)

Written for a laptop with KDE Plasma 6.4 on Wayland, PipeWire, an Intel +
NVIDIA GPU, a 1080p MJPEG webcam and a Shure MV7 USB microphone. Other setups
need the settings below.

## Install

```
git clone https://github.com/maxweber/rawcast ~/bin/rawcast
~/bin/rawcast/record
```

The first `record-screen` run shows the desktop's screen picker: pick the
screen. gpu-screen-recorder stores the portal token and restores it on every
later run (`-restore-portal-session yes`), so the picker does not come back.
To pick a different screen, delete
`~/.var/app/com.dec05eba.gpu_screen_recorder/config/gpu-screen-recorder/restore_token`.

## Settings

Environment variables, defaults in `lib/rawcast.clj`:

| variable | default | |
|---|---|---|
| `RAWCAST_MIC` | `alsa_input.usb-Shure_Inc_Shure_MV7-00.mono-fallback` | source name from `pactl list short sources` |
| `RAWCAST_CAMERA` | `/dev/video0` | |
| `RAWCAST_CAMERA_SIZE` | `1920x1080` | an MJPEG mode of the camera, see `ffmpeg -f v4l2 -list_formats all -i /dev/video0` |
| `RAWCAST_CAMERA_FPS` | `30` | |
| `RAWCAST_CAMERA_ENCODER` | `h264_nvenc` | or `libx264` |
| `RAWCAST_FFMPEG` | `/usr/bin/ffmpeg` | |
| `RAWCAST_SCREEN_FPS` | `60` | |
| `RAWCAST_RECORDINGS` | `~/Videos/Recordings` | |

Both scripts refuse to start when the microphone is not connected, rather
than recording with whatever input is the default.

Quality and codec of the screen are the `command` fn in `record-screen`
(`-k h264 -q very_high`, see `gpu-screen-recorder --help`), those of the
webcam the `encoder-args` map in `record-webcam`.

## How stopping works

Two traps shaped the scripts, both found by test recordings that came back
without a duration:

- A signal to `flatpak run` never reaches the program inside the sandbox; the
  recorder survives as an orphan. `record-screen` finds the
  `gpu-screen-recorder` process among the descendants of the wrapper and
  sends SIGINT to it directly.
- ffmpeg skips writing the mkv trailer when it receives a second signal
  ("Immediate exit requested"). A Ctrl-C in the terminal would be that second
  signal, so both recorders run under `setsid` in their own session, and the
  scripts send exactly one SIGINT, whether stopped by Enter, Ctrl-C or a
  SIGTERM from the parent.

## In the editor

Import both files, align by waveform (Kdenlive, DaVinci Resolve, Premiere all
have it), mute the webcam track. The screencast's track is the voice.

DaVinci Resolve does not import mkv; remux without re-encoding first:

```
ffmpeg -i webcam.mkv -c copy webcam.mp4
ffmpeg -i screen.mkv -c copy screen.mp4
```

## License

MIT, see LICENSE.
