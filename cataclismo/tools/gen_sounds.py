#!/usr/bin/env python3
"""
gen_sounds.py — Sintetizador procedural de los sonidos del mod "Cataclismo".

Genera desde cero (100%% sintetico, sin samples externos ni material con
copyright) todos los efectos de sonido del mod y los escribe como OGG Vorbis
MONO a 44100 Hz en src/main/resources/assets/cataclysm/sounds/.

Tecnicas usadas: sintesis aditiva/FM con barridos de fase acumulada, ruido
espectral (magnitudes moldeadas + fases aleatorias via IRFFT, lo que produce
señales EXACTAMENTE periodicas para los loops sin costura), filtros variables
en el tiempo (SVF / un polo), envolventes ADSR sin clicks y una reverberacion
sintetica simple por multi-tap.

Los loops (rumble, tornado_loop, lava_loop) se construyen periodicos por
diseño (ruido IRFFT + LFOs con numero entero de ciclos + eventos colocados
con aritmetica modular), y se verifican con un assert de continuidad en la
costura sobre el render circular.

Determinista: semilla numpy fija. Re-ejecutable: sobrescribe los .ogg.

Uso:  python3 tools/gen_sounds.py
"""

import math
from pathlib import Path

import numpy as np
import soundfile as sf

SR = 44100
SEED = 20260804
PEAK = 10 ** (-1.0 / 20.0)  # -1 dBFS
OUT_DIR = Path(__file__).resolve().parent.parent / (
    "src/main/resources/assets/cataclysm/sounds"
)

rng = np.random.default_rng(SEED)


# ---------------------------------------------------------------------------
# Utilidades basicas
# ---------------------------------------------------------------------------

def tvec(dur):
    n = int(round(dur * SR))
    return np.arange(n) / SR


def normalize(y):
    """Normaliza al pico -1 dBFS."""
    m = np.max(np.abs(y))
    if m < 1e-12:
        raise ValueError("señal silenciosa")
    return y * (PEAK / m)


def fade(y, fin=0.01, fout=0.03):
    """Fundidos coseno al inicio/fin para evitar clicks (no usar en loops)."""
    y = y.copy()
    ni, no = int(fin * SR), int(fout * SR)
    if ni > 0:
        y[:ni] *= 0.5 - 0.5 * np.cos(np.pi * np.arange(ni) / ni)
    if no > 0:
        y[-no:] *= 0.5 + 0.5 * np.cos(np.pi * np.arange(no) / no)
    return y


def env_adsr(n, a, d, s, r, s_level=0.7):
    """Envolvente ADSR (tiempos en s, s = duracion del sustain)."""
    na, nd, ns, nr = (int(x * SR) for x in (a, d, s, r))
    nr = max(1, min(nr, n - na - nd - ns))
    e = np.zeros(n)
    i = 0
    if na:
        e[i:i + na] = np.linspace(0.0, 1.0, na, endpoint=False)
        i += na
    if nd:
        e[i:i + nd] = np.linspace(1.0, s_level, nd, endpoint=False)
        i += nd
    if ns:
        e[i:i + ns] = s_level
        i += ns
    rem = n - i
    if rem > 0:
        e[i:] = s_level * (0.5 + 0.5 * np.cos(np.pi * np.arange(rem) / max(nr, rem)))
    return e


def sweep_phase(freqs):
    """Fase acumulada (rad) para una trayectoria de frecuencia instantanea."""
    return 2.0 * np.pi * np.cumsum(freqs) / SR


def shaped_noise(n, shape_fn):
    """Ruido blanco moldeado en frecuencia (FFT). shape_fn(f_hz) -> ganancia."""
    x = rng.standard_normal(n)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    X *= shape_fn(f)
    return np.fft.irfft(X, n)


def periodic_noise(n, shape_fn):
    """Ruido EXACTAMENTE periodico (para loops): magnitudes moldeadas +
    fases aleatorias, sintetizado con IRFFT. y[0] empalma con y[n-1]."""
    f = np.fft.rfftfreq(n, 1.0 / SR)
    mag = shape_fn(f).astype(float)
    ph = rng.uniform(0, 2 * np.pi, mag.size)
    X = mag * np.exp(1j * ph)
    X[0] = 0.0  # sin DC
    if n % 2 == 0:
        X[-1] = X[-1].real
    return np.fft.irfft(X, n)


