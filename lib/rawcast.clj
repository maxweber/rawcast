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

(def camera
  (setting "RAWCAST_CAMERA" "/dev/video0"))

(def camera-size
  (setting "RAWCAST_CAMERA_SIZE" "1920x1080"))

(def camera-fps
  (setting "RAWCAST_CAMERA_FPS" "30"))

(def camera-encoder
  "h264_nvenc (NVIDIA) or libx264 (CPU)."
  (setting "RAWCAST_CAMERA_ENCODER" "h264_nvenc"))

(def ffmpeg
  "Needs the pulse input device and the camera encoder compiled in."
  (setting "RAWCAST_FFMPEG" "/usr/bin/ffmpeg"))

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
