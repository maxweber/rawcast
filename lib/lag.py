"""Lag between two mono wav files via FFT cross-correlation.
Prints: lag_seconds (positive = event appears later in file B = B started earlier),
peak ratio (best peak / second best outside +-20 ms) as confidence."""
import sys, wave, numpy as np

def load(path):
    with wave.open(path) as w:
        assert w.getnchannels() == 1 and w.getsampwidth() == 2
        x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float64)
        return x / (np.abs(x).max() or 1), w.getframerate()

a, sr = load(sys.argv[1]); b, sr2 = load(sys.argv[2]); assert sr == sr2
max_lag = int(float(sys.argv[3]) * sr) if len(sys.argv) > 3 else int(3 * sr)
n = len(a) + len(b) - 1
fa, fb = np.fft.rfft(a, n), np.fft.rfft(b, n)
corr = np.fft.irfft(fb * np.conj(fa), n)          # corr[k] = sum_t b[t+k]*a[t]
lags = np.concatenate([np.arange(0, n - len(a) + 1), np.arange(-(len(a) - 1), 0)])
corr = np.concatenate([corr[:n - len(a) + 1], corr[n - len(a) + 1:]])
order = np.argsort(lags); lags, corr = lags[order], corr[order]
window = (lags >= -max_lag) & (lags <= max_lag)
lags, corr = lags[window], corr[window]
best = int(np.argmax(corr)); lag = lags[best]
exclude = np.abs(lags - lag) <= int(0.02 * sr)
second = corr[~exclude].max() if (~exclude).any() else 0.0
print(f"{lag / sr:.4f} {corr[best] / max(second, 1e-9):.2f}")