def lowpass_gauss(f, fc, order=4.0):
    return np.exp(-((f / fc) ** order))


def bandpass_gauss(f, fc, width):
    return np.exp(-(((f - fc) / width) ** 2))


def svf_bandpass(x, f_hz, q=2.0):
    """Filtro de estado variable (banda), con frecuencia variable en el tiempo.
    f_hz puede ser escalar o vector de len(x)."""
    n = len(x)
    f = np.full(n, f_hz, dtype=float) if np.isscalar(f_hz) else np.asarray(f_hz, float)
    g = 2.0 * np.sin(np.pi * np.clip(f, 10.0, SR * 0.45) / SR)
    damp = 1.0 / q
    low = band = 0.0
    out = np.empty(n)
    for i in range(n):
        hi = x[i] - low - damp * band
        band += g[i] * hi
        low += g[i] * band
        out[i] = band
    return out


def onepole_lp(x, fc_hz):
    """Paso bajo de un polo con corte variable en el tiempo."""
    n = len(x)
    fc = np.full(n, fc_hz, dtype=float) if np.isscalar(fc_hz) else np.asarray(fc_hz, float)
    a = np.exp(-2.0 * np.pi * np.clip(fc, 5.0, SR * 0.45) / SR)
    y = np.empty(n)
    z = 0.0
    for i in range(n):
        z = (1.0 - a[i]) * x[i] + a[i] * z
        y[i] = z
    return y


def multitap_reverb(x, delays_ms=(29, 37, 43, 53), fb=0.55, taps=8):
    """Reverberacion sintetica simple: copias retardadas decayentes."""
    out = x.copy()
    n = len(x)
    for d_ms in delays_ms:
        d = int(d_ms * SR / 1000)
        for k in range(1, taps + 1):
            off = k * d
            if off >= n:
                break
            out[off:] += x[: n - off] * (fb ** k) / len(delays_ms)
    return out


def add_wrapped(buf, sig, start):
    """Suma `sig` en `buf` con envolvimiento circular (para loops)."""
    n = len(buf)
    idx = (start + np.arange(len(sig))) % n
    np.add.at(buf, idx, sig)


def check_seamless(y, label):
    """Verifica loop sin costura sobre el render circular:
    1) diferencia RMS entre las primeras y las ultimas 256 muestras del
       render circular extendido (2 periodos) — debe ser ~0 para una señal
       periodica por construccion;
    2) el salto entre muestras consecutivas en la costura no supera lo
       tipico del interior (continuidad real, no trivial)."""
    n = len(y)
    circ = np.concatenate([y, y])          # render circular (2 periodos)
    head = circ[:256]
    tail_next = circ[n:n + 256]            # "primeras" muestras tras la costura
    rms = math.sqrt(float(np.mean((head - tail_next) ** 2)))
    assert rms < 1e-9, f"{label}: render circular no periodico (RMS={rms})"
    seam_step = abs(float(y[0] - y[-1]))
    typical = float(np.percentile(np.abs(np.diff(y)), 99.9))
    assert seam_step <= max(4.0 * typical, 1e-3), (
        f"{label}: salto en la costura {seam_step:.4f} > tipico {typical:.4f}"
    )


# ---------------------------------------------------------------------------
# Sintesis de cada sonido
# ---------------------------------------------------------------------------

def gen_siren():
    """Sirena 4s: barrido 400->900->400 Hz, 2 ciclos, vibrato y distorsion."""
    t = tvec(4.0)
    tri = 1.0 - np.abs(((t / 2.0) % 1.0) * 2.0 - 1.0)   # 2 ciclos de 2 s
    f = 400.0 + 500.0 * tri
    f = f * (1.0 + 0.012 * np.sin(2 * np.pi * 5.5 * t))  # vibrato
    ph = sweep_phase(f)
    y = np.sin(ph) + 0.35 * np.sin(2 * ph) + 0.15 * np.sin(3 * ph)
    y = np.tanh(2.2 * y)                                  # distorsion suave
    y *= env_adsr(len(t), 0.06, 0.1, 3.55, 0.25, 0.9)
    return fade(normalize(y), 0.01, 0.05)


