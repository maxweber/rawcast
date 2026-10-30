# rawcast

Record a screencast as two raw files: the screen with your microphone and
the desktop audio as two separate tracks, and the webcam with the same
microphone. No OBS, no scenes, no overlays. The picture-in-picture, the audio
mix, cuts and captions happen later in the editor, where the two clips
auto-align by waveform.

```
record sample
Recording into /home/max/Videos/Recordings/2026-10-21T11:56-sample
Press Enter to stop both recordings.
```

```
~/Videos/Recordings/2026-10-21T11:56-sample/
├── screen.mkv   full resolution, 60 fps, H.264, two opus tracks: microphone, desktop audio
└── webcam.mkv   1920x1080, 30 fps, H.264, microphone as FLAC track
```

Four babashka scripts:

| script | does |
|---|---|
| `record [--mute] [name]` | records both, into one dated folder; `--mute` silences the speakers meanwhile |
| `record-screen [out.mkv]` | records the screen |
| `record-webcam [out.mkv]` | records the webcam |
| `compose <folder> [out.mkv]` | screen with the webcam in a corner, in sync, as one mkv |

Enter or Ctrl-C stops a recording. The files are finalized before the script
exits.

## Why

On KDE Plasma (Wayland) nothing simple records the screen: `ffmpeg -f x11grab`
sees only XWayland windows and `kmsgrab` needs root. The screen therefore goes
through the xdg-desktop-portal via [gpu-screen-recorder](https://git.dec05eba.com/gpu-screen-recorder/about/),
the webcam through ffmpeg. Two files instead of one composite keep every pixel
of both sources for the edit.

The desktop audio (what the speakers play: a video in the browser, a
notification) is a second track of `screen.mkv`, not mixed into the
microphone. A mix cannot be undone; separate tracks can be balanced, ducked or
muted in the edit, a video that was too loud or a notification that went off
is one fader away, and `compose` keeps aligning the webcam by the clean
microphone track. A second track in the same file shares the clock with the
first; a separate file would need its own alignment.

With speakers the microphone hears the desktop audio a few milliseconds late,
which sounds hollow on top of the clean track. Wear headphones, or record with
`record --mute`: the speakers are muted while recording and come back when it
stops. PipeWire takes the monitor before the mute, so the desktop audio track
is recorded all the same; you just do not hear it.

## Requirements

- [babashka](https://babashka.org/)
- gpu-screen-recorder as Flatpak:
  `flatpak install flathub com.dec05eba.gpu_screen_recorder`
- an ffmpeg with the `pulse` input device and your camera encoder
  (`h264_nvenc` or `libx264`). The Ubuntu package `ffmpeg` has both; the
  Homebrew build has neither, hence the default `/usr/bin/ffmpeg`.
- `pactl` (pulseaudio-utils) and `setsid` (util-linux)
- for `compose`: a python3 with numpy (Ubuntu: `apt install python3-numpy`)

Written for a laptop with KDE Plasma 6.4 on Wayland, PipeWire, an Intel +
NVIDIA GPU, a 1080p MJPEG webcam and a Shure MV7 USB microphone. Other setups
need the settings below.

## Install

```
git clone https://github.com/maxweber/rawcast ~/bin/rawcast
echo 'export PATH="$HOME/bin/rawcast:$PATH"' >> ~/.bashrc
```

Open a new shell, then `record`, `record-screen` and `record-webcam` are
commands. Without the PATH line call them as `~/bin/rawcast/record`.

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
| `RAWCAST_DESKTOP_AUDIO` | `default_output` | second audio track of the screen: `default_output`, `app:chrome`, `app-inverse:slack` or `device:<name>` from `gpu-screen-recorder --list-audio-devices`; empty for no second track |
| `RAWCAST_DESKTOP_GAIN` | `0.5` | volume of the desktop audio in the composed video, as the ffmpeg `volume` filter takes it (`0.5`, `-6dB`, `1`) |
| `RAWCAST_CAMERA` | `/dev/video0` | |
| `RAWCAST_CAMERA_SIZE` | `1920x1080` | an MJPEG mode of the camera, see `ffmpeg -f v4l2 -list_formats all -i /dev/video0` |
| `RAWCAST_CAMERA_FPS` | `30` | |
| `RAWCAST_CAMERA_ENCODER` | `h264_nvenc` | or `libx264` |
| `RAWCAST_FFMPEG` | `/usr/bin/ffmpeg` | |
| `RAWCAST_FFPROBE` | `/usr/bin/ffprobe` | for `compose` |
| `RAWCAST_SCREEN_FPS` | `60` | |
| `RAWCAST_RECORDINGS` | `~/Videos/Recordings` | somewhere in your home: the Flatpak has its own `/tmp` |
| `RAWCAST_PYTHON` | `/usr/bin/python3` | needs numpy, for `compose` |
| `RAWCAST_PIP_WIDTH` | `640` | webcam width in the composed video |
| `RAWCAST_PIP_MARGIN` | `32` | distance from the edges |
| `RAWCAST_PIP_CORNER` | `bottom-right` | or `bottom-left`, `top-right`, `top-left` |

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

## Compose

```
compose ~/Videos/Recordings/2026-10-21T11:56-sample
Webcam started 0.121 s before the screen (confidence 2.8)
Saved /home/max/Videos/Recordings/2026-10-21T11:56-sample/composite.mkv
```

The two recordings start a few hundred milliseconds apart and the offset
differs per run. Both files carry the microphone, so `compose` measures it:
the two audio tracks are cross-correlated (`lib/lag.py`, FFT at 8 kHz) and
the webcam is shifted by the lag, which puts lips and voice in sync to the
audio sample. The webcam is scaled to `RAWCAST_PIP_WIDTH` and placed in
`RAWCAST_PIP_CORNER`. The confidence is the ratio of the correlation peak to
the runner-up; below 1.5 the two tracks had too little sound in common and the
sync deserves a look.

The audio of the result is one stereo track: the microphone plus the desktop
audio at `RAWCAST_DESKTOP_GAIN` (0.5, that is -6 dB), so a video played during
the recording is heard under the voice. A screencast without a desktop track
(`RAWCAST_DESKTOP_AUDIO=`) keeps its microphone track as it is.

## In the editor

For anything beyond the picture-in-picture, import both files, align by
waveform (Kdenlive, DaVinci Resolve, Premiere all have it), mute the webcam
track. The screencast's first audio track is the voice, its second the desktop
audio; all three editors can put each track of a clip on its own timeline
track, where the desktop audio gets its own level or a cut.

DaVinci Resolve does not import mkv; remux without re-encoding first:

```
ffmpeg -i webcam.mkv -c copy webcam.mp4
ffmpeg -i screen.mkv -c copy screen.mp4
```

## License

MIT, see LICENSE.
