#!/usr/bin/env python3
"""Generates every built-in sound for the soundboard.

All audio is original: synthesized effects (numpy) mixed with text-to-speech
lines from espeak-ng. No movie or TV audio is used.

Needs: python3 + numpy, espeak-ng, ffmpeg.
Usage: python3 tools/make_sounds.py   (writes app/src/main/res/raw/*.ogg)
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
rng = np.random.default_rng(392)  # KFBR392


def t(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, attack=0.005, release=0.1):
    e = np.ones(n)
    a = max(1, int(SR * attack))
    r = max(1, int(SR * release))
    e[:a] = np.linspace(0, 1, a)
    e[-r:] *= np.linspace(1, 0, r)
    return e


def lowpass(x, alpha):
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += alpha * (v - acc)
        y[i] = acc
    return y


def silence(sec):
    return np.zeros(int(SR * sec))


# ---------- synthesized effects ----------

def explosion(sec=2.2, size=1.0):
    n = int(SR * sec)
    noise = rng.standard_normal(n)
    rumble = lowpass(noise, 0.02) * 6
    crack = lowpass(noise, 0.4)
    decay = np.exp(-np.linspace(0, 5 / size, n))
    crack_env = np.exp(-np.linspace(0, 25, n))
    boom = np.sin(2 * np.pi * np.cumsum(np.linspace(90, 30, n)) / SR) * np.exp(-np.linspace(0, 6, n))
    return (rumble * decay + crack * crack_env * 0.8 + boom * 0.9) * env(n, 0.002, 0.3)


def gunshot():
    n = int(SR * 0.45)
    noise = rng.standard_normal(n)
    return lowpass(noise, 0.5) * np.exp(-np.linspace(0, 18, n)) + explosion(0.45, 0.3) * 0.5


def beep(freq=1000, sec=0.12):
    x = t(sec)
    return np.sign(np.sin(2 * np.pi * freq * x)) * 0.35 * env(len(x), 0.002, 0.02)


def synth_sting():
    """Big 80s detuned-saw chord stab."""
    x = t(1.6)
    out = np.zeros(len(x))
    for f in (220.0, 277.18, 329.63, 440.0):
        for d in (-0.6, 0, 0.6):
            ph = (f + d) * x
            out += 2 * (ph - np.floor(ph + 0.5))
    out = lowpass(out / 12, 0.25)
    return out * np.exp(-np.linspace(0, 3, len(x))) * env(len(x), 0.005, 0.2)


def synth_arp():
    notes = [220, 277.18, 329.63, 440, 329.63, 277.18, 220, 164.81] * 2
    parts = []
    for f in notes:
        x = t(0.11)
        ph = f * x
        saw = 2 * (ph - np.floor(ph + 0.5))
        parts.append(lowpass(saw, 0.3) * 0.5 * env(len(x), 0.002, 0.03))
    return np.concatenate(parts)


def whoosh(sec=0.6, up=True):
    n = int(SR * sec)
    noise = rng.standard_normal(n)
    alphas = np.linspace(0.02, 0.6, n) if up else np.linspace(0.6, 0.02, n)
    y = np.empty(n)
    acc = 0.0
    for i in range(n):
        acc += alphas[i] * (noise[i] - acc)
        y[i] = acc
    return y * np.sin(np.linspace(0, np.pi, n)) * 1.2


def air_horn():
    x = t(1.3)
    out = np.zeros(len(x))
    for f in (466.16, 587.33, 698.46):
        ph = f * x
        out += np.sign(np.sin(2 * np.pi * ph)) * 0.25
    return lowpass(out, 0.35) * env(len(x), 0.01, 0.15)


def sad_trombone():
    parts = []
    for f, d in ((293.66, 0.45), (277.18, 0.45), (261.63, 0.45), (246.94, 1.3)):
        x = t(d)
        vib = 1 + (0.012 * np.sin(2 * np.pi * 6 * x) if d > 1 else 0)
        ph = np.cumsum(f * vib) / SR
        saw = 2 * (ph - np.floor(ph + 0.5))
        parts.append(lowpass(saw, 0.12) * env(len(x), 0.03, 0.08))
    return np.concatenate(parts) * 0.8


def helicopter(sec=2.5):
    x = t(sec)
    noise = lowpass(rng.standard_normal(len(x)), 0.08) * 3
    chop = (np.sin(2 * np.pi * 11 * x) > 0.3).astype(float)
    chop = lowpass(chop, 0.01)
    return noise * (0.2 + chop) * env(len(x), 0.3, 0.6)


def ticking(count=6):
    parts = []
    for _ in range(count):
        x = t(0.5)
        click = rng.standard_normal(len(x)) * np.exp(-np.linspace(0, 120, len(x)))
        parts.append(lowpass(click, 0.7) * 0.8)
    return np.concatenate(parts)


def rocket():
    n = int(SR * 1.2)
    hiss = lowpass(rng.standard_normal(n), 0.3) * np.linspace(0.3, 1.0, n)
    return np.concatenate([whoosh(0.35) * 0.6, hiss * env(n, 0.05, 0.1), explosion(2.0)])


def drum_hit():
    x = t(0.5)
    kick = np.sin(2 * np.pi * np.cumsum(np.linspace(150, 45, len(x))) / SR) * np.exp(-np.linspace(0, 8, len(x)))
    snare = lowpass(rng.standard_normal(len(x)), 0.5) * np.exp(-np.linspace(0, 14, len(x))) * 0.6
    return kick + snare


def guitar_riff():
    """Crunchy power-chord riff."""
    parts = []
    for root, d in ((82.41, 0.25), (82.41, 0.25), (98.0, 0.25), (110.0, 0.5), (82.41, 0.7)):
        x = t(d)
        sig = np.zeros(len(x))
        for mult in (1, 1.5, 2):
            ph = root * mult * x
            sig += 2 * (ph - np.floor(ph + 0.5))
        sig = np.tanh(sig * 3) * 0.5
        parts.append(lowpass(sig, 0.3) * env(len(x), 0.003, 0.05))
    return np.concatenate(parts)


def slow_mo(x):
    """Pitch down and stretch by resampling (slow-motion effect)."""
    idx = np.arange(0, len(x) - 1, 0.6)
    return np.interp(idx, np.arange(len(x)), x)


# ---------- speech ----------

def say(text, voice="en-us", pitch=25, speed=150, amp=180):
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        path = f.name
    subprocess.run(
        ["espeak-ng", "-v", voice, "-p", str(pitch), "-s", str(speed), "-a", str(amp), "-w", path, text],
        check=True,
    )
    with wave.open(path) as w:
        rate = w.getframerate()
        data = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768
    os.unlink(path)
    if rate != SR:
        data = np.interp(np.arange(0, len(data), rate / SR), np.arange(len(data)), data)
    return data


def mix(*tracks):
    """Each track is (start_seconds, samples, gain)."""
    end = max(int(s * SR) + len(x) for s, x, _ in tracks)
    out = np.zeros(end)
    for s, x, g in tracks:
        i = int(s * SR)
        out[i:i + len(x)] += x * g
    return out


def write(name, x):
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.9
    os.makedirs(OUT, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        tmp = f.name
    with wave.open(tmp, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())
    dst = os.path.join(OUT, name + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", "4", dst], check=True)
    os.unlink(tmp)
    print("wrote", dst)


def main():
    gruff = dict(pitch=20, speed=140)
    write("snd_macgruber", mix((0, say("Mac Gruber!", pitch=30, speed=130, amp=200), 1.0), (0.9, explosion(), 0.9)))
    write("snd_kfbr392", mix((0, say("K. F. B. R. 3 9 2.", **gruff), 1.0), (1.9, synth_sting(), 0.6)))
    write("snd_countdown", mix(
        (0, say("Three. Two. One.", speed=120), 1.0),
        (0.05, beep(), 1), (0.75, beep(), 1), (1.45, beep(), 1), (2.0, explosion(2.5, 1.3), 1.0)))
    write("snd_boom", explosion(2.8, 1.4))
    write("snd_paperclip", mix(
        (0, say("Give me a paperclip, a rubber band, and a stick of gum.", **gruff), 1.0), (3.4, explosion(), 0.8)))
    write("snd_celery", mix((0, say("Relax. I've got a stalk of celery.", **gruff), 1.0), (2.2, whoosh(0.4), 0.7)))
    write("snd_mullet", mix((0, whoosh(0.5), 0.8), (0.4, say("Mullet power!", pitch=35, speed=150, amp=200), 1.0)))
    write("snd_seal", mix(
        (0, synth_arp(), 0.35),
        (0.1, say("Navy Seal. Army Ranger. Green Beret. Basically everything.", **gruff), 1.0)))
    write("snd_synth", synth_sting())
    write("snd_synth_arp", synth_arp())
    write("snd_airhorn", air_horn())
    write("snd_sad", sad_trombone())
    write("snd_ticking", mix((0, ticking(6), 1.0), (3.0, explosion(), 1.0)))
    write("snd_nooo", slow_mo(mix((0, say("No!", pitch=40, speed=90, amp=200), 1.0), (0.05, explosion(1.5), 0.5))))
    write("snd_helicopter", helicopter())
    write("snd_lets_do_this", mix((0, drum_hit(), 0.9), (0.25, say("Let's do this!", pitch=35, speed=160, amp=200), 1.0)))
    write("snd_high_five", mix((0, say("Who wants a high five?", pitch=45, speed=150), 1.0), (1.6, gunshot(), 0.8)))
    write("snd_rocket", rocket())
    write("snd_gunshot", np.concatenate([gunshot(), silence(0.08), gunshot(), silence(0.08), gunshot()]))
    write("snd_guitar", guitar_riff())


if __name__ == "__main__":
    main()