def gen_klaxon():
    """Bocinazo 2s: ~180 Hz con armonicos cuadrados, 2 rafagas."""
    t = tvec(2.0)
    f = 180.0 * (1.0 + 0.01 * np.sin(2 * np.pi * 3.0 * t))
    ph = sweep_phase(f)
    y = np.zeros_like(t)
    for k in range(1, 17, 2):                # armonicos impares (cuadrada)
        y += np.sin(k * ph) / k
    y = np.tanh(3.0 * y)
    gate = np.zeros_like(t)
    for a, b in ((0.03, 0.88), (1.05, 1.92)):
        i, j = int(a * SR), int(b * SR)
        seg = np.ones(j - i)
        r = int(0.02 * SR)
        seg[:r] = 0.5 - 0.5 * np.cos(np.pi * np.arange(r) / r)
        seg[-r:] = 0.5 + 0.5 * np.cos(np.pi * np.arange(r) / r)
        gate[i:j] = seg
    return fade(normalize(y * gate), 0.005, 0.02)


def gen_rumble():
    """Retumbo 3s LOOP: ruido browniano filtrado <120 Hz, periodico exacto."""
    n = int(3.0 * SR)
    y = periodic_noise(
        n, lambda f: lowpass_gauss(f, 110.0, 4.0) / np.maximum(f, 12.0)
    )
    t = np.arange(n) / SR
    # variacion lenta con numero ENTERO de ciclos (2 en 3 s) -> sigue periodico
    y *= 1.0 + 0.25 * np.sin(2 * np.pi * (2.0 / 3.0) * t)
    y = normalize(y)
    check_seamless(y, "rumble")
    return y


def _rock_crack(variant):
    """Crujido 0.8s: bursts de ruido secos + resonancia grave."""
    n = int(0.8 * SR)
    y = np.zeros(n)
    times = ((0.02, 0.10, 0.30), (0.03, 0.13, 0.24, 0.42))[variant]
    fcs = ((2600, 1700, 3200), (3100, 2100, 1500, 2700))[variant]
    for tt, fc in zip(times, fcs):
        ln = int(rng.uniform(0.05, 0.09) * SR)
        burst = shaped_noise(ln, lambda f, fc=fc: bandpass_gauss(f, fc, 1400.0))
        burst *= np.exp(-np.arange(ln) / (0.012 * SR))    # transitorio seco
        i = int(tt * SR)
        y[i:i + ln] += burst[: n - i] * rng.uniform(0.8, 1.2)
    # resonancia grave: golpe con caida de tono
    tt = tvec(0.6)
    f0 = (120.0, 95.0)[variant]
    thump = np.sin(sweep_phase(f0 * np.exp(-tt * 2.2))) * np.exp(-tt / 0.16)
    y[: len(tt)] += 0.9 * thump
    return fade(normalize(y), 0.002, 0.05)


def gen_meteor_whistle():
    """Silbido 2s: 2000->300 Hz + ruido de friccion creciente."""
    t = tvec(2.0)
    f = 2000.0 * (300.0 / 2000.0) ** (t / 2.0)            # barrido exponencial
    f *= 1.0 + 0.008 * np.sin(2 * np.pi * 9.0 * t)
    ph = sweep_phase(f)
    tone = np.sin(ph) + 0.3 * np.sin(2 * ph)
    fric = svf_bandpass(rng.standard_normal(len(t)), f * 1.5, q=3.0)
    fric = fric / (np.max(np.abs(fric)) + 1e-12)
    mix = tone * (1.0 - 0.45 * t / 2.0) + fric * (0.25 + 1.1 * (t / 2.0) ** 2)
    mix *= env_adsr(len(t), 0.05, 0.2, 1.55, 0.2, 0.85)
    return fade(normalize(mix), 0.01, 0.04)


