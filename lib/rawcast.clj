(ns rawcast
  "Settings and helpers shared by the rawcast scripts. Every setting has a
  default for the machine rawcast was written on and an environment variable
  to override it."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

(defn setting [env-var default]
  (or (System/getenv env-var) default))

(def mic
  "PulseAudio/PipeWire source name of the microphone, see
  `pactl list short sources`."
  (setting "RAWCAST_MIC" "alsa_input.usb-Shure_Inc_Shure_MV7-00.mono-fallback"))

(def desktop-audio
  "Second audio track of the screen recording, a gpu-screen-recorder audio
  source: default_output (what the speakers play), app:chrome (one
  application), app-inverse:slack (everything but one application) or
  device:<name> from `gpu-screen-recorder --list-audio-devices`. Empty: no
  second track."
  (setting "RAWCAST_DESKTOP_AUDIO" "default_output"))

(def desktop-gain
  "Volume of the desktop audio in the composed video, as the ffmpeg volume
  filter takes it: 0.5, -6dB, 1 for unchanged."
  (setting "RAWCAST_DESKTOP_GAIN" "0.5"))

(def camera
  (setting "RAWCAST_CAMERA" "/dev/video0"))

(def camera-size
  (setting "RAWCAST_CAMERA_SIZE" "1920x1080"))

(def camera-fps
  (setting "RAWCAST_CAMERA_FPS" "30"))

(def camera-encoder
  "h264_nvenc (NVIDIA) or libx264 (CPU)."
  (setting "RAWCAST_CAMERA_ENCODER" "h264_nvenc"))

(def encoder-args
  "ffmpeg arguments per camera encoder, constant quality."
  {"h264_nvenc" ["-c:v" "h264_nvenc" "-preset" "p5" "-tune" "hq"
                 "-rc" "vbr" "-cq" "20" "-b:v" "0"]
   "libx264" ["-c:v" "libx264" "-preset" "veryfast" "-crf" "20"]})

(defn encoder
  "The ffmpeg arguments of RAWCAST_CAMERA_ENCODER."
  []
  (or (encoder-args camera-encoder)
      (throw (ex-info (str "unknown RAWCAST_CAMERA_ENCODER " camera-encoder)
                      {:known (keys encoder-args)}))))

(def ffmpeg
  "Needs the pulse input device and the camera encoder compiled in."
  (setting "RAWCAST_FFMPEG" "/usr/bin/ffmpeg"))

(def ffprobe
  "Used by compose to count the audio tracks of the screen recording."
  (setting "RAWCAST_FFPROBE" "/usr/bin/ffprobe"))

(def python
  "A python3 with numpy, used by compose to measure the lag between the two
  recordings."
  (setting "RAWCAST_PYTHON" "/usr/bin/python3"))

(def pip-width
  "Width of the webcam picture in the composed video, in pixels."
  (setting "RAWCAST_PIP_WIDTH" "640"))

(def pip-margin
  "Distance of the webcam picture from the edges, in pixels."
  (setting "RAWCAST_PIP_MARGIN" "32"))

(def pip-corner
  "bottom-right, bottom-left, top-right or top-left."
  (setting "RAWCAST_PIP_CORNER" "bottom-right"))

(def screen-fps
  (setting "RAWCAST_SCREEN_FPS" "60"))

(def recordings-dir
  (setting "RAWCAST_RECORDINGS" (str (fs/home) "/Videos/Recordings")))

(defn mic-connected? []
  (str/includes? (:out (p/shell {:out :string} "pactl list short sources"))
                 mic))

(defn require-mic!
  "Refuses to record with whatever microphone happens to be the default."
  [script]
  (when-not (mic-connected?)
    (binding [*out* *err*]
      (println (str script ": microphone " mic " not found, refusing to record.")))
    (System/exit 1)))

(defn timestamp []
  (.format (java.time.LocalDateTime/now)
           (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm")))

(defn default-output [file-name]
  (str recordings-dir "/" (timestamp) "/" file-name))

(defn interrupt!
  "SIGINT, the Ctrl-C on which both ffmpeg and gpu-screen-recorder finalize
  their file. A plain process, p/shell would register a shutdown hook, which
  fails inside a shutdown hook."
  [pid]
  @(p/process ["kill" "-INT" (str pid)]))
