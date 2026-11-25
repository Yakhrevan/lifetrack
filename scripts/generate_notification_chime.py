"""Generate an original, soft two-note LifeTrack notification sound (PCM WAV)."""

import math
import struct
import wave
from pathlib import Path


out = Path(__file__).resolve().parents[1] / "app/src/main/res/raw/lifetrack_chime.wav"
out.parent.mkdir(parents=True, exist_ok=True)
rate = 22050
samples = []
for sample_index in range(int(rate * 0.82)):
    t = sample_index / rate
    # Two gentle bell tones, with short attack and exponential decay.
    value = 0.0
    for onset, freq, strength in [(0.0, 659.25, 0.34), (0.23, 987.77, 0.26)]:
        elapsed = t - onset
        if elapsed >= 0:
            fade = min(1.0, elapsed / 0.018) * math.exp(-7.0 * elapsed)
            value += strength * fade * (
                math.sin(2 * math.pi * freq * elapsed)
                + 0.16 * math.sin(2 * math.pi * 2 * freq * elapsed)
            )
    samples.append(int(max(-1.0, min(1.0, value)) * 32767))

with wave.open(str(out), "wb") as wav:
    wav.setnchannels(1)
    wav.setsampwidth(2)
    wav.setframerate(rate)
    wav.writeframes(struct.pack("<" + "h" * len(samples), *samples))