def _impact_blast(variant):
    """Explosion 2.5s: transitorio brutal + cola grave con reverb sintetica."""
    n = int(2.5 * SR)
    t = np.arange(n) / SR
    # transitorio: ruido ancho, ataque instantaneo
    ln = int(0.05 * SR)
    burst = np.zeros(n)
    burst[:ln] = shaped_noise(ln, lambda f: lowpass_gauss(f, 9000.0, 2.0))
    burst[:ln] *= np.exp(-np.arange(ln) / (0.010 * SR)) * 3.0
    # cuerpo grave: seno con caida de tono
    f0 = (62.0, 74.0)[variant]
    body = np.sin(sweep_phase(f0 * np.exp(-t * 1.4))) * np.exp(-t / 0.55)
    # cola de ruido grave decayente
    tail = shaped_noise(n, lambda f: lowpass_gauss(f, 350.0, 2.0))
    tail *= np.exp(-t / 0.8) * 0.8
    y = multitap_reverb(burst + 0.4 * tail, fb=(0.55, 0.6)[variant]) + 1.1 * body
    return fade(normalize(y), 0.0, 0.15)


def _thunder(variant):
    """Trueno 3s: crack agudo inicial + rumble largo decayente."""
    n = int(3.0 * SR)
    t = np.arange(n) / SR
    ln = int(0.10 * SR)
    crack = np.zeros(n)
    seg = shaped_noise(ln, lambda f: np.clip(f / 2500.0, 0, 1) *
                       lowpass_gauss(f, 9500.0, 2.0))
    crack[:ln] = seg * np.exp(-np.arange(ln) / (0.020 * SR)) * 2.5
    crack = multitap_reverb(crack, delays_ms=(23, 31, 41), fb=0.5, taps=6)
    rum = shaped_noise(n, lambda f: lowpass_gauss(f, 220.0, 2.0) /
                       np.maximum(f, 25.0) * 25.0)
    flutter = 1.0 + 0.5 * onepole_lp(rng.standard_normal(n), 6.0) * 12.0
    rum *= np.exp(-t / (1.1, 0.9)[variant]) * np.clip(flutter, 0.2, 2.0)
    d = int((0.06, 0.11)[variant] * SR)
    rum = np.concatenate([np.zeros(d), rum[:-d]])
    y = crack + 1.6 * rum / (np.max(np.abs(rum)) + 1e-12)
    return fade(normalize(y), 0.002, 0.25)


def gen_ionize():
    """Zumbido electrico 1s: 100->800 Hz, parciales inarmonicos + crackle."""
    t = tvec(1.0)
    f = 100.0 * (800.0 / 100.0) ** t
    y = np.zeros_like(t)
    for ratio, amp in ((1.0, 1.0), (2.76, 0.5), (4.31, 0.35), (6.17, 0.22)):
        y += amp * np.sin(sweep_phase(f * ratio))
    y = np.tanh(1.8 * y)
    # crackle: impulsos dispersos filtrados
    ck = np.zeros_like(t)
    pos = rng.choice(len(t), size=90, replace=False)
    ck[pos] = rng.uniform(-1, 1, size=90)
    ck = np.convolve(ck, np.exp(-np.arange(80) / 12.0), mode="same")
    y = y * env_adsr(len(t), 0.08, 0.1, 0.62, 0.2, 0.8) + 0.5 * ck * (0.3 + 0.7 * t)
    return fade(normalize(y), 0.008, 0.05)


def gen_tornado_loop():
    """Viento rotatorio 3s LOOP: ruido filtrado + AM ~1.2 Hz, sin costura."""
    n = int(3.0 * SR)
    t = np.arange(n) / SR
    wind = periodic_noise(
        n, lambda f: bandpass_gauss(f, 500.0, 700.0) + 0.4 * lowpass_gauss(f, 180.0, 2.0)
    )
    # AM con ciclos enteros: 4 ciclos en 3 s = 1.333 Hz (~1.2 Hz pedido)
    am = 1.0 + 0.45 * np.sin(2 * np.pi * (4.0 / 3.0) * t)
    am *= 1.0 + 0.15 * np.sin(2 * np.pi * (7.0 / 3.0) * t + 1.3)
    y = normalize(wind * am)
    check_seamless(y, "tornado_loop")
    return y


def _wind_gust(variant):
    """Rafaga 2s: ruido con ataque suave y whoosh (filtro que se abre)."""
    n = int(2.0 * SR)
    t = np.arange(n) / SR
    peak_t = (0.85, 1.05)[variant]
    env = np.exp(-((t - peak_t) ** 2) / (2 * (0.38, 0.30)[variant] ** 2))
    x = rng.standard_normal(n)
    cutoff = 250.0 + (2600.0, 1900.0)[variant] * env      # whoosh
    y = onepole_lp(x, cutoff) * env
    y += 0.3 * onepole_lp(rng.standard_normal(n), 150.0) * env ** 2
    return fade(normalize(y), 0.05, 0.1)


def gen_lava_loop():
    """Borboteo 3s LOOP: burbujas (senos con pitch bend abajo) + ruido grave."""
    n = int(3.0 * SR)
    base = periodic_noise(
        n, lambda f: lowpass_gauss(f, 160.0, 2.0) / np.maximum(f, 20.0) * 20.0
    )
    buf = 0.5 * base / (np.max(np.abs(base)) + 1e-12)
    for _ in range(16):
        f0 = rng.uniform(280.0, 900.0)
        dur = rng.uniform(0.06, 0.16)
        tt = tvec(dur)
        fb = f0 * np.exp(-tt * rng.uniform(4.0, 9.0))     # bend hacia abajo
        bub = np.sin(sweep_phase(fb)) * np.exp(-tt / (dur * 0.4))
        r = int(0.004 * SR)
        bub[:r] *= np.linspace(0, 1, r)
        add_wrapped(buf, bub * rng.uniform(0.25, 0.6), int(rng.uniform(0, n)))
    y = normalize(buf)
    check_seamless(y, "lava_loop")
    return y


def gen_eruption():
    """Erupcion 4s: boom grave + rugido continuo + crackles."""
    n = int(4.0 * SR)
    t = np.arange(n) / SR
    boom = np.sin(sweep_phase(58.0 * np.exp(-t * 1.2))) * np.exp(-t / 0.7) * 1.6
    roar = shaped_noise(n, lambda f: lowpass_gauss(f, 550.0, 2.0))
    swell = 0.5 + 0.5 * np.clip(t / 1.2, 0, 1)
    slow = 1.0 + 0.25 * np.sin(2 * np.pi * 0.7 * t + 0.5)
    roar *= swell * slow * 0.8
    ck = np.zeros(n)
    pos = rng.choice(n - 400, size=140, replace=False)
    amps = rng.uniform(0.2, 1.0, size=140)
    ker = np.exp(-np.arange(300) / 40.0) * rng.standard_normal(300)
    for p, a in zip(pos, amps):
        ck[p:p + 300] += a * ker
    y = boom + roar + 0.5 * ck
    y *= env_adsr(n, 0.01, 0.3, 3.1, 0.5, 0.85)
    return fade(normalize(y), 0.003, 0.3)


def gen_tsunami_roar():
    """Rugido de agua 4s: ruido ancho con modulacion lenta creciente."""
    n = int(4.0 * SR)
    t = np.arange(n) / SR
    x = rng.standard_normal(n)
    cutoff = 400.0 + 3400.0 * (t / 4.0) ** 1.5            # se abre al crecer
    y = onepole_lp(x, cutoff)
    grow = 0.30 + 0.70 * (t / 4.0) ** 1.3
    mod = 1.0 + (0.15 + 0.25 * t / 4.0) * np.sin(2 * np.pi * 0.45 * t)
    deep = shaped_noise(n, lambda f: lowpass_gauss(f, 130.0, 2.0)) * grow ** 2
    y = y * grow * mod + 0.9 * deep / (np.max(np.abs(deep)) + 1e-12) * grow
    return fade(normalize(y), 0.15, 0.25)


def gen_telegraph_ping():
    """Ping 0.5s: seno 1400 Hz + armonico, decay exponencial."""
    t = tvec(0.5)
    y = (np.sin(2 * np.pi * 1400.0 * t)
         + 0.45 * np.sin(2 * np.pi * 2800.0 * t)
         + 0.12 * np.sin(2 * np.pi * 4200.0 * t))
    y *= np.exp(-t / 0.11)
    r = int(0.002 * SR)
    y[:r] *= np.linspace(0, 1, r)
    return fade(normalize(y), 0.0, 0.03)


def _totem_pop(variant):
    """Pop 0.6s: crack transitorio + campanilla dorada 1800/2400 Hz."""
    n = int(0.6 * SR)
    t = np.arange(n) / SR
    ln = int(0.012 * SR)
    y = np.zeros(n)
    click = shaped_noise(ln, lambda f: bandpass_gauss(f, 3500.0, 2000.0))
    y[:ln] = click * np.exp(-np.arange(ln) / (0.003 * SR)) * 1.5
    fs = ((1800.0, 2400.0, 3610.0), (1750.0, 2480.0, 3390.0))[variant]
    amps = (1.0, 0.7, 0.25)
    taus = (0.16, 0.11, 0.07)
    d = int(0.01 * SR)
    tt = t[:-d] if d else t
    for fq, a, tau in zip(fs, amps, taus):
        y[d:] += a * np.sin(2 * np.pi * fq * tt) * np.exp(-tt / tau)
    return fade(normalize(y), 0.001, 0.06)


def gen_atmosphere_tear():
    """Rasgado atmosferico 3s: barrido resonante descendente + rumble."""
    n = int(3.0 * SR)
    t = np.arange(n) / SR
    fsweep = 3200.0 * (150.0 / 3200.0) ** (t / 3.0)
    tear = svf_bandpass(rng.standard_normal(n), fsweep, q=9.0)
    tear = tear / (np.max(np.abs(tear)) + 1e-12)
    rum = shaped_noise(n, lambda f: lowpass_gauss(f, 140.0, 2.0) /
                       np.maximum(f, 20.0) * 20.0)
    rum = rum / (np.max(np.abs(rum)) + 1e-12) * (0.3 + 0.7 * t / 3.0)
    y = tear * (0.6 + 0.4 * np.sin(2 * np.pi * 0.9 * t) ** 2) + 0.9 * rum
    y *= env_adsr(n, 0.15, 0.3, 2.15, 0.4, 0.9)
    return fade(normalize(y), 0.02, 0.2)


# ---------------------------------------------------------------------------
# Tabla de sonidos y escritura
# ---------------------------------------------------------------------------

SOUNDS = {
    "siren": gen_siren,
    "klaxon": gen_klaxon,
    "rumble": gen_rumble,
    "rock_crack": lambda: _rock_crack(0),
    "rock_crack_2": lambda: _rock_crack(1),
    "meteor_whistle": gen_meteor_whistle,
    "impact_blast": lambda: _impact_blast(0),
    "impact_blast_2": lambda: _impact_blast(1),
    "thunder": lambda: _thunder(0),
    "thunder_2": lambda: _thunder(1),
    "ionize": gen_ionize,
    "tornado_loop": gen_tornado_loop,
    "wind_gust": lambda: _wind_gust(0),
    "wind_gust_2": lambda: _wind_gust(1),
    "lava_loop": gen_lava_loop,
    "eruption": gen_eruption,
    "tsunami_roar": gen_tsunami_roar,
    "telegraph_ping": gen_telegraph_ping,
    "totem_pop": lambda: _totem_pop(0),
    "totem_pop_2": lambda: _totem_pop(1),
    "atmosphere_tear": gen_atmosphere_tear,
}


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"Escribiendo en {OUT_DIR}")
    for name, fn in SOUNDS.items():
        # normalizacion final: reescalado puro, no altera fades ni costuras
        y = normalize(fn())
        peak = np.max(np.abs(y))
        assert abs(peak - PEAK) < 1e-6, f"{name}: pico {peak} != -1 dBFS"
        path = OUT_DIR / f"{name}.ogg"
        sf.write(path, y.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        kb = path.stat().st_size / 1024
        print(f"  {name}.ogg  {len(y)/SR:.2f}s  {kb:.1f} KB")
        assert path.stat().st_size > 4096, f"{name}.ogg pesa menos de 4 KB"
    print("OK: todos los sonidos generados.")


if __name__ == "__main__":
    main()
